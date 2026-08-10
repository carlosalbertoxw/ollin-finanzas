package mx.ollin.finanzas.domain.usecase

import mx.ollin.finanzas.data.db.MovimientoDao
import mx.ollin.finanzas.data.db.OllinDatabase
import mx.ollin.finanzas.domain.model.Medio
import mx.ollin.finanzas.domain.model.TipoCuenta
import mx.ollin.finanzas.domain.model.TipoMovimiento
import mx.ollin.finanzas.domain.model.normalizaClave

/**
 * Arregla los hallazgos que tienen una correccion inequivoca.
 *
 * Regla de oro: nunca se toca el importe. El importe es lo que realmente
 * paso y de el dependen todos los saldos; lo que se corrige es la etiqueta
 * que lo describe mal.
 */
class ReparaDatos(private val db: OllinDatabase) {

    private val movimientoDao: MovimientoDao = db.movimientoDao()

    suspend fun repara(clave: String): Int = when (clave) {
        "tipo_vs_signo" -> alineaTipoConSigno()
        "medio_incoherente" -> alineaMedioConCuenta()
        "sin_categoria" -> asignaCategoriaAprendida()
        else -> 0
    }

    private suspend fun alineaTipoConSigno(): Int {
        var corregidos = 0
        movimientoDao.todos().forEach { m ->
            val esperado = m.tipo.signoEsperado
            if (esperado == 0 || m.importeCentavos == 0L) return@forEach
            val real = if (m.importeCentavos > 0) 1 else -1
            if (real == esperado) return@forEach

            val nuevo = when (m.tipo) {
                TipoMovimiento.TRANSFERENCIA_ENTRADA -> TipoMovimiento.TRANSFERENCIA_SALIDA
                TipoMovimiento.TRANSFERENCIA_SALIDA -> TipoMovimiento.TRANSFERENCIA_ENTRADA
                TipoMovimiento.ENTRADA -> TipoMovimiento.SALIDA
                TipoMovimiento.SALIDA -> TipoMovimiento.ENTRADA
                // Los internos admiten cualquier signo: no hay nada que alinear.
                TipoMovimiento.BALANCE_INICIAL, TipoMovimiento.AJUSTE_VALOR -> return@forEach
            }
            movimientoDao.actualiza(m.copy(tipo = nuevo, actualizadoEn = System.currentTimeMillis()))
            corregidos++
        }
        return corregidos
    }

    /**
     * Clasifica lo que se puede clasificar solo: los movimientos cuya misma
     * descripcion ya elegiste antes. Lo demas se queda como esta, porque
     * adivinar una categoria es peor que no tenerla; para esos esta la lista
     * de revision uno por uno.
     */
    private suspend fun asignaCategoriaAprendida(): Int {
        val categoriasValidas = db.categoriaDao().todas().map { it.id }.toSet()
        var corregidos = 0
        movimientoDao.todos().forEach { m ->
            if (m.categoriaId != null) return@forEach
            if (m.tipo.esTransferencia || m.tipo.esInterno) return@forEach
            val aprendida = db.mapeoDescripcionDao().categoriaDe(m.descripcion.normalizaClave())
                ?: return@forEach
            if (aprendida !in categoriasValidas) return@forEach
            movimientoDao.actualiza(
                m.copy(categoriaId = aprendida, actualizadoEn = System.currentTimeMillis())
            )
            corregidos++
        }
        return corregidos
    }

    private suspend fun alineaMedioConCuenta(): Int {
        val cuentas = db.cuentaDao().todas().associateBy { it.id }
        var corregidos = 0
        movimientoDao.todos().forEach { m ->
            val cuenta = cuentas[m.cuentaId] ?: return@forEach
            val correcto = when {
                cuenta.tipo == TipoCuenta.EFECTIVO -> Medio.EFECTIVO
                cuenta.soloElectronico -> Medio.ELECTRONICO
                // Sin regla declarada no se toca: lo que capturaste manda.
                else -> return@forEach
            }
            if (m.medio == correcto) return@forEach
            movimientoDao.actualiza(m.copy(medio = correcto, actualizadoEn = System.currentTimeMillis()))
            corregidos++
        }
        return corregidos
    }
}
