package mx.ollin.finanzas.data.db

import androidx.room.Embedded
import mx.ollin.finanzas.domain.model.TipoCuenta

/** Saldo vivo de una cuenta: la suma de todos sus movimientos. */
data class SaldoCuenta(
    val cuentaId: Long,
    val nombre: String,
    val tipo: TipoCuenta,
    val limiteCentavos: Long?,
    val incluirEnPatrimonio: Boolean,
    val saldoCentavos: Long,
    val movimientos: Int
)

/** Un mes de flujo, ya separando consumo real de compra de patrimonio. */
data class FlujoMes(
    val periodo: String,          // "2026-01"
    val ingresosCentavos: Long,
    val gastoConsumoCentavos: Long,
    val compraPatrimonioCentavos: Long
) {
    val gastoTotalCentavos: Long get() = gastoConsumoCentavos + compraPatrimonioCentavos
    val netoCentavos: Long get() = ingresosCentavos + gastoTotalCentavos

    /** Tasa de ahorro sobre consumo real, que es la que refleja tu tren de vida. */
    val tasaAhorro: Double
        get() = if (ingresosCentavos <= 0L) 0.0
        else (ingresosCentavos + gastoConsumoCentavos).toDouble() / ingresosCentavos
}

/** Cuantos movimientos cuelgan de cada categoria. Decide si se puede borrar o solo archivar. */
data class UsoCategoria(
    val categoriaId: Long,
    val movimientos: Int
)

data class TotalPorCategoria(
    val categoriaId: Long?,
    val nombre: String?,
    val totalCentavos: Long,
    val movimientos: Int
)

data class TotalPorDescripcion(
    val descripcion: String,
    val totalCentavos: Long,
    val movimientos: Int
)

data class TotalPorPeriodoCategoria(
    val periodo: String,
    val categoriaId: Long?,
    val totalCentavos: Long
)

/** Movimiento acompañado de los nombres que la lista necesita mostrar. */
data class MovimientoDetallado(
    @Embedded val movimiento: Movimiento,
    val nombreCuenta: String,
    val nombreCategoria: String?,
    val tipoCuenta: TipoCuenta
)

data class SaldoPorPeriodo(
    val periodo: String,
    val cuentaId: Long,
    val deltaCentavos: Long
)

/** Fila del comparativo presupuesto contra realidad. */
data class RenglonPresupuesto(
    val categoriaId: Long,
    val nombreCategoria: String,
    val metaCentavos: Long,
    val realCentavos: Long
) {
    val desviacionCentavos: Long get() = metaCentavos - kotlin.math.abs(realCentavos)
    val avance: Double
        get() = if (metaCentavos <= 0L) 0.0
        else kotlin.math.abs(realCentavos).toDouble() / metaCentavos
}
