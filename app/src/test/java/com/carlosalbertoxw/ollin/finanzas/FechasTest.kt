package com.carlosalbertoxw.ollin.finanzas

import com.carlosalbertoxw.ollin.finanzas.domain.model.Fechas
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

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
}
