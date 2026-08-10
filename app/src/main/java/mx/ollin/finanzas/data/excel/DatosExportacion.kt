package mx.ollin.finanzas.data.excel

import mx.ollin.finanzas.data.db.Categoria
import mx.ollin.finanzas.data.db.Compromiso
import mx.ollin.finanzas.data.db.Cuenta
import mx.ollin.finanzas.data.db.Movimiento
import mx.ollin.finanzas.data.db.Presupuesto
import mx.ollin.finanzas.domain.model.TipoCategoria
import java.time.LocalDate
import java.time.YearMonth

/** Fotografia completa de los datos en el momento de exportar. */
data class DatosExportacion(
    val cuentas: List<Cuenta>,
    val categorias: List<Categoria>,
    val movimientos: List<Movimiento>,
    val presupuestos: List<Presupuesto>,
    val compromisos: List<Compromiso>
) {
    val cuentaPorId: Map<Long, Cuenta> = cuentas.associateBy { it.id }
    val categoriaPorId: Map<Long, Categoria> = categorias.associateBy { it.id }

    fun nombreCuenta(id: Long): String = cuentaPorId[id]?.nombre ?: "(cuenta eliminada)"

    fun nombreCategoria(id: Long?): String = id?.let { categoriaPorId[it]?.nombre } ?: ""

    /** Categorias hoja agrupadas bajo su padre, en el orden del catalogo. */
    fun agrupadasPorPadre(tipo: TipoCategoria): List<Pair<Categoria, List<Categoria>>> {
        val padres = categorias.filter { it.padreId == null && it.tipo == tipo }.sortedBy { it.orden }
        return padres.map { padre ->
            padre to categorias.filter { it.padreId == padre.id }.sortedBy { it.orden }
        }
    }

    /** Meses con actividad, en orden ascendente. Vacio si no hay movimientos. */
    val meses: List<YearMonth> by lazy {
        movimientos.map { YearMonth.from(it.fecha) }.distinct().sorted()
    }

    /** Numero de la ultima fila con datos en la hoja Registros (fila 1 = encabezado). */
    val ultimaFilaRegistros: Int get() = movimientos.size + 1

    val movimientosOrdenados: List<Movimiento> by lazy {
        movimientos.sortedWith(compareBy({ it.fecha }, { it.id }))
    }
}
