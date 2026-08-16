package com.carlosalbertoxw.ollin.finanzas.data.excel

/**
 * Modo de columnas de la hoja Registros.
 *
 * COMPACTO emite solo las ocho columnas indispensables, para quien quiere la
 * hoja lo mas angosta posible. EXTENDIDO agrega categoria jerarquica, año
 * explicito, nota y vinculo al compromiso que origino el movimiento.
 */
enum class EsquemaExportacion(val etiqueta: String, val descripcion: String) {
    EXTENDIDO(
        "Extendido",
        "Agrega Categoria, Año, Nota y Compromiso. Recomendado para trabajar dentro de Ollin Finanzas."
    ),
    COMPACTO(
        "Compacto",
        "Solo las 8 columnas indispensables, sin categoria, nota ni compromiso."
    );

    val columnas: List<String>
        get() = when (this) {
            EXTENDIDO -> listOf(
                "Fecha", "Cantidad", "Cuenta", "Categoria", "Descripcion",
                "Medio", "Contraparte", "Tipo", "Mes", "Anio", "Nota", "Compromiso"
            )
            COMPACTO -> listOf(
                "Fecha", "Cantidad", "Cuenta", "Descripcion", "Medio", "Contraparte", "Tipo", "Mes"
            )
        }

    /** Indice 1-based de una columna dentro de Registros, o 0 si no existe en este modo. */
    fun indiceDe(nombre: String): Int = columnas.indexOfFirst {
        it.equals(nombre, ignoreCase = true)
    }.let { if (it < 0) 0 else it + 1 }

    val columnaFecha: Int get() = indiceDe("Fecha")
    val columnaCantidad: Int get() = indiceDe("Cantidad")
    val columnaCuenta: Int get() = indiceDe("Cuenta")
    val columnaTipo: Int get() = indiceDe("Tipo")

    /**
     * Columna sobre la que agrupan las hojas de analisis. En modo extendido es
     * Categoria; en compacto no hay categoria, asi que se cae a Descripcion.
     */
    val columnaAgrupacion: Int
        get() = if (this == EXTENDIDO) indiceDe("Categoria") else indiceDe("Descripcion")
}

/**
 * Pestañas que Ollin Finanzas sabe generar. El usuario elige cuales exportar; Registros
 * es obligatoria porque es la unica que contiene datos y de la que dependen las
 * formulas de todas las demas.
 */
enum class HojaExportable(
    val titulo: String,
    val descripcion: String,
    val obligatoria: Boolean = false
) {
    REGISTROS(
        "Registros",
        "Todos los movimientos, uno por renglon. Es la fuente de las demas hojas.",
        obligatoria = true
    ),
    BALANCE(
        "Balance",
        "Saldo vivo de cada cuenta, agrupado por naturaleza y con el patrimonio neto al final."
    ),
    INGRESOS_EGRESOS(
        "Ingresos - Egresos",
        "Categorias contra meses. Separa el consumo real de la compra de patrimonio."
    ),
    TRANSFERENCIAS(
        "Transferencias",
        "Entradas y salidas internas por cuenta, con el neto que debe cuadrar en cero."
    ),
    PRESUPUESTO(
        "Presupuesto",
        "Meta contra realidad por categoria, con la desviacion del mes."
    ),
    COMPROMISOS(
        "Compromisos",
        "Mensualidades MSI pendientes, suscripciones y gastos anuales por venir."
    ),
    DICCIONARIOS(
        "Diccionarios",
        "Catalogos de cuentas, categorias y tipos. Alimenta las listas desplegables de Registros."
    );

    companion object {
        /** Seleccion inicial: el libro completo. */
        val PREDETERMINADAS: Set<HojaExportable> = entries.toSet()

        /** Solo los datos, para quien unicamente quiere el respaldo. */
        val MINIMA: Set<HojaExportable> = setOf(REGISTROS, DICCIONARIOS)

        fun normaliza(seleccion: Set<HojaExportable>): Set<HojaExportable> =
            seleccion + entries.filter { it.obligatoria }
    }
}
