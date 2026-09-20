package com.carlosalbertoxw.ollin.finanzas.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Las cuatro pestañas de abajo. Todo lo demas cuelga de ellas.
 *
 * Archivo estuvo aqui y se fue a Ajustes: importar y exportar se hace de vez en
 * cuando --y el aviso de respaldo lleva ahi de un toque-- mientras que las
 * cuatro que quedan se miran a diario. Una barra reservada a lo cotidiano es
 * una barra que se lee de un vistazo.
 */
enum class Destino(
    val ruta: String,
    val titulo: String,
    val icono: ImageVector
) {
    TABLERO("tablero", "Tablero", Icons.Filled.PieChart),
    MOVIMIENTOS("movimientos", "Movimientos", Icons.Filled.ReceiptLong),
    PRESUPUESTO("presupuesto", "Presupuesto", Icons.Filled.AccountBalanceWallet),
    ANALITICA("analitica", "Analitica", Icons.Filled.Insights)
}

object Rutas {
    const val CAPTURA = "captura"
    const val CAPTURA_CON_ID = "captura?id={id}&compromiso={compromiso}"
    const val TRANSFERENCIA = "transferencia"
    const val TRANSFERENCIA_CON_ID = "transferencia?id={id}"
    const val ARCHIVO = "archivo"
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
