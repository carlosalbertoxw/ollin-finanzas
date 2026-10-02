package com.carlosalbertoxw.ollin.finanzas

import com.carlosalbertoxw.ollin.finanzas.data.db.SaldoCuenta
import com.carlosalbertoxw.ollin.finanzas.domain.model.TipoCuenta
import com.carlosalbertoxw.ollin.finanzas.ui.screens.EstadoTablero
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cuando sale la tarjeta de "Empieza por aqui".
 *
 * Hasta la 1.1.0 se colaba un instante al abrir la app: se decidia sobre el
 * estado inicial, que no trae cuentas porque la base aun no termino de abrir,
 * y desaparecia en cuanto llegaban los datos.
 */
class EstadoTableroTest {

    private fun cuenta(movimientos: Int) = SaldoCuenta(
        cuentaId = 1,
        nombre = "Nomina",
        tipo = TipoCuenta.entries.first(),
        limiteCentavos = null,
        incluirEnPatrimonio = true,
        saldoCentavos = 0,
        movimientos = movimientos
    )

    @Test
    fun `antes de leer la base no sale aunque todavia no haya cuentas`() {
        assertFalse(EstadoTablero().muestraBienvenida(muestraTutoriales = true))
    }

    @Test
    fun `antes de leer los ajustes no sale`() {
        assertFalse(EstadoTablero(cargado = true).muestraBienvenida(muestraTutoriales = null))
    }

    @Test
    fun `con el libro en blanco de verdad sale`() {
        val estado = EstadoTablero(saldos = listOf(cuenta(movimientos = 0)), cargado = true)

        assertTrue(estado.muestraBienvenida(muestraTutoriales = true))
    }

    @Test
    fun `con movimientos no sale`() {
        val estado = EstadoTablero(saldos = listOf(cuenta(movimientos = 3)), cargado = true)

        assertFalse(estado.muestraBienvenida(muestraTutoriales = true))
    }

    @Test
    fun `con los tutoriales apagados no sale`() {
        assertFalse(EstadoTablero(cargado = true).muestraBienvenida(muestraTutoriales = false))
    }
}
