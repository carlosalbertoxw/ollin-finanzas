package com.carlosalbertoxw.ollin.finanzas

import com.carlosalbertoxw.ollin.finanzas.data.notify.Respaldos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cuando toca recordar que hay que respaldar.
 *
 * Es aritmetica de instantes, asi que no necesita Android: se le empuja el
 * "ahora" igual que a los recordatorios de compromisos.
 */
class RespaldosTest {

    private val lunes = 1_756_000_000_000L
    private val semana = Respaldos.CADA_MS

    // ------------------------------------------------------- desde cuando

    /**
     * Sin ancla no hay de donde contar. Es el estado de una instalacion que
     * todavia no ha llegado a su primer arranque completo, y avisar ahi seria
     * avisar por un tiempo que nadie ha medido.
     */
    @Test
    fun `sin punto de partida no se avisa`() {
        assertFalse(Respaldos.toca(ultimoRespaldo = 0L, ancla = 0L, ahora = lunes))
    }

    @Test
    fun `manda el respaldo mas reciente sobre el ancla`() {
        val ancla = lunes
        val respaldo = lunes + semana

        assertEquals(respaldo, Respaldos.cuentaDesde(ultimoRespaldo = respaldo, ancla = ancla))
    }

    // ------------------------------------------------------------ la semana

    @Test
    fun `antes de la semana no se avisa`() {
        assertFalse(Respaldos.toca(ultimoRespaldo = 0L, ancla = lunes, ahora = lunes + semana - 1))
    }

    @Test
    fun `cumplida la semana se avisa`() {
        assertTrue(Respaldos.toca(ultimoRespaldo = 0L, ancla = lunes, ahora = lunes + semana))
    }

    /** Exportar reinicia la cuenta: el aviso pedia justo eso. */
    @Test
    fun `un respaldo reciente calla el aviso aunque el ancla sea vieja`() {
        val ancla = lunes
        val respaldo = lunes + semana * 3
        val ahora = respaldo + semana - 1

        assertFalse(Respaldos.toca(ultimoRespaldo = respaldo, ancla = ancla, ahora = ahora))
    }

    @Test
    fun `pasada otra semana desde el ultimo respaldo se vuelve a avisar`() {
        val respaldo = lunes
        assertTrue(Respaldos.toca(ultimoRespaldo = respaldo, ancla = 0L, ahora = respaldo + semana))
    }

    /**
     * Igual que en la comprobacion de versiones: adelantar el reloj y volverlo
     * atras no puede dejar el aviso congelado esperando a alcanzar una marca
     * del futuro. Un recordatorio de mas no le hace dano a nadie.
     */
    @Test
    fun `un reloj movido hacia atras no congela el aviso`() {
        assertTrue(Respaldos.toca(ultimoRespaldo = lunes + semana * 5, ancla = lunes, ahora = lunes))
    }

    // -------------------------------------------------------- que se dice

    @Test
    fun `sin ningun respaldo el aviso lo dice sin rodeos`() {
        val texto = Respaldos.textoDelAviso(ultimoRespaldo = 0L, ancla = lunes, ahora = lunes + semana)

        assertTrue("Debe decir que no hay respaldo, salio: $texto", texto.contains("Todavia no"))
    }

    /**
     * El aviso nombra los dias que llevan sin respaldo: "hace 21 dias" mueve a
     * exportar, y "acuerdate de respaldar" ya no lo lee nadie a la tercera
     * semana.
     */
    @Test
    fun `con un respaldo viejo el aviso dice cuantos dias lleva`() {
        val respaldo = lunes
        val texto = Respaldos.textoDelAviso(respaldo, ancla = 0L, ahora = respaldo + semana * 3)

        assertTrue("Debe nombrar los 21 dias, salio: $texto", texto.contains("21"))
    }

    @Test
    fun `los dias se cuentan enteros`() {
        assertEquals(7, Respaldos.diasDesde(lunes, lunes + semana))
        assertEquals(0, Respaldos.diasDesde(lunes, lunes + 1_000))
    }
}
