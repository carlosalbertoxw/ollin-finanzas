package com.carlosalbertoxw.ollin.finanzas.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.ui.graphics.vector.ImageVector

/** Las cinco pestañas de abajo. Todo lo demas cuelga de ellas. */
enum class Destino(
    val ruta: String,
    val titulo: String,
    val icono: ImageVector
) {
    TABLERO("tablero", "Tablero", Icons.Filled.PieChart),
    MOVIMIENTOS("movimientos", "Movimientos", Icons.Filled.ReceiptLong),
    PRESUPUESTO("presupuesto", "Presupuesto", Icons.Filled.AccountBalanceWallet),
    ANALITICA("analitica", "Analitica", Icons.Filled.Insights),
    ARCHIVO("archivo", "Archivo", Icons.Filled.SwapVert)
}

object Rutas {
    const val CAPTURA = "captura"
    const val CAPTURA_CON_ID = "captura?id={id}&compromiso={compromiso}"
    const val TRANSFERENCIA = "transferencia"
    const val TRANSFERENCIA_CON_ID = "transferencia?id={id}"
    const val CUENTAS = "cuentas"
    const val CATEGORIAS = "categorias"
    const val COMPROMISOS = "compromisos"
    const val CALIDAD = "calidad"
    const val REVISION = "revision/{clave}"
    const val AJUSTES = "ajustes"
    const val TUTORIALES = "tutoriales"
    const val ACERCA_DE = "acerca"

    fun captura(id: Long? = null): String = if (id == null) "captura" else "captura?id=$id"

    /** Captura nueva con los datos de un compromiso ya puestos. */
    fun capturaDeCompromiso(compromisoId: Long): String = "captura?compromiso=$compromisoId"

    /** Sin id captura una transferencia nueva; con id abre la que ya existe. */
    fun transferencia(movimientoId: Long? = null): String =
        if (movimientoId == null) "transferencia" else "transferencia?id=$movimientoId"

    fun revision(clave: String): String = "revision/$clave"
}
