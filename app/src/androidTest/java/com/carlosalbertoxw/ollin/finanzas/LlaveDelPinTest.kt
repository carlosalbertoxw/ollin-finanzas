package com.carlosalbertoxw.ollin.finanzas

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.carlosalbertoxw.ollin.finanzas.data.seguridad.ClavePin
import com.carlosalbertoxw.ollin.finanzas.data.seguridad.LlaveDelPin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * El sello del PIN con el Keystore de verdad.
 *
 * En la JVM `ControlBloqueoTest` usa un HMAC con llave fija, porque alli no hay
 * Keystore. Lo que solo se puede comprobar aqui es que la llave del telefono
 * se crea, se reutiliza y sella siempre igual: si sellara distinto cada vez, el
 * PIN dejaria de abrir al dia siguiente.
 */
@RunWith(AndroidJUnit4::class)
class LlaveDelPinTest {

    @Test
    fun sellarEsDeterministaEnElMismoTelefono() {
        val huella = ByteArray(32) { it.toByte() }

        val primera = LlaveDelPin.sella(huella)
        val segunda = LlaveDelPin.sella(huella)

        assertArrayEquals(primera, segunda)
        assertEquals("HMAC-SHA256 mide 32 bytes", 32, primera.size)
        assertFalse(primera.contentEquals(huella))
    }

    @Test
    fun unaHuellaSelladaAbreConElPinCorrectoYNoConOtro() = runBlocking {
        val sal = ClavePin.nuevaSal()
        val huella = ClavePin.huella("2468", sal, LlaveDelPin)

        assertTrue(huella.startsWith("ks1:"))
        assertTrue(ClavePin.coincide("2468", huella, sal, LlaveDelPin))
        assertFalse(ClavePin.coincide("1357", huella, sal, LlaveDelPin))
    }

    /** Lo que compra el sello: sin la llave del telefono, la huella no se comprueba. */
    @Test
    fun unaHuellaSelladaNoCoincideFueraDelKeystore() = runBlocking {
        val sal = ClavePin.nuevaSal()
        val huella = ClavePin.huella("2468", sal, LlaveDelPin)
        val otroTelefono = ClavePin.Sello { it.reversedArray() }

        assertFalse(ClavePin.coincide("2468", huella, sal, otroTelefono))
        assertFalse(ClavePin.coincide("2468", huella, sal))
    }
}
