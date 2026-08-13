package mx.ollin.finanzas.data.excel

import mx.ollin.finanzas.domain.model.Dinero
import mx.ollin.finanzas.domain.model.Periodicidad
import mx.ollin.finanzas.domain.model.TipoCuenta
import mx.ollin.finanzas.domain.model.normalizaClave
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * Lee las pestañas que no son Registros: Diccionarios, Presupuesto y
 * Compromisos.
 *
 * Aqui solo se interpreta el papel; nada toca la base. Lo que sale son datos
 * sueltos —nombres, montos, fechas— que [ImportadorExcel] resuelve contra los
 * catalogos que ya existen en el telefono.
 *
 * Se aceptan tanto los libros que salieron de Ollin Finanzas, con sus titulos y
 * subtitulos antes del encabezado, como una tabla pelona hecha a mano: el
 * encabezado se busca por sinonimos en cualquier renglon, no en el primero.
 */
object LectorCatalogos {

    /** Cuenta declarada en Diccionarios. [tipo] es nulo si la naturaleza no vino o no se reconocio. */
    data class CuentaDeclarada(val nombre: String, val tipo: TipoCuenta?)

    /** Categoria declarada en Diccionarios. [grupo] es el nombre de su categoria padre. */
    data class CategoriaDeclarada(val nombre: String, val grupo: String?)

    data class Diccionarios(
        val cuentas: List<CuentaDeclarada> = emptyList(),
        val categorias: List<CategoriaDeclarada> = emptyList()
    ) {
        val vacio: Boolean get() = cuentas.isEmpty() && categorias.isEmpty()
    }

    /** Una meta de presupuesto tal como venia en la hoja. */
    data class MetaDeclarada(
        val categoria: String,
        val periodo: YearMonth?,
        val montoCentavos: Long,
        val fila: Int
    )

    /**
     * Un compromiso tal como venia en la hoja.
     *
     * La hoja que exporta la app muestra el **proximo** pago y los pagos que
     * faltan, no la fecha original ni los ya cubiertos: son los dos datos que
     * sirven para vigilar lo que viene. Al volver a entrar, el compromiso se
     * reconstruye desde ahi —arranca en el proximo pago con los pagos que
     * quedan— salvo que el libro traiga las columnas completas.
     */
    data class CompromisoDeclarado(
        val nombre: String,
        val cuenta: String?,
        val categoria: String?,
        val montoCentavos: Long,
        val periodicidad: Periodicidad,
        val fechaPrimerPago: LocalDate,
        val totalPagos: Int?,
        val pagosRealizados: Int,
        val notas: String?,
        val fila: Int
    )

    // ------------------------------------------------------------- lectura

    private val FORMATOS_FECHA = listOf(
        DateTimeFormatter.ISO_LOCAL_DATE,
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("d/M/yyyy"),
        DateTimeFormatter.ofPattern("MM/dd/yyyy"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy")
    )

    private val MESES = listOf(
        "enero", "febrero", "marzo", "abril", "mayo", "junio",
        "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"
    )

    fun texto(celda: CeldaLeida?): String? =
        celda?.comoTexto()?.trim()?.ifBlank { null }

    fun fecha(celda: CeldaLeida?): LocalDate? {
        if (celda == null) return null
        celda.numero?.let { return Ooxml.desdeSerial(it) }
        val valor = celda.texto?.trim().orEmpty()
        if (valor.isEmpty()) return null
        FORMATOS_FECHA.forEach { formato ->
            runCatching { return LocalDate.parse(valor, formato) }
        }
        return null
    }

    fun centavos(celda: CeldaLeida?): Long? {
        if (celda == null) return null
        celda.numero?.let { return Dinero.aCentavos(it) }
        return celda.texto?.let(Dinero::parsea)
    }

    fun entero(celda: CeldaLeida?): Int? {
        if (celda == null) return null
        celda.numero?.let { return it.toInt() }
        return celda.texto?.trim()?.toIntOrNull()
    }

    /** "2026-01", "01/2026", "enero 2026" o el numero de mes suelto con su año aparte. */
    fun periodo(celda: CeldaLeida?, anio: Int? = null): YearMonth? {
        val bruto = texto(celda) ?: return null
        runCatching { return YearMonth.parse(bruto) }
        val clave = bruto.normalizaClave()

        Regex("^(\\d{1,2})[/-](\\d{4})$").find(clave)?.groupValues?.let { g ->
            return yearMonthSeguro(g[2].toInt(), g[1].toInt())
        }
        Regex("^(\\d{4})[/-](\\d{1,2})$").find(clave)?.groupValues?.let { g ->
            return yearMonthSeguro(g[1].toInt(), g[2].toInt())
        }
        // Nombre de mes, con o sin año pegado: "enero", "enero 2026".
        val nombre = MESES.indexOfFirst { clave.startsWith(it) }
        if (nombre >= 0) {
            val delTexto = Regex("(\\d{4})").find(clave)?.value?.toInt()
            val año = delTexto ?: anio ?: return null
            return yearMonthSeguro(año, nombre + 1)
        }
        // Un mes suelto solo sirve si el año viene de otra columna.
        val soloMes = clave.toIntOrNull()
        if (soloMes != null && anio != null) return yearMonthSeguro(anio, soloMes)
        return null
    }

    private fun yearMonthSeguro(anio: Int, mes: Int): YearMonth? =
        if (mes in 1..12 && anio in 1900..2200) YearMonth.of(anio, mes) else null

    // -------------------------------------------------------- Diccionarios

    private val ALIAS_CUENTA = listOf("cuentas", "cuenta", "nombre de cuenta")
    private val ALIAS_NATURALEZA = listOf("naturaleza", "tipo de cuenta", "tipo cuenta")
    private val ALIAS_CATEGORIA = listOf("categorias", "categoria", "rubro")
    private val ALIAS_GRUPO = listOf("grupo", "padre", "categoria padre", "grupo de categoria")

    /**
     * Diccionarios trae dos catalogos independientes uno al lado del otro:
     * cuentas con su naturaleza en un par de columnas y categorias con su grupo
     * en otro. Cada uno se lee por su cuenta, asi que no importa que tengan
     * distinto largo ni en que columnas hayan quedado.
     */
    fun leeDiccionarios(libro: LibroLeido): Diccionarios {
        val hoja = libro.hoja("Diccionarios") ?: libro.hoja("Diccionario") ?: return Diccionarios()
        val encabezado = hoja.filas.firstOrNull() ?: return Diccionarios()

        val cuenta = columna(encabezado, ALIAS_CUENTA)
        val naturaleza = columna(encabezado, ALIAS_NATURALEZA)
        val categoria = columna(encabezado, ALIAS_CATEGORIA)
        val grupo = columna(encabezado, ALIAS_GRUPO)

        val cuentas = LinkedHashMap<String, CuentaDeclarada>()
        val categorias = LinkedHashMap<String, CategoriaDeclarada>()

        hoja.filas.drop(1).forEach { fila ->
            if (cuenta != null) {
                texto(fila.getOrNull(cuenta))?.let { nombre ->
                    cuentas.getOrPut(nombre.normalizaClave()) {
                        CuentaDeclarada(
                            nombre = nombre,
                            tipo = TipoCuenta.desdeEtiqueta(naturaleza?.let { texto(fila.getOrNull(it)) })
                        )
                    }
                }
            }
            if (categoria != null) {
                texto(fila.getOrNull(categoria))?.let { nombre ->
                    categorias.getOrPut(nombre.normalizaClave()) {
                        CategoriaDeclarada(
                            nombre = nombre,
                            grupo = grupo?.let { texto(fila.getOrNull(it)) }
                        )
                    }
                }
            }
        }

        return Diccionarios(cuentas.values.toList(), categorias.values.toList())
    }

    // --------------------------------------------------------- Presupuesto

    private val ALIAS_META = listOf("meta", "monto", "presupuesto", "importe", "cantidad", "planeado")
    private val ALIAS_ANIO = listOf("anio", "año", "year")
    private val ALIAS_MES = listOf("mes", "periodo", "month")

    /**
     * La hoja de la app apila un bloque por periodo: un subtitulo "2026-01",
     * su encabezado y sus renglones. Una tabla hecha a mano, en cambio, suele
     * llevar el periodo en columnas propias. Un solo recorrido cubre las dos:
     * se recuerda el ultimo subtitulo de periodo visto y las columnas mandan
     * sobre el cuando existen.
     */
    fun leePresupuesto(libro: LibroLeido): List<MetaDeclarada> {
        val hoja = libro.hoja("Presupuesto") ?: libro.hoja("Presupuestos") ?: return emptyList()
        val metas = mutableListOf<MetaDeclarada>()

        var colCategoria: Int? = null
        var colMeta: Int? = null
        var colAnio: Int? = null
        var colMes: Int? = null
        var periodoDelBloque: YearMonth? = null

        hoja.filas.forEachIndexed { indice, fila ->
            val numeroFila = indice + 1
            if (fila.all { it.estaVacia }) return@forEachIndexed

            // Subtitulo de bloque: una sola celda y es un periodo.
            val llenas = fila.filter { !it.estaVacia }
            if (llenas.size == 1) {
                periodo(llenas.first())?.let { periodoDelBloque = it }
                return@forEachIndexed
            }

            val categoria = columna(fila, ALIAS_CATEGORIA)
            val meta = columna(fila, ALIAS_META)
            if (categoria != null && meta != null) {
                colCategoria = categoria
                colMeta = meta
                colAnio = columna(fila, ALIAS_ANIO)
                colMes = columna(fila, ALIAS_MES)
                return@forEachIndexed
            }

            val cCategoria = colCategoria ?: return@forEachIndexed
            val cMeta = colMeta ?: return@forEachIndexed
            val nombre = texto(fila.getOrNull(cCategoria)) ?: return@forEachIndexed
            // Sin numero en la columna de la meta no hay meta: asi se caen solos
            // los renglones de total y cualquier nota suelta.
            val monto = fila.getOrNull(cMeta)?.takeIf { it.numero != null }?.let(::centavos)
                ?: return@forEachIndexed

            val anio = colAnio?.let { entero(fila.getOrNull(it)) }
            val delRenglon = colMes?.let { periodo(fila.getOrNull(it), anio) }

            metas += MetaDeclarada(
                categoria = nombre,
                periodo = delRenglon ?: periodoDelBloque,
                montoCentavos = monto,
                fila = numeroFila
            )
        }

        return metas
    }

    // --------------------------------------------------------- Compromisos

    private val ALIAS_COMPROMISO = listOf("compromiso", "compromisos", "nombre", "concepto", "descripcion")
    private val ALIAS_MONTO = listOf("monto", "importe", "cantidad", "pago", "mensualidad", "monto del pago")
    private val ALIAS_PERIODICIDAD = listOf("periodicidad", "frecuencia")
    private val ALIAS_PROXIMO = listOf("proximo pago", "proximo", "siguiente pago", "fecha")
    private val ALIAS_RESTANTES = listOf("pagos restantes", "restantes", "pagos por pagar", "faltan")
    private val ALIAS_PRIMER_PAGO = listOf("fecha primer pago", "primer pago", "inicio", "fecha de inicio")
    private val ALIAS_TOTAL_PAGOS = listOf("total pagos", "pagos totales", "plazos", "mensualidades", "numero de pagos")
    private val ALIAS_REALIZADOS = listOf("pagos realizados", "realizados", "pagados", "pagos hechos")
    private val ALIAS_NOTAS = listOf("notas", "nota", "comentario")

    fun leeCompromisos(libro: LibroLeido): List<CompromisoDeclarado> {
        val hoja = libro.hoja("Compromisos") ?: libro.hoja("Compromiso") ?: return emptyList()

        // El encabezado no esta en la primera fila: antes van titulo y nota.
        val filaEncabezado = hoja.filas.indexOfFirst {
            columna(it, ALIAS_COMPROMISO) != null && columna(it, ALIAS_MONTO) != null
        }
        if (filaEncabezado < 0) return emptyList()
        val encabezado = hoja.filas[filaEncabezado]

        val cNombre = columna(encabezado, ALIAS_COMPROMISO) ?: return emptyList()
        val cMonto = columna(encabezado, ALIAS_MONTO) ?: return emptyList()
        val cCuenta = columna(encabezado, ALIAS_CUENTA)
        val cCategoria = columna(encabezado, ALIAS_CATEGORIA)
        val cPeriodicidad = columna(encabezado, ALIAS_PERIODICIDAD)
        val cProximo = columna(encabezado, ALIAS_PROXIMO)
        val cRestantes = columna(encabezado, ALIAS_RESTANTES)
        val cPrimerPago = columna(encabezado, ALIAS_PRIMER_PAGO)
        val cTotalPagos = columna(encabezado, ALIAS_TOTAL_PAGOS)
        val cRealizados = columna(encabezado, ALIAS_REALIZADOS)
        val cNotas = columna(encabezado, ALIAS_NOTAS)

        val leidos = mutableListOf<CompromisoDeclarado>()

        hoja.filas.drop(filaEncabezado + 1).forEachIndexed { indice, fila ->
            val numeroFila = filaEncabezado + indice + 2
            if (fila.all { it.estaVacia }) return@forEachIndexed

            val nombre = texto(fila.getOrNull(cNombre)) ?: return@forEachIndexed
            if (nombre.normalizaClave().startsWith("total")) return@forEachIndexed
            // Sin numero propio no hay compromiso: asi se cae tambien cualquier
            // renglon de nota suelto entre los datos.
            val monto = fila.getOrNull(cMonto)?.takeIf { it.numero != null }?.let(::centavos)
                ?: return@forEachIndexed

            val periodicidad = Periodicidad.desdeEtiqueta(cPeriodicidad?.let { texto(fila.getOrNull(it)) })
                ?: Periodicidad.MENSUAL
            val proximo = cProximo?.let { fecha(fila.getOrNull(it)) }
            val primerPago = cPrimerPago?.let { fecha(fila.getOrNull(it)) }
                ?: proximo
                ?: return@forEachIndexed

            val realizados = cRealizados?.let { entero(fila.getOrNull(it)) } ?: 0
            val total = cTotalPagos?.let { entero(fila.getOrNull(it)) }
                // Sin columna de total, los que faltan son todos los que quedan:
                // el compromiso se reconstruye a partir del proximo pago.
                ?: cRestantes?.let { entero(fila.getOrNull(it)) }?.let { it + realizados }

            leidos += CompromisoDeclarado(
                nombre = nombre,
                cuenta = cCuenta?.let { texto(fila.getOrNull(it)) },
                categoria = cCategoria?.let { texto(fila.getOrNull(it)) },
                montoCentavos = kotlin.math.abs(monto),
                periodicidad = periodicidad,
                fechaPrimerPago = primerPago,
                totalPagos = total,
                pagosRealizados = realizados,
                notas = cNotas?.let { texto(fila.getOrNull(it)) },
                fila = numeroFila
            )
        }

        return leidos
    }

    // -------------------------------------------------------------- utiles

    /** Indice de la primera celda del renglon cuyo texto sea uno de los alias. */
    private fun columna(fila: List<CeldaLeida>, alias: List<String>): Int? =
        fila.indexOfFirst { celda ->
            celda.comoTexto()?.normalizaClave()?.let { it in alias } == true
        }.takeIf { it >= 0 }
}
