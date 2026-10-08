package com.carlosalbertoxw.ollin.finanzas.data.excel

import org.xml.sax.Attributes
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory

/** Una celda tal como venia en el archivo, sin interpretar todavia. */
data class CeldaLeida(
    val texto: String? = null,
    val numero: Double? = null
) {
    val estaVacia: Boolean get() = texto.isNullOrBlank() && numero == null

    /** Texto para comparar contra catalogos. Un numero entero sale sin ".0". */
    fun comoTexto(): String? = when {
        texto != null -> texto
        numero != null ->
            if (numero == numero.toLong().toDouble()) numero.toLong().toString()
            else numero.toString()
        else -> null
    }
}

data class HojaLeida(
    val nombre: String,
    val filas: List<List<CeldaLeida>>
)

data class LibroLeido(val hojas: List<HojaLeida>) {
    fun hoja(nombre: String): HojaLeida? =
        hojas.firstOrNull { it.nombre.equals(nombre, ignoreCase = true) }
}

/**
 * Lector de .xlsx basado en SAX del JDK, sin dependencias.
 *
 * El paquete se carga completo en memoria porque sharedStrings.xml puede venir
 * despues de las hojas dentro del zip y hace falta resolverlo antes. Para un
 * libro de finanzas personales (decenas de miles de filas como mucho) el costo
 * es irrelevante y evita necesitar acceso aleatorio al archivo.
 */
object XlsxLector {

    /** Lo que pueden sumar descomprimidas todas las partes XML del libro. */
    internal const val LIMITE_BYTES = 64L * 1024 * 1024

    /**
     * Partes XML que se aceptan. Un libro real trae una veintena; un zip con
     * cientos de miles de partes vacias no pesa nada y aun asi llena el mapa.
     */
    private const val LIMITE_PARTES = 2_000

    /** Los topes de una hoja de Excel: 1 048 576 filas por 16 384 columnas (XFD). */
    internal const val MAX_FILAS = 1_048_576
    internal const val MAX_COLUMNAS = 16_384

    /**
     * Celdas que se pueden materializar entre todas las hojas, contando las
     * vacias con que se rellenan los huecos.
     *
     * [LIMITE_BYTES] acota lo que se lee, no lo que sale de ahi: una sola
     * `<row r="2000000000">` o una celda en la columna XFD son unos pocos bytes
     * que obligan a rellenar millones de posiciones, y el `OutOfMemoryError`
     * cierra la app en vez de rechazar el archivo. Un libro de Ollin con
     * cincuenta mil movimientos ronda las 750 mil; esto es holgura de sobra.
     */
    internal const val LIMITE_CELDAS = 3_000_000L

    /** Una sola instancia para todos los huecos: rellenar no debe costar un objeto por celda. */
    private val VACIA = CeldaLeida()

    class ArchivoInvalido(mensaje: String, causa: Throwable? = null) : Exception(mensaje, causa)

    /** Lo que queda del [LIMITE_CELDAS] mientras se leen las hojas de un libro. */
    private class Presupuesto(private var restantes: Long) {
        fun gasta(celdas: Long) {
            restantes -= celdas
            if (restantes < 0) throw demasiadoGrande()
        }
    }

    fun lee(entrada: InputStream): LibroLeido {
        val partes = descomprime(entrada)
        val presupuesto = Presupuesto(LIMITE_CELDAS)

        if (!partes.containsKey("xl/workbook.xml")) {
            throw ArchivoInvalido("El archivo no parece un libro de Excel (.xlsx). Si es .xls antiguo, guardalo primero como .xlsx.")
        }

        val cadenas = partes["xl/sharedStrings.xml"]?.let(::leeSharedStrings) ?: emptyList()
        val relaciones = partes["xl/_rels/workbook.xml.rels"]?.let(::leeRelaciones) ?: emptyMap()
        val definiciones = leeDefinicionHojas(partes.getValue("xl/workbook.xml"))

        val hojas = definiciones.mapNotNull { (nombre, rid) ->
            val destino = relaciones[rid] ?: return@mapNotNull null
            val ruta = normalizaRuta(destino)
            val bytes = partes[ruta] ?: return@mapNotNull null
            HojaLeida(nombre, leeFilas(bytes, cadenas, presupuesto))
        }

        if (hojas.isEmpty()) throw ArchivoInvalido("El libro no tiene hojas legibles.")
        return LibroLeido(hojas)
    }

    // ------------------------------------------------------------------ zip

    private fun descomprime(entrada: InputStream): Map<String, ByteArray> {
        val partes = HashMap<String, ByteArray>()
        var total = 0L
        var leidas = 0
        try {
            ZipInputStream(entrada.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) { zip.closeEntry(); continue }
                    val nombre = entry.name.removePrefix("/")
                    // Solo interesan las partes XML del libro.
                    if (!nombre.endsWith(".xml") && !nombre.endsWith(".rels")) {
                        zip.closeEntry(); continue
                    }
                    if (++leidas > LIMITE_PARTES) throw demasiadoGrande()
                    val bytes = leeAcotado(zip, LIMITE_BYTES - total)
                    total += bytes.size
                    partes[nombre] = bytes
                    zip.closeEntry()
                }
            }
        } catch (e: ArchivoInvalido) {
            throw e
        } catch (e: Exception) {
            // La causa se conserva para el diagnostico, pero no viaja en el
            // texto: el mensaje crudo habla de rutas y clases internas.
            throw ArchivoInvalido(
                "No se pudo abrir el archivo. Puede estar danado o protegido con contrasena.",
                e
            )
        }
        return partes
    }

    /**
     * Lee la parte actual sin pasar de [presupuesto] bytes descomprimidos.
     *
     * No vale `readBytes()` y medir despues: una zip bomb cabe en unos KB y se
     * expande a gigas dentro de una sola parte, asi que el `OutOfMemoryError`
     * llega antes que la comprobacion. Y como es un `Error` y no una
     * `Exception`, nadie lo atrapa: la app se cierra en vez de rechazar el
     * archivo. Aqui se corta en cuanto se rebasa, sin haberlo cargado.
     */
    private fun leeAcotado(zip: ZipInputStream, presupuesto: Long): ByteArray {
        val salida = java.io.ByteArrayOutputStream()
        val bufer = ByteArray(64 * 1024)
        var acumulado = 0L
        while (true) {
            val n = zip.read(bufer)
            if (n < 0) break
            acumulado += n
            if (acumulado > presupuesto) throw demasiadoGrande()
            salida.write(bufer, 0, n)
        }
        return salida.toByteArray()
    }

    private fun demasiadoGrande() =
        ArchivoInvalido("El archivo es demasiado grande para procesarse en el telefono.")

    private fun fueraDeLaHoja() =
        ArchivoInvalido(
            "El archivo tiene celdas fuera de los limites de una hoja de calculo. " +
                "Vuelve a guardarlo como .xlsx desde tu hoja de calculo."
        )

    private fun normalizaRuta(destino: String): String {
        val limpio = destino.removePrefix("/")
        return if (limpio.startsWith("xl/")) limpio else "xl/$limpio"
    }

    // ------------------------------------------------------------- parseo

    /**
     * Banderas de endurecimiento, por si el parser las reconoce.
     *
     * En Android **ninguna** lo es: su SAXParserFactory esta hecha sobre Expat
     * y solo admite las dos de namespaces; cualquier otra lanza
     * SAXNotRecognizedException. Por eso se intentan una por una y sin ruido,
     * y por eso la defensa de verdad no puede vivir aqui.
     */
    private val BANDERAS_SEGURAS = listOf(
        "http://apache.org/xml/features/disallow-doctype-decl" to true,
        "http://xml.org/sax/features/external-general-entities" to false,
        "http://xml.org/sax/features/external-parameter-entities" to false
    )

    /**
     * El DOCTYPE es lo que abre la puerta a las entidades XML: sin el no hay
     * bomba que expandir. El limite de [LIMITE_BYTES] no la ataja, porque
     * cuenta lo que se lee del zip y no lo que el parser expande despues.
     *
     * Se rechaza leyendo el prologo a mano en vez de pedirselo al parser,
     * porque en Android el parser no sabe hacerlo. Una comprobacion propia
     * sobre los bytes funciona igual en todas partes.
     */
    private fun parsea(bytes: ByteArray, handler: DefaultHandler) {
        rechazaDoctype(bytes)
        val factory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = false
            BANDERAS_SEGURAS.forEach { (nombre, valor) ->
                runCatching { setFeature(nombre, valor) }
            }
        }
        try {
            factory.newSAXParser().parse(ByteArrayInputStream(bytes), handler)
        } catch (e: Exception) {
            // Un rechazo lanzado desde el handler puede llegar envuelto en una
            // SAXException, segun el parser. Se desenvuelve para que el mensaje
            // legible llegue a la persona y no el de la envoltura.
            throw rechazoDentroDe(e) ?: e
        }
    }

    private fun rechazoDentroDe(e: Throwable): ArchivoInvalido? =
        generateSequence<Throwable>(e) { (it as? SAXException)?.exception ?: it.cause }
            .take(8)
            .filterIsInstance<ArchivoInvalido>()
            .firstOrNull()

    /**
     * Recorre el prologo —lo que va antes del elemento raiz— y aborta si
     * encuentra un DOCTYPE. Solo mira ahi: mas adelante un `<` literal viaja
     * escapado como `&lt;`, asi que la secuencia no puede aparecer en el texto
     * de una celda y buscarla en todo el archivo daria falsos positivos.
     */
    private fun rechazaDoctype(bytes: ByteArray) {
        var i = 0
        while (i < bytes.size) {
            val b = bytes[i].toInt().toChar()
            if (b != '<') { i++; continue }
            when {
                // Declaracion XML o instruccion de proceso: <? ... ?>
                coincide(bytes, i, "<?") -> i = tras(bytes, i, "?>") ?: return
                // Comentario: <!-- ... -->
                coincide(bytes, i, "<!--") -> i = tras(bytes, i, "-->") ?: return
                coincide(bytes, i, "<!DOCTYPE") -> throw ArchivoInvalido(
                    "El archivo declara un DOCTYPE, que Ollin Finanzas no acepta. " +
                        "Vuelve a guardarlo como .xlsx desde tu hoja de calculo."
                )
                // Cualquier otra cosa ya es el elemento raiz: el prologo acabo.
                else -> return
            }
        }
    }

    /** Compara sin distinguir mayusculas, sobre ASCII, sin crear cadenas. */
    private fun coincide(bytes: ByteArray, desde: Int, texto: String): Boolean {
        if (desde + texto.length > bytes.size) return false
        return texto.indices.all { j ->
            bytes[desde + j].toInt().toChar().uppercaseChar() == texto[j].uppercaseChar()
        }
    }

    /** Indice justo despues de [cierre], o null si el archivo se acaba antes. */
    private fun tras(bytes: ByteArray, desde: Int, cierre: String): Int? {
        var i = desde
        while (i < bytes.size) {
            if (coincide(bytes, i, cierre)) return i + cierre.length
            i++
        }
        return null
    }

    private fun leeSharedStrings(bytes: ByteArray): List<String> {
        val resultado = mutableListOf<String>()
        val actual = StringBuilder()
        var dentroDeSi = false
        var capturando = false

        parsea(bytes, object : DefaultHandler() {
            override fun startElement(uri: String?, local: String?, qName: String, attrs: Attributes?) {
                when (qName) {
                    "si" -> { dentroDeSi = true; actual.setLength(0) }
                    "t" -> if (dentroDeSi) capturando = true
                    // <rPh> lleva la lectura fonetica japonesa; no es contenido.
                    "rPh" -> capturando = false
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (capturando) actual.appendRange(ch, start, start + length)
            }

            override fun endElement(uri: String?, local: String?, qName: String) {
                when (qName) {
                    "t" -> capturando = false
                    "si" -> { resultado += actual.toString(); dentroDeSi = false }
                }
            }
        })
        return resultado
    }

    private fun leeRelaciones(bytes: ByteArray): Map<String, String> {
        val mapa = HashMap<String, String>()
        parsea(bytes, object : DefaultHandler() {
            override fun startElement(uri: String?, local: String?, qName: String, attrs: Attributes?) {
                if (qName == "Relationship" && attrs != null) {
                    val id = attrs.getValue("Id") ?: return
                    val target = attrs.getValue("Target") ?: return
                    mapa[id] = target
                }
            }
        })
        return mapa
    }

    /** Devuelve pares (nombre de hoja, rId) en el orden del libro. */
    private fun leeDefinicionHojas(bytes: ByteArray): List<Pair<String, String>> {
        val lista = mutableListOf<Pair<String, String>>()
        parsea(bytes, object : DefaultHandler() {
            override fun startElement(uri: String?, local: String?, qName: String, attrs: Attributes?) {
                if (qName == "sheet" && attrs != null) {
                    val nombre = attrs.getValue("name") ?: return
                    val rid = attrs.getValue("r:id") ?: attrs.getValue("id") ?: return
                    lista += nombre to rid
                }
            }
        })
        return lista
    }

    private fun leeFilas(
        bytes: ByteArray,
        cadenas: List<String>,
        presupuesto: Presupuesto
    ): List<List<CeldaLeida>> {
        val filas = mutableListOf<List<CeldaLeida>>()
        var filaActual = HashMap<Int, CeldaLeida>()
        var numeroFilaActual = 0
        var maxColFila = 0

        var columnaCelda = 0
        var tipoCelda: String? = null
        val valor = StringBuilder()
        var capturandoValor = false
        var dentroDeFormula = false

        fun cierraFila() {
            if (numeroFilaActual <= 0) return
            // Rellena los huecos que el archivo omite y las filas salteadas. Se
            // cobra antes de rellenar: el presupuesto existe justo para que una
            // fila o una columna lejanas no se materialicen.
            val huecos = (numeroFilaActual - 1 - filas.size).coerceAtLeast(0)
            presupuesto.gasta(huecos.toLong() + maxColFila)
            repeat(huecos) { filas.add(emptyList()) }
            val fila = List(maxColFila) { filaActual[it + 1] ?: VACIA }
            if (filas.size == numeroFilaActual - 1) filas.add(fila) else filas[numeroFilaActual - 1] = fila
        }

        parsea(bytes, object : DefaultHandler() {
            override fun startElement(uri: String?, local: String?, qName: String, attrs: Attributes?) {
                when (qName) {
                    "row" -> {
                        filaActual = HashMap()
                        maxColFila = 0
                        numeroFilaActual = attrs?.getValue("r")?.toIntOrNull() ?: (filas.size + 1)
                        if (numeroFilaActual > MAX_FILAS) throw fueraDeLaHoja()
                    }
                    "c" -> {
                        val ref = attrs?.getValue("r")
                        columnaCelda = if (ref != null) Ooxml.indiceColumna(Ooxml.partesReferencia(ref).first)
                        else columnaCelda + 1
                        if (columnaCelda > MAX_COLUMNAS) throw fueraDeLaHoja()
                        if (columnaCelda > maxColFila) maxColFila = columnaCelda
                        tipoCelda = attrs?.getValue("t")
                        valor.setLength(0)
                    }
                    "f" -> dentroDeFormula = true
                    "v" -> if (!dentroDeFormula) { capturandoValor = true; valor.setLength(0) }
                    "t" -> if (!dentroDeFormula) { capturandoValor = true }
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (capturandoValor) valor.appendRange(ch, start, start + length)
            }

            override fun endElement(uri: String?, local: String?, qName: String) {
                when (qName) {
                    "f" -> dentroDeFormula = false
                    "v", "t" -> capturandoValor = false
                    "c" -> {
                        val crudo = valor.toString()
                        if (crudo.isNotEmpty()) {
                            val celda = when (tipoCelda) {
                                "s" -> CeldaLeida(texto = crudo.toIntOrNull()?.let { cadenas.getOrNull(it) })
                                "inlineStr", "str" -> CeldaLeida(texto = crudo)
                                "b" -> CeldaLeida(texto = if (crudo == "1") "VERDADERO" else "FALSO")
                                "e" -> CeldaLeida(texto = crudo) // #REF!, #VALUE!, etc.
                                else -> crudo.toDoubleOrNull()
                                    ?.let { CeldaLeida(numero = it) }
                                    ?: CeldaLeida(texto = crudo)
                            }
                            if (!celda.estaVacia) filaActual[columnaCelda] = celda
                        }
                        valor.setLength(0)
                        tipoCelda = null
                    }
                    "row" -> cierraFila()
                }
            }
        })

        return filas
    }
}
