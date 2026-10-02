package com.carlosalbertoxw.ollin.finanzas

import com.carlosalbertoxw.ollin.finanzas.data.diagnostico.RegistroDeFallos
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El informe se escribe mientras la app se esta cayendo, asi que tiene que
 * salir completo a la primera y sin sorpresas de tamano.
 */
class RegistroDeFallosTest {

    @Test
    fun `el informe lleva version, Android y la traza`() {
        val error = IllegalStateException("La base no abrio")

        val informe = RegistroDeFallos.informe(error, version = "1.1.0", cuando = 0L, sdk = 34)

        assertTrue(informe.startsWith("Ollin Finanzas 1.1.0"))
        assertTrue(informe.contains("Android API 34"))
        assertTrue(informe.contains("IllegalStateException: La base no abrio"))
        assertTrue("Sin la traza no hay donde buscar", informe.contains("at "))
    }

    /** Una recursion infinita deja una traza de miles de renglones. */
    @Test
    fun `una traza enorme se recorta`() {
        val error = RuntimeException("x".repeat(RegistroDeFallos.TOPE_CARACTERES * 2))

        val informe = RegistroDeFallos.informe(error, version = "1.1.0", cuando = 0L, sdk = 34)

        assertTrue(informe.length <= RegistroDeFallos.TOPE_CARACTERES + 20)
        assertTrue(informe.endsWith("[recortado]"))
    }
}
