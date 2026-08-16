package com.carlosalbertoxw.ollin.finanzas.data.db

import androidx.room.Embedded
import com.carlosalbertoxw.ollin.finanzas.domain.model.TipoCuenta

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

/** Movimiento acompañado de los nombres que la lista necesita mostrar. */
data class MovimientoDetallado(
    @Embedded val movimiento: Movimiento,
    val nombreCuenta: String,
    val nombreCategoria: String?,
    val tipoCuenta: TipoCuenta
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
