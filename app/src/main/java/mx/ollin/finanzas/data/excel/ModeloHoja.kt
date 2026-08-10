package mx.ollin.finanzas.data.excel

import java.time.LocalDate

/**
 * Indices de estilo tal como quedan escritos en cellXfs de styles.xml.
 * El orden aqui debe coincidir exactamente con [XlsxEscritor.estilosXml].
 */
object Estilo {
    const val NORMAL = 0
    const val FECHA = 1
    const val DINERO = 2
    const val PORCENTAJE = 3
    const val ENTERO = 4
    const val ENCABEZADO = 5
    const val NEGRITA = 6
    const val TITULO = 7
    const val DINERO_TOTAL = 8
    const val TENUE = 9
    const val PORCENTAJE_TOTAL = 10
    const val DINERO_NEGATIVO = 11
    const val SUBTITULO = 12
}

/** Una celda ya resuelta y lista para serializar. */
sealed interface Celda {
    val estilo: Int

    data object Vacia : Celda {
        override val estilo: Int get() = Estilo.NORMAL
    }

    data class Texto(val valor: String, override val estilo: Int = Estilo.NORMAL) : Celda

    data class Numero(val valor: Double, override val estilo: Int = Estilo.NORMAL) : Celda

    data class Fecha(val valor: LocalDate, override val estilo: Int = Estilo.FECHA) : Celda

    data class Booleano(val valor: Boolean, override val estilo: Int = Estilo.NORMAL) : Celda

    /**
     * Formula viva. [cache] es el resultado que Ollin ya conoce; se escribe como
     * valor almacenado para que la hoja se vea bien incluso en visores que no
     * recalculan, sin dejar de ser una formula real que se actualiza al editar.
     */
    data class Formula(
        val expresion: String,
        val cache: Double? = null,
        val cacheTexto: String? = null,
        override val estilo: Int = Estilo.NORMAL
    ) : Celda
}

/** Validacion de lista apuntando a un rango de otra hoja. */
data class ValidacionLista(
    val rangoDestino: String,   // "D2:D5000"
    val origenFormula: String   // "Diccionarios!$A$2:$A$40"
)

/** Ancho de columna en caracteres, como lo mide Excel. */
data class AnchoColumna(val columna: Int, val ancho: Double)

/** Definicion de un ListObject (tabla de Excel) sobre un rango. */
data class TablaExcel(
    val nombre: String,
    val rango: String,
    val encabezados: List<String>
)

/** Una hoja completa lista para escribirse. */
data class Hoja(
    val nombre: String,
    val filas: List<List<Celda>>,
    val anchos: List<AnchoColumna> = emptyList(),
    /** Fila donde congelar el panel (1 = congela el encabezado). 0 = sin congelar. */
    val congelarTrasFila: Int = 0,
    val autoFiltro: String? = null,
    val validaciones: List<ValidacionLista> = emptyList(),
    val tabla: TablaExcel? = null
) {
    val totalFilas: Int get() = filas.size
    val totalColumnas: Int get() = filas.maxOfOrNull { it.size } ?: 0
}
