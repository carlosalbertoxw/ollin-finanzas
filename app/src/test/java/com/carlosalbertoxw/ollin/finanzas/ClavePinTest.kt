package com.carlosalbertoxw.ollin.finanzas

import kotlinx.coroutines.test.runTest
import com.carlosalbertoxw.ollin.finanzas.data.seguridad.ClavePin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * El PIN nunca se guarda; lo que se guarda es PBKDF2 sobre el. Estas pruebas
 * son lentas a proposito: cada derivacion son 120 000 iteraciones, que es
 * justamente lo que hace caro probar las diez mil combinaciones de un PIN de
 * cuatro digitos contra el archivo de preferencias.
 */
class ClavePinTest {

    @Test
    fun `acepta el PIN correcto contra su propia huella`() = runTest {
        val sal = ClavePin.nuevaSal()
        val huella = ClavePin.deriva("2468", sal)
        assertTrue(ClavePin.coincide("2468", huella, sal))
    }

    /**
     * Con el mismo prefijo, porque la comparacion es en tiempo constante: si
     * alguien la cambiara por un == normal, este es el caso que lo delata.
     */
    @Test
    fun `rechaza un PIN incorrecto aunque comparta el prefijo`() = runTest {
        val sal = ClavePin.nuevaSal()
        val huella = ClavePin.deriva("2468", sal)
        assertFalse(ClavePin.coincide("2469", huella, sal))
        assertFalse(ClavePin.coincide("246", huella, sal))
        assertFalse(ClavePin.coincide("", huella, sal))
    }

    @Test
    fun `sin hash o sin sal no deja pasar`() = runTest {
        val sal = ClavePin.nuevaSal()
        val huella = ClavePin.deriva("2468", sal)
        assertFalse(ClavePin.coincide("2468", null, sal))
        assertFalse(ClavePin.coincide("2468", huella, null))
        assertFalse(ClavePin.coincide("2468", "", ""))
        assertFalse(ClavePin.coincide("2468", "   ", sal))
    }

    /** Preferencias corruptas no deben tumbar la pantalla de bloqueo. */
    @Test
    fun `un hash o una sal que no son Base64 devuelven false en vez de lanzar`() = runTest {
        val sal = ClavePin.nuevaSal()
        val huella = ClavePin.deriva("2468", sal)
        assertFalse(ClavePin.coincide("2468", "no-es-base64!!", sal))
        assertFalse(ClavePin.coincide("2468", huella, "tampoco!!"))
    }

    @Test
    fun `cada sal es distinta y mide dieciseis bytes`() {
        val a = ClavePin.nuevaSal()
        val b = ClavePin.nuevaSal()
        assertNotEquals(a, b)
        assertEquals(16, Base64.getDecoder().decode(a).size)
        assertEquals(16, Base64.getDecoder().decode(b).size)
    }

    /**
     * La sal por telefono es lo que impide una tabla precalculada compartida:
     * el mismo PIN tiene que dar huellas distintas en dos aparatos.
     */
    @Test
    fun `la derivacion es determinista por sal`() = runTest {
        val salA = ClavePin.nuevaSal()
        val salB = ClavePin.nuevaSal()
        val primera = ClavePin.deriva("2468", salA)
        val segunda = ClavePin.deriva("2468", salA)
        val otraSal = ClavePin.deriva("2468", salB)

        assertEquals(primera, segunda)
        assertNotEquals(primera, otraSal)
    }

    @Test
    fun `el largo minimo declarado es el que exige la interfaz`() {
        assertEquals(4, ClavePin.LARGO_MINIMO)
    }
}
