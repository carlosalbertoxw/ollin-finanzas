package com.carlosalbertoxw.ollin.finanzas.domain.usecase

import com.carlosalbertoxw.ollin.finanzas.data.db.Movimiento
import com.carlosalbertoxw.ollin.finanzas.data.repo.FinanzasRepositorio
import com.carlosalbertoxw.ollin.finanzas.domain.model.Medio
import com.carlosalbertoxw.ollin.finanzas.domain.model.TipoCuenta
import com.carlosalbertoxw.ollin.finanzas.domain.model.TipoMovimiento
import com.carlosalbertoxw.ollin.finanzas.domain.model.normalizaClave

/**
 * Arregla los hallazgos que tienen una correccion inequivoca.
 *
 * Regla de oro: nunca se toca el importe. El importe es lo que realmente
 * paso y de el dependen todos los saldos; lo que se corrige es la etiqueta
 * que lo describe mal.
 *
 * Escribe por [FinanzasRepositorio] y no por los DAOs. Antes iba directo a la
 * base, que era el unico punto de la app que se saltaba la puerta unica de
 * escritura; y ademas mandaba un UPDATE suelto por movimiento, asi que una
 * reparacion a medias dejaba el libro mitad corregido.
 */
class ReparaDatos(private val repo: FinanzasRepositorio) {

    suspend fun repara(clave: String): Int = when (clave) {
        ClaveHallazgo.TIPO_VS_SIGNO -> repo.actualizaMovimientos(alineaTipoConSigno())
        ClaveHallazgo.MEDIO_INCOHERENTE -> repo.actualizaMovimientos(alineaMedioConCuenta())
        ClaveHallazgo.SIN_CATEGORIA -> repo.actualizaMovimientos(asignaCategoriaAprendida())
        else -> 0
    }

    private suspend fun alineaTipoConSigno(): List<Movimiento> =
        repo.listaMovimientos().mapNotNull { m ->
            val esperado = m.tipo.signoEsperado
            if (esperado == 0 || m.importeCentavos == 0L) return@mapNotNull null
            val real = if (m.importeCentavos > 0) 1 else -1
            if (real == esperado) return@mapNotNull null

            val nuevo = when (m.tipo) {
                TipoMovimiento.TRANSFERENCIA_ENTRADA -> TipoMovimiento.TRANSFERENCIA_SALIDA
                TipoMovimiento.TRANSFERENCIA_SALIDA -> TipoMovimiento.TRANSFERENCIA_ENTRADA
                TipoMovimiento.ENTRADA -> TipoMovimiento.SALIDA
                TipoMovimiento.SALIDA -> TipoMovimiento.ENTRADA
                // Los internos admiten cualquier signo: no hay nada que alinear.
                TipoMovimiento.BALANCE_INICIAL, TipoMovimiento.AJUSTE_VALOR -> return@mapNotNull null
            }
            m.copy(tipo = nuevo)
        }

    /**
     * Clasifica lo que se puede clasificar solo: los movimientos cuya misma
     * descripcion ya elegiste antes. Lo demas se queda como esta, porque
     * adivinar una categoria es peor que no tenerla; para esos esta la lista
     * de revision uno por uno.
     *
     * El mapeo se carga entero antes del recorrido. Consultarlo por movimiento
     * era una consulta por renglon para leer una tabla que cabe de sobra en
     * memoria.
     */
    private suspend fun asignaCategoriaAprendida(): List<Movimiento> {
        val categoriasValidas = repo.listaCategorias().mapTo(HashSet()) { it.id }
        val aprendidas = repo.mapeoDeDescripciones()

        return repo.listaMovimientos().mapNotNull { m ->
            if (m.categoriaId != null) return@mapNotNull null
            if (m.tipo.esTransferencia || m.tipo.esInterno) return@mapNotNull null
            val aprendida = aprendidas[m.descripcion.normalizaClave()] ?: return@mapNotNull null
            if (aprendida !in categoriasValidas) return@mapNotNull null
            m.copy(categoriaId = aprendida)
        }
    }

    private suspend fun alineaMedioConCuenta(): List<Movimiento> {
        val cuentas = repo.listaCuentas().associateBy { it.id }

        return repo.listaMovimientos().mapNotNull { m ->
            val cuenta = cuentas[m.cuentaId] ?: return@mapNotNull null
            val correcto = when {
                cuenta.tipo == TipoCuenta.EFECTIVO -> Medio.EFECTIVO
                cuenta.soloElectronico -> Medio.ELECTRONICO
                // Sin regla declarada no se toca: lo que capturaste manda.
                else -> return@mapNotNull null
            }
            if (m.medio == correcto) return@mapNotNull null
            m.copy(medio = correcto)
        }
    }
}
