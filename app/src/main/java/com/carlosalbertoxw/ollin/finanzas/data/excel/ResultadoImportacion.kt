package com.carlosalbertoxw.ollin.finanzas.data.excel

/*
 * Lo que entra a una importacion y lo que sale de ella. Viven aparte de
 * [ImportadorExcel] porque los leen tambien la pantalla de Archivo y las
 * pruebas, y el importador ya es largo por si solo.
 */

enum class Severidad { INFO, AVISO, ERROR }

data class Diagnostico(
    val severidad: Severidad,
    val mensaje: String,
    val fila: Int? = null
)

data class OpcionesImportacion(
    /** Alinea el Tipo con el signo del importe cuando se contradicen. */
    val corregirTipoSegunSigno: Boolean = true,
    /** Recalcula Contraparte en vez de confiar en lo que traiga esa columna. */
    val derivarContraparte: Boolean = true,
    /** Empareja las dos patas de cada transferencia. */
    val emparejarTransferencias: Boolean = true,
    val crearCuentasFaltantes: Boolean = true,
    val autoCategorizar: Boolean = true,
    /**
     * Vacia los movimientos antes de importar y, ya sin ellos, se lleva tambien
     * las cuentas y categorias que quedaron sin uso y que el libro no nombra.
     * Si es falso, agrega y no borra nada.
     */
    val reemplazarTodo: Boolean = true,
    /** Lee tambien Diccionarios, Presupuesto y Compromisos cuando el libro las trae. */
    val importarOtrasHojas: Boolean = true
)

data class ResultadoImportacion(
    val filasLeidas: Int = 0,
    val importados: Int = 0,
    val omitidos: Int = 0,
    val cuentasCreadas: List<String> = emptyList(),
    val categoriasCreadas: List<String> = emptyList(),
    val sinCategoria: Int = 0,
    val tiposCorregidos: Int = 0,
    val contrapartesRecalculadas: Int = 0,
    val transferenciasEmparejadas: Int = 0,
    val transferenciasHuerfanas: Int = 0,
    /** Metas leidas de la pestaña Presupuesto. */
    val presupuestosImportados: Int = 0,
    /** Compromisos leidos de la pestaña Compromisos. */
    val compromisosImportados: Int = 0,
    /** Cuentas que al reemplazar quedaron sin uso y sin respaldo en el libro. */
    val cuentasEliminadas: List<String> = emptyList(),
    val categoriasEliminadas: Int = 0,
    val diagnosticos: List<Diagnostico> = emptyList()
) {
    val huboProblemas: Boolean get() = diagnosticos.any { it.severidad != Severidad.INFO }

    /**
     * Los diagnosticos como se muestran: un mensaje que se repite renglon por
     * renglon se cuenta una sola vez, con la lista de renglones a los que le
     * paso. Sin esto, un libro con doscientas filas incompletas devuelve
     * doscientas lineas identicas y ninguna se lee.
     */
    fun diagnosticosAgrupados(): List<DiagnosticoAgrupado> = diagnosticos
        .groupBy { it.severidad to it.mensaje }
        .map { (clave, iguales) ->
            DiagnosticoAgrupado(
                severidad = clave.first,
                mensaje = clave.second,
                filas = iguales.mapNotNull { it.fila }
            )
        }
        .sortedWith(compareBy({ ordenSeveridad(it.severidad) }, { -it.veces }))

    private fun ordenSeveridad(severidad: Severidad): Int = when (severidad) {
        Severidad.ERROR -> 0
        Severidad.AVISO -> 1
        Severidad.INFO -> 2
    }
}

/** Un mensaje de diagnostico con los renglones que lo provocaron. */
data class DiagnosticoAgrupado(
    val severidad: Severidad,
    val mensaje: String,
    val filas: List<Int>
) {
    val veces: Int get() = maxOf(filas.size, 1)
}
