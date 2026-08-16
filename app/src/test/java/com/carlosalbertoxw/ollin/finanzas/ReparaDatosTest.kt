package com.carlosalbertoxw.ollin.finanzas

import kotlinx.coroutines.test.runTest
import com.carlosalbertoxw.ollin.finanzas.domain.model.Medio
import com.carlosalbertoxw.ollin.finanzas.domain.model.TipoCuenta
import com.carlosalbertoxw.ollin.finanzas.domain.model.TipoMovimiento
import com.carlosalbertoxw.ollin.finanzas.domain.usecase.ReparaDatos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La regla de oro del modulo es que nunca se toca el importe: el importe es lo
 * que de verdad paso y de el cuelgan todos los saldos. Lo que se corrige es la
 * etiqueta que lo describe mal. Casi cada prueba de aqui la vigila.
 */
class ReparaDatosTest : BaseEnMemoria() {

    private fun repara() = ReparaDatos(db)

    private suspend fun importes(): List<Long> = movimientoDao.todos().map { it.importeCentavos }

    // ----------------------------------------------------- tipo contra signo

    @Test
    fun `voltea entrada y salida sin tocar el importe`() = runTest {
        val banorte = nuevaCuenta("Banorte")
        nuevoMovimiento(banorte, -50_000, TipoMovimiento.ENTRADA, "Reembolso")
        nuevoMovimiento(banorte, 30_000, TipoMovimiento.SALIDA, "Cobro")

        val antes = importes()
        assertEquals(2, repara().repara("tipo_vs_signo"))

        val despues = movimientoDao.todos()
        assertEquals(TipoMovimiento.SALIDA, despues.first { it.descripcion == "Reembolso" }.tipo)
        assertEquals(TipoMovimiento.ENTRADA, despues.first { it.descripcion == "Cobro" }.tipo)
        assertEquals("El importe no se toca nunca", antes, importes())
    }

    @Test
    fun `voltea tambien las dos patas de transferencia`() = runTest {
        val banorte = nuevaCuenta("Banorte")
        nuevoMovimiento(banorte, -50_000, TipoMovimiento.TRANSFERENCIA_ENTRADA, "Traspaso")

        assertEquals(1, repara().repara("tipo_vs_signo"))
        assertEquals(TipoMovimiento.TRANSFERENCIA_SALIDA, movimientoDao.todos().single().tipo)
    }

    /** Revaluar sube y depreciar baja: los internos admiten cualquier signo. */
    @Test
    fun `no toca el balance inicial ni el ajuste de valor`() = runTest {
        val banorte = nuevaCuenta("Banorte")
        nuevoMovimiento(banorte, -50_000, TipoMovimiento.BALANCE_INICIAL, "Balance Inicial")
        nuevoMovimiento(banorte, -30_000, TipoMovimiento.AJUSTE_VALOR, "Depreciacion")

        assertEquals(0, repara().repara("tipo_vs_signo"))
        val tipos = movimientoDao.todos().map { it.tipo }.toSet()
        assertEquals(
            setOf(TipoMovimiento.BALANCE_INICIAL, TipoMovimiento.AJUSTE_VALOR),
            tipos
        )
    }

    @Test
    fun `un importe en cero no se considera contradictorio`() = runTest {
        val banorte = nuevaCuenta("Banorte")
        nuevoMovimiento(banorte, 0, TipoMovimiento.SALIDA, "Cancelado")

        assertEquals(0, repara().repara("tipo_vs_signo"))
    }

    // ------------------------------------------------------ medio y cuenta

    @Test
    fun `alinea el medio con la cuenta en los dos sentidos`() = runTest {
        val cartera = nuevaCuenta("Cartera", TipoCuenta.EFECTIVO)
        val tarjeta = nuevaCuenta("Tarjeta Oro", TipoCuenta.CREDITO)
        nuevoMovimiento(
            cartera, -10_000, TipoMovimiento.SALIDA, "Tortilleria",
            medio = Medio.ELECTRONICO
        )
        nuevoMovimiento(
            tarjeta, -20_000, TipoMovimiento.SALIDA, "Restaurante",
            medio = Medio.EFECTIVO
        )

        val antes = importes()
        assertEquals(2, repara().repara("medio_incoherente"))

        val despues = movimientoDao.todos()
        assertEquals(Medio.EFECTIVO, despues.first { it.descripcion == "Tortilleria" }.medio)
        assertEquals(Medio.ELECTRONICO, despues.first { it.descripcion == "Restaurante" }.medio)
        assertEquals(antes, importes())
    }

    /** Sin regla declarada manda lo que capturaste; adivinar seria peor. */
    @Test
    fun `una cuenta sin regla de medio no se toca`() = runTest {
        val banorte = nuevaCuenta("Banorte", TipoCuenta.DEBITO)
        nuevoMovimiento(
            banorte, -10_000, TipoMovimiento.SALIDA, "Propina",
            medio = Medio.EFECTIVO
        )

        assertEquals(0, repara().repara("medio_incoherente"))
        assertEquals(Medio.EFECTIVO, movimientoDao.todos().single().medio)
    }

    // ------------------------------------------------- categoria aprendida

    @Test
    fun `asigna la categoria que ya elegiste para esa misma descripcion`() = runTest {
        val banorte = nuevaCuenta("Banorte")
        val gasolina = nuevaCategoria("Gasolina")
        nuevoMapeo("gasolina", gasolina)
        // La descripcion llega con mayusculas y acento: normalizaClave las iguala.
        nuevoMovimiento(banorte, -10_000, TipoMovimiento.SALIDA, "Gasolina")

        assertEquals(1, repara().repara("sin_categoria"))
        assertEquals(gasolina, movimientoDao.todos().single().categoriaId)
    }

    /**
     * El mapeo sobrevive a que borres la categoria. Si no se validara, la
     * reparacion escribiria un categoriaId muerto y la clave foranea reventaria.
     */
    @Test
    fun `no asigna una categoria aprendida que ya se borro`() = runTest {
        val banorte = nuevaCuenta("Banorte")
        nuevoMapeo("gasolina", 999)
        nuevoMovimiento(banorte, -10_000, TipoMovimiento.SALIDA, "Gasolina")

        assertEquals(0, repara().repara("sin_categoria"))
        assertNull(movimientoDao.todos().single().categoriaId)
    }

    @Test
    fun `no categoriza transferencias ni internos aunque haya mapeo`() = runTest {
        val banorte = nuevaCuenta("Banorte")
        val gasolina = nuevaCategoria("Gasolina")
        nuevoMapeo("traspaso", gasolina)
        nuevoMapeo("balance inicial", gasolina)
        nuevoMovimiento(banorte, -10_000, TipoMovimiento.TRANSFERENCIA_SALIDA, "Traspaso")
        nuevoMovimiento(banorte, 50_000, TipoMovimiento.BALANCE_INICIAL, "Balance Inicial")

        assertEquals(0, repara().repara("sin_categoria"))
        assertTrue(movimientoDao.todos().all { it.categoriaId == null })
    }

    @Test
    fun `no pisa la categoria de un movimiento que ya la tiene`() = runTest {
        val banorte = nuevaCuenta("Banorte")
        val gasolina = nuevaCategoria("Gasolina")
        val despensa = nuevaCategoria("Despensa")
        nuevoMapeo("gasolina", gasolina)
        nuevoMovimiento(banorte, -10_000, TipoMovimiento.SALIDA, "Gasolina", categoriaId = despensa)

        assertEquals(0, repara().repara("sin_categoria"))
        assertEquals(despensa, movimientoDao.todos().single().categoriaId)
    }

    // ------------------------------------------------------------- la clave

    @Test
    fun `una clave desconocida no repara nada`() = runTest {
        val banorte = nuevaCuenta("Banorte")
        nuevoMovimiento(banorte, -50_000, TipoMovimiento.ENTRADA, "Reembolso")

        assertEquals(0, repara().repara("clave_inventada"))
        assertEquals(TipoMovimiento.ENTRADA, movimientoDao.todos().single().tipo)
    }
}
