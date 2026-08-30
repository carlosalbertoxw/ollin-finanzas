package com.carlosalbertoxw.ollin.finanzas

import com.carlosalbertoxw.ollin.finanzas.data.db.Compromiso
import com.carlosalbertoxw.ollin.finanzas.data.db.Movimiento
import com.carlosalbertoxw.ollin.finanzas.domain.model.Contraparte
import com.carlosalbertoxw.ollin.finanzas.domain.model.Medio
import com.carlosalbertoxw.ollin.finanzas.domain.model.Periodicidad
import com.carlosalbertoxw.ollin.finanzas.domain.model.TipoCuenta
import com.carlosalbertoxw.ollin.finanzas.domain.model.TipoMovimiento
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * El repositorio es la puerta unica de escritura, y por lo tanto el sitio donde
 * viven los invariantes que la interfaz no debe poder saltarse: una
 * transferencia nace y muere con sus dos patas, y la contraparte se deriva
 * siempre. Aqui se prueban uno por uno.
 */
class FinanzasRepositorioTest : BaseEnMemoria() {

    // ------------------------------------------------------- transferencias

    @Test
    fun `una transferencia nace con sus dos patas ligadas y de signo opuesto`() = runTest {
        val banorte = nuevaCuenta("Banorte")
        val cartera = nuevaCuenta("Cartera", TipoCuenta.EFECTIVO)

        repositorio.guardaTransferencia(
            fecha = LocalDate.of(2026, 3, 10),
            importeCentavos = 50_000,
            cuentaOrigenId = banorte,
            cuentaDestinoId = cartera,
            descripcion = "Retiro de cajero"
        )

        val patas = movimientoDao.todos()
        assertEquals(2, patas.size)
        assertEquals("Comparten un solo grupo", 1, patas.mapNotNull { it.grupoTransferencia }.distinct().size)
        assertEquals(0L, patas.sumOf { it.importeCentavos })

        val salida = patas.first { it.cuentaId == banorte }
        val entrada = patas.first { it.cuentaId == cartera }
        assertEquals(-50_000L, salida.importeCentavos)
        assertEquals(50_000L, entrada.importeCentavos)
        assertEquals(TipoMovimiento.TRANSFERENCIA_SALIDA, salida.tipo)
        assertEquals(TipoMovimiento.TRANSFERENCIA_ENTRADA, entrada.tipo)
        // Las dos son entre cuentas propias, nunca con un tercero.
        assertTrue(patas.all { it.contraparte == Contraparte.PROPIA })
        // Cada pata toma el medio de su cuenta: la cartera se mueve en mano.
        assertEquals(Medio.ELECTRONICO, salida.medio)
        assertEquals(Medio.EFECTIVO, entrada.medio)
    }

    @Test
    fun `el importe se captura en positivo y el origen no puede ser el destino`() = runTest {
        val banorte = nuevaCuenta("Banorte")
        val cartera = nuevaCuenta("Cartera")

        val negativo = runCatching {
            repositorio.guardaTransferencia(
                LocalDate.now(), -100, banorte, cartera, "Al reves"
            )
        }
        assertTrue(negativo.exceptionOrNull() is IllegalArgumentException)

        val misma = runCatching {
            repositorio.guardaTransferencia(
                LocalDate.now(), 100, banorte, banorte, "A si misma"
            )
        }
        assertTrue(misma.exceptionOrNull() is IllegalArgumentException)

        assertEquals("Ninguna de las dos escribio nada", 0, movimientoDao.todos().size)
    }

    /** Editar reescribe el par completo: nunca deja tres patas ni una suelta. */
    @Test
    fun `editar una transferencia deja exactamente dos patas`() = runTest {
        val banorte = nuevaCuenta("Banorte")
        val cartera = nuevaCuenta("Cartera")

        repositorio.guardaTransferencia(
            LocalDate.of(2026, 3, 10), 50_000, banorte, cartera, "Retiro"
        )
        val grupo = movimientoDao.todos().first().grupoTransferencia!!

        repositorio.guardaTransferencia(
            fecha = LocalDate.of(2026, 3, 11),
            importeCentavos = 75_000,
            cuentaOrigenId = cartera,
            cuentaDestinoId = banorte,
            descripcion = "Deposito",
            grupoExistente = grupo
        )

        val patas = movimientoDao.todos()
        assertEquals(2, patas.size)
        assertEquals(0L, patas.sumOf { it.importeCentavos })
        assertEquals(-75_000L, patas.first { it.cuentaId == cartera }.importeCentavos)
        assertTrue(patas.all { it.descripcion == "Deposito" })
    }

    /** Una pata huerfana se absorbe al guardar: es como se repara desde la UI. */
    @Test
    fun `una pata suelta se reemplaza por el par completo`() = runTest {
        val banorte = nuevaCuenta("Banorte")
        val cartera = nuevaCuenta("Cartera")
        val suelta = nuevoMovimiento(
            banorte, -50_000, TipoMovimiento.TRANSFERENCIA_SALIDA, "Sin pareja"
        )

        repositorio.guardaTransferencia(
            fecha = LocalDate.of(2026, 3, 10),
            importeCentavos = 50_000,
            cuentaOrigenId = banorte,
            cuentaDestinoId = cartera,
            descripcion = "Ya con pareja",
            idsAReemplazar = listOf(suelta)
        )

        val patas = movimientoDao.todos()
        assertEquals(2, patas.size)
        assertNull("La suelta ya no existe", patas.firstOrNull { it.id == suelta })
    }

    @Test
    fun `borrar una pata borra la otra`() = runTest {
        val banorte = nuevaCuenta("Banorte")
        val cartera = nuevaCuenta("Cartera")
        repositorio.guardaTransferencia(
            LocalDate.of(2026, 3, 10), 50_000, banorte, cartera, "Retiro"
        )

        repositorio.eliminaMovimiento(movimientoDao.todos().first())

        assertEquals("Media transferencia no es un estado valido", 0, movimientoDao.todos().size)
    }

    // ------------------------------------------------------ contraparte

    @Test
    fun `la contraparte se deriva del tipo y no se cree lo que le manden`() = runTest {
        val banorte = nuevaCuenta("Banorte")

        // Se guarda a proposito con la contraparte equivocada.
        repositorio.guardaMovimiento(
            Movimiento(
                fecha = LocalDate.of(2026, 1, 5),
                importeCentavos = -10_000,
                cuentaId = banorte,
                descripcion = "Tortilleria",
                medio = Medio.EFECTIVO,
                tipo = TipoMovimiento.SALIDA,
                contraparte = Contraparte.PROPIA
            )
        )
        repositorio.guardaMovimiento(
            Movimiento(
                fecha = LocalDate.of(2026, 1, 1),
                importeCentavos = 100_000,
                cuentaId = banorte,
                descripcion = "Balance Inicial",
                medio = Medio.ELECTRONICO,
                tipo = TipoMovimiento.BALANCE_INICIAL,
                contraparte = Contraparte.TERCERO
            )
        )

        val porTipo = movimientoDao.todos().associateBy { it.tipo }
        assertEquals(Contraparte.TERCERO, porTipo.getValue(TipoMovimiento.SALIDA).contraparte)
        assertEquals(Contraparte.PROPIA, porTipo.getValue(TipoMovimiento.BALANCE_INICIAL).contraparte)
    }

    // ------------------------------------------------------- compromisos

    private suspend fun nuevoCompromiso(
        primerPago: LocalDate,
        totalPagos: Int? = null
    ): Long = repositorio.guardaCompromiso(
        Compromiso(
            nombre = "Suscripcion",
            cuentaId = null,
            categoriaId = null,
            montoCentavos = 29_900,
            periodicidad = Periodicidad.MENSUAL,
            fechaPrimerPago = primerPago,
            totalPagos = totalPagos
        )
    )

    @Test
    fun `cumplir avanza el plan y lo apaga al llegar al ultimo pago`() = runTest {
        val id = nuevoCompromiso(LocalDate.of(2026, 1, 10), totalPagos = 2)

        repositorio.avanzaCompromiso(id)
        var c = repositorio.compromiso(id)!!
        assertEquals(1, c.pagosRealizados)
        assertEquals(LocalDate.of(2026, 2, 10), c.proximoPago)
        assertTrue(c.activo)

        repositorio.avanzaCompromiso(id)
        c = repositorio.compromiso(id)!!
        assertEquals(2, c.pagosRealizados)
        assertFalse("Con el ultimo pago el plan se apaga", c.activo)

        // Deshacer lo revive.
        repositorio.retrocedeCompromiso(id)
        c = repositorio.compromiso(id)!!
        assertEquals(1, c.pagosRealizados)
        assertTrue(c.activo)
    }

    @Test
    fun `no se puede retroceder un compromiso que no ha pagado nada`() = runTest {
        val id = nuevoCompromiso(LocalDate.of(2026, 1, 10))

        repositorio.retrocedeCompromiso(id)

        assertEquals(0, repositorio.compromiso(id)!!.pagosRealizados)
    }

    /**
     * Lo que protege el ancla inmovil. Si el plan avanzara moviendo
     * `fechaPrimerPago` con `plusMonths`/`minusMonths` —que recortan el dia en
     * los meses cortos y no lo recuerdan— un plan del 31 de enero se iria al 28
     * de febrero y al deshacerlo volveria al 28 de enero, perdiendo el 31 para
     * siempre.
     */
    @Test
    fun `descartar y restaurar devuelve el plan al mismo dia, aun en meses cortos`() = runTest {
        val id = nuevoCompromiso(LocalDate.of(2026, 1, 31))

        repositorio.descartaPagoCompromiso(id)
        assertEquals(LocalDate.of(2026, 2, 28), repositorio.compromiso(id)!!.proximoPago)

        repositorio.restauraPagoCompromiso(id)
        assertEquals(LocalDate.of(2026, 1, 31), repositorio.compromiso(id)!!.proximoPago)
    }

    /** Y el ancla recupera el 31 en cuanto vuelve a haber un mes que lo tiene. */
    @Test
    fun `descartar dos veces desde un 31 llega al 31 de marzo y no al 28`() = runTest {
        val id = nuevoCompromiso(LocalDate.of(2026, 1, 31))

        repositorio.descartaPagoCompromiso(id)
        repositorio.descartaPagoCompromiso(id)

        assertEquals(LocalDate.of(2026, 3, 31), repositorio.compromiso(id)!!.proximoPago)
    }

    @Test
    fun `descartar corre el plan pero no acorta un MSI`() = runTest {
        val id = nuevoCompromiso(LocalDate.of(2026, 1, 10), totalPagos = 12)

        repositorio.descartaPagoCompromiso(id)

        val c = repositorio.compromiso(id)!!
        assertEquals("El mes saltado no cuenta como pagado", 0, c.pagosRealizados)
        assertEquals("Siguen debiendose los doce", 12, c.pagosRestantes)
        assertEquals(LocalDate.of(2026, 2, 10), c.proximoPago)
    }

    @Test
    fun `restaurar sin nada descartado no mueve el plan`() = runTest {
        val id = nuevoCompromiso(LocalDate.of(2026, 1, 10))

        repositorio.restauraPagoCompromiso(id)

        val c = repositorio.compromiso(id)!!
        assertEquals(0, c.pagosDescartados)
        assertEquals(LocalDate.of(2026, 1, 10), c.proximoPago)
    }

    /** Cumplidos y descartados se suman para saber que toca. */
    @Test
    fun `cumplir y descartar corren el plan juntos`() = runTest {
        val id = nuevoCompromiso(LocalDate.of(2026, 1, 15), totalPagos = 6)

        repositorio.avanzaCompromiso(id)
        repositorio.descartaPagoCompromiso(id)

        val c = repositorio.compromiso(id)!!
        assertEquals(LocalDate.of(2026, 3, 15), c.proximoPago)
        assertEquals(1, c.pagosRealizados)
        assertEquals(5, c.pagosRestantes)
    }

    // ---------------------------------------------------- reparacion en lote

    @Test
    fun `actualizar en lote sella la fecha y devuelve cuantos cambio`() = runTest {
        val banorte = nuevaCuenta("Banorte")
        nuevoMovimiento(banorte, -10_000, TipoMovimiento.SALIDA, "Uno")
        nuevoMovimiento(banorte, -20_000, TipoMovimiento.SALIDA, "Dos")

        assertEquals("Una lista vacia no toca nada", 0, repositorio.actualizaMovimientos(emptyList()))

        val antes = movimientoDao.todos()
        val n = repositorio.actualizaMovimientos(antes.map { it.copy(nota = "revisado") })

        assertEquals(2, n)
        val despues = movimientoDao.todos()
        assertTrue(despues.all { it.nota == "revisado" })
        assertNotNull(despues.first().actualizadoEn)
        assertEquals("Los importes no se tocan", antes.map { it.importeCentavos }, despues.map { it.importeCentavos })
    }
}
