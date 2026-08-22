package com.carlosalbertoxw.ollin.finanzas

import com.carlosalbertoxw.ollin.finanzas.data.db.Compromiso
import com.carlosalbertoxw.ollin.finanzas.data.notify.Recordatorios
import com.carlosalbertoxw.ollin.finanzas.domain.model.Periodicidad
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Que avisa y que no. Es aritmetica de fechas pura, asi que no necesita base
 * ni Robolectric: solo hay que empujarle el "hoy".
 */
class RecordatoriosTest {

    private fun compromiso(
        nombre: String = "Suscripcion",
        primerPago: LocalDate,
        periodicidad: Periodicidad = Periodicidad.MENSUAL,
        totalPagos: Int? = null,
        pagosRealizados: Int = 0,
        pagosDescartados: Int = 0,
        activo: Boolean = true,
        avisarDiasAntes: Int = 3
    ) = Compromiso(
        nombre = nombre,
        cuentaId = null,
        categoriaId = null,
        montoCentavos = 29_900,
        periodicidad = periodicidad,
        fechaPrimerPago = primerPago,
        totalPagos = totalPagos,
        pagosRealizados = pagosRealizados,
        pagosDescartados = pagosDescartados,
        activo = activo,
        avisarDiasAntes = avisarDiasAntes
    )

    @Test
    fun `avisa dentro de la ventana y calla fuera de ella`() {
        val hoy = LocalDate.of(2026, 5, 10)
        val dentro = compromiso("Dentro", LocalDate.of(2026, 5, 13))   // en 3 dias
        val justoFuera = compromiso("Fuera", LocalDate.of(2026, 5, 14)) // en 4

        val avisados = Recordatorios.porVencer(listOf(dentro, justoFuera), hoy).map { it.first.nombre }

        assertEquals(listOf("Dentro"), avisados)
    }

    /** Lo atrasado sigue pendiente: el plan no avanza hasta que alguien decide. */
    @Test
    fun `lo vencido sigue avisando y sale primero`() {
        val hoy = LocalDate.of(2026, 5, 10)
        val atrasado = compromiso("Atrasado", LocalDate.of(2026, 4, 1))
        val proximo = compromiso("Proximo", LocalDate.of(2026, 5, 12))

        val avisados = Recordatorios.porVencer(listOf(proximo, atrasado), hoy)

        assertEquals(listOf("Atrasado", "Proximo"), avisados.map { it.first.nombre })
        assertEquals(LocalDate.of(2026, 4, 1), avisados.first().second)
    }

    @Test
    fun `un compromiso apagado no avisa`() {
        val hoy = LocalDate.of(2026, 5, 10)
        val apagado = compromiso(primerPago = LocalDate.of(2026, 5, 11), activo = false)

        assertTrue(Recordatorios.porVencer(listOf(apagado), hoy).isEmpty())
    }

    @Test
    fun `un plan ya terminado de pagar no avisa`() {
        val hoy = LocalDate.of(2026, 5, 10)
        val terminado = compromiso(
            primerPago = LocalDate.of(2026, 1, 11),
            totalPagos = 4,
            pagosRealizados = 4
        )

        assertTrue(Recordatorios.porVencer(listOf(terminado), hoy).isEmpty())
    }

    @Test
    fun `los pagos ya hechos corren la fecha que se anuncia`() {
        val hoy = LocalDate.of(2026, 5, 10)
        val c = compromiso(
            primerPago = LocalDate.of(2026, 1, 12),
            totalPagos = 12,
            pagosRealizados = 4
        )

        val avisado = Recordatorios.porVencer(listOf(c), hoy).single()

        assertEquals(LocalDate.of(2026, 5, 12), avisado.second)
    }

    /** Un descarte corre el plan igual que un pago, aunque no acorte el MSI. */
    @Test
    fun `los descartes tambien corren la fecha que se anuncia`() {
        val hoy = LocalDate.of(2026, 5, 10)
        val c = compromiso(
            primerPago = LocalDate.of(2026, 1, 12),
            totalPagos = 12,
            pagosRealizados = 2,
            pagosDescartados = 2
        )

        assertEquals(LocalDate.of(2026, 5, 12), Recordatorios.porVencer(listOf(c), hoy).single().second)
    }

    /** El ancla no se recorta: desde el 31 se vuelve al 31 en los meses que lo tienen. */
    @Test
    fun `un plan del 31 conserva el 31 al correrse`() {
        val hoy = LocalDate.of(2026, 3, 30)
        val c = compromiso(primerPago = LocalDate.of(2026, 1, 31), pagosRealizados = 2)

        assertEquals(LocalDate.of(2026, 3, 31), Recordatorios.porVencer(listOf(c), hoy).single().second)
    }

    @Test
    fun `una anual avisa con su propia ventana`() {
        val hoy = LocalDate.of(2026, 6, 1)
        val seguro = compromiso(
            nombre = "Seguro del carro",
            primerPago = LocalDate.of(2026, 6, 20),
            periodicidad = Periodicidad.ANUAL,
            avisarDiasAntes = 30
        )

        assertEquals(1, Recordatorios.porVencer(listOf(seguro), hoy).size)
    }

    @Test
    fun `la fecha se formatea para una persona y no en ISO`() {
        val texto = Recordatorios.formateaFecha(LocalDate.of(2026, 8, 16))

        assertTrue("No debe salir 2026-08-16, salio: $texto", texto.contains("16"))
        assertTrue("Debe nombrar el mes, salio: $texto", texto.contains("agosto"))
    }

    // ------------------------------------------------------ la hora del aviso

    /**
     * La regresion que dejo el aviso diario sin dispararse durante toda la vida
     * de la app: el disparo se calculaba como `now().plusDays(1)`, y como la
     * revision se reprograma en cada arranque, abrir la app antes de las nueve
     * corria la alarma al dia siguiente. Otra vez. Y otra.
     */
    @Test
    fun `antes de las nueve el aviso es hoy mismo`() {
        val aLasOcho = LocalDate.of(2026, 8, 21).atTime(8, 0)

        assertEquals(
            LocalDate.of(2026, 8, 21).atTime(9, 0),
            Recordatorios.proximoDisparo(aLasOcho)
        )
    }

    @Test
    fun `pasadas las nueve el aviso es mañana`() {
        val aLasDiez = LocalDate.of(2026, 8, 21).atTime(10, 0)

        assertEquals(
            LocalDate.of(2026, 8, 22).atTime(9, 0),
            Recordatorios.proximoDisparo(aLasDiez)
        )
    }

    /** A las nueve en punto el aviso del dia ya salio: el siguiente es mañana. */
    @Test
    fun `a las nueve en punto el aviso es mañana`() {
        val enPunto = LocalDate.of(2026, 8, 21).atTime(9, 0)

        assertEquals(
            LocalDate.of(2026, 8, 22).atTime(9, 0),
            Recordatorios.proximoDisparo(enPunto)
        )
    }

    /** Un minuto antes todavia alcanza: es el borde del lado util. */
    @Test
    fun `un minuto antes de las nueve el aviso sigue siendo hoy`() {
        val casi = LocalDate.of(2026, 8, 21).atTime(8, 59)

        assertEquals(
            LocalDate.of(2026, 8, 21).atTime(9, 0),
            Recordatorios.proximoDisparo(casi)
        )
    }

    /** Pasada la medianoche el aviso es de ese mismo dia, no del siguiente. */
    @Test
    fun `de madrugada el aviso es del dia que empieza`() {
        val madrugada = LocalDate.of(2026, 8, 21).atTime(0, 30)

        assertEquals(
            LocalDate.of(2026, 8, 21).atTime(9, 0),
            Recordatorios.proximoDisparo(madrugada)
        )
    }

    /** El ultimo dia del mes no se sale del calendario. */
    @Test
    fun `pasadas las nueve del ultimo dia del mes cae en el primero del siguiente`() {
        val fin = LocalDate.of(2026, 8, 31).atTime(21, 0)

        assertEquals(
            LocalDate.of(2026, 9, 1).atTime(9, 0),
            Recordatorios.proximoDisparo(fin)
        )
    }
}
