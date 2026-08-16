package com.carlosalbertoxw.ollin.finanzas.data.excel

import org.xml.sax.Attributes
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

    private const val LIMITE_BYTES = 64L * 1024 * 1024

    class ArchivoInvalido(mensaje: String, causa: Throwable? = null) : Exception(mensaje, causa)

    fun lee(entrada: InputStream): LibroLeido {
        val partes = descomprime(entrada)

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
            HojaLeida(nombre, leeFilas(bytes, cadenas))
        }

        if (hojas.isEmpty()) throw ArchivoInvalido("El libro no tiene hojas legibles.")
        return LibroLeido(hojas)
    }

    // ------------------------------------------------------------------ zip

    private fun descomprime(entrada: InputStream): Map<String, ByteArray> {
        val partes = HashMap<String, ByteArray>()
        var total = 0L
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
                    val bytes = zip.readBytes()
                    total += bytes.size
                    if (total > LIMITE_BYTES) {
                        throw ArchivoInvalido("El archivo es demasiado grande para procesarse en el telefono.")
                    }
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

    private fun normalizaRuta(destino: String): String {
        val limpio = destino.removePrefix("/")
        return if (limpio.startsWith("xl/")) limpio else "xl/$limpio"
    }

    // ------------------------------------------------------------- parseo

    private fun parsea(bytes: ByteArray, handler: DefaultHandler) {
        val factory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = false
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        }
        factory.newSAXParser().parse(ByteArrayInputStream(bytes), handler)
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

    private fun leeFilas(bytes: ByteArray, cadenas: List<String>): List<List<CeldaLeida>> {
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
            // Rellena los huecos que el archivo omite y las filas salteadas.
            while (filas.size < numeroFilaActual - 1) filas.add(emptyList())
            val fila = (1..maxColFila).map { filaActual[it] ?: CeldaLeida() }
            if (filas.size == numeroFilaActual - 1) filas.add(fila) else filas[numeroFilaActual - 1] = fila
        }

        parsea(bytes, object : DefaultHandler() {
            override fun startElement(uri: String?, local: String?, qName: String, attrs: Attributes?) {
                when (qName) {
                    "row" -> {
                        filaActual = HashMap()
                        maxColFila = 0
                        numeroFilaActual = attrs?.getValue("r")?.toIntOrNull() ?: (filas.size + 1)
                    }
                    "c" -> {
                        val ref = attrs?.getValue("r")
                        columnaCelda = if (ref != null) Ooxml.indiceColumna(Ooxml.partesReferencia(ref).first)
                        else columnaCelda + 1
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
