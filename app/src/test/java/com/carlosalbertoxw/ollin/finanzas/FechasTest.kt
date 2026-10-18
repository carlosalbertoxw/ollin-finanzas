package com.carlosalbertoxw.ollin.finanzas

import com.carlosalbertoxw.ollin.finanzas.domain.model.Fechas
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class FechasTest {

    private val hoy = LocalDate.of(2026, 10, 13)

    @Test
    fun `la fecha de este ano se lee sin el ano`() {
        assertEquals("Martes 13 de octubre", Fechas.larga(hoy, hoy))
    }

    @Test
    fun `la de otro ano lo lleva para no confundirse con la de hace dias`() {
        assertEquals("Martes 2 de diciembre de 2025", Fechas.larga(LocalDate.of(2025, 12, 2), hoy))
    }

    @Test
    fun `la corta va sin punto y con el ano solo si no es este`() {
        assertEquals("13 oct", Fechas.corta(hoy, hoy))
        assertEquals("2 dic 2025", Fechas.corta(LocalDate.of(2025, 12, 2), hoy))
    }

    @Test
    fun `hoy ayer y manana se dicen asi y lo demas con la fecha larga`() {
        assertEquals("Hoy", Fechas.relativa(hoy, hoy))
        assertEquals("Ayer", Fechas.relativa(hoy.minusDays(1), hoy))
        assertEquals("Mañana", Fechas.relativa(hoy.plusDays(1), hoy))
        assertEquals("Domingo 11 de octubre", Fechas.relativa(hoy.minusDays(2), hoy))
    }

    @Test
    fun `los meses se rotulan por su nombre y no como 2026-09`() {
        assertEquals("Sep", Fechas.mesCorto(YearMonth.of(2026, 9), hoy))
        assertEquals("Dic 2025", Fechas.mesCorto(YearMonth.of(2025, 12), hoy))
        assertEquals("agosto de 2026", Fechas.mesEnFrase(YearMonth.of(2026, 8)))
    }

    @Test
    fun `el rotulo de una barra nunca lleva ano y un periodo raro no truena`() {
        assertEquals("Dic", Fechas.rotuloDeMes("2025-12"))
        assertEquals("Oct", Fechas.rotuloDeMes("2026-10"))
        assertEquals("xx", Fechas.rotuloDeMes("raroxx"))
    }
}
