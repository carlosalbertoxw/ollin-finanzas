package com.carlosalbertoxw.ollin.finanzas

import android.app.Application
import com.carlosalbertoxw.ollin.finanzas.data.actualizacion.Actualizaciones
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Las reglas de la busqueda de versiones, sin red de por medio: cuando toca
 * preguntar, que cuenta como novedad y que se acepta del archivo publicado.
 *
 * Necesita Robolectric solo por `org.json`, que en la JVM pelona es un stub que
 * devuelve valores por omision. El `sdk = [34]` y la [Application] pelona son
 * los mismos de [BaseEnMemoria] y por las mismas razones.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ActualizacionesTest {

    private val dia = Actualizaciones.INTERVALO_MS

    // ------------------------------------------------------- cuando preguntar

    @Test
    fun `la primera vez siempre toca`() {
        assertTrue(Actualizaciones.toca(ultimaBusqueda = 0L, ahora = 1_000L))
    }

    @Test
    fun `antes del dia no toca`() {
        assertFalse(Actualizaciones.toca(ultimaBusqueda = 1_000L, ahora = 1_000L + dia - 1))
    }

    @Test
    fun `cumplido el dia toca`() {
        assertTrue(Actualizaciones.toca(ultimaBusqueda = 1_000L, ahora = 1_000L + dia))
    }

    /**
     * El reloj movido hacia atras tiene que volver a permitir la busqueda. Sin
     * esta rama, adelantar el reloj un año y devolverlo dejaria la app sin
     * preguntar hasta alcanzar de nuevo esa fecha.
     */
    @Test
    fun `un reloj movido hacia atras no congela la busqueda`() {
        assertTrue(Actualizaciones.toca(ultimaBusqueda = 5_000_000L, ahora = 1_000L))
    }

    // ------------------------------------------------------- que es novedad

    @Test
    fun `solo hay novedad si lo publicado es mas nuevo`() {
        assertTrue(Actualizaciones.hayNovedad(publicada = 10_100, instalada = 10_000))
        assertFalse(Actualizaciones.hayNovedad(publicada = 10_000, instalada = 10_000))
        assertFalse(Actualizaciones.hayNovedad(publicada = 9_900, instalada = 10_000))
    }

    /** Sin saber que traes puesto no se puede afirmar que estas atrasado. */
    @Test
    fun `sin version instalada conocida no se anuncia novedad`() {
        assertFalse(Actualizaciones.hayNovedad(publicada = 10_100, instalada = 0))
    }

    // ------------------------------------------------- que se acepta del json

    @Test
    fun `interpreta el archivo que publica el sitio`() {
        val publicada = Actualizaciones.interpreta(
            """{"versionCode":10100,"versionName":"1.1.0","url":"${Actualizaciones.SITIO}"}"""
        )

        assertEquals(10_100, publicada?.codigo)
        assertEquals("1.1.0", publicada?.nombre)
    }

    @Test
    fun `sin url usa la del sitio`() {
        val publicada = Actualizaciones.interpreta("""{"versionCode":10100,"versionName":"1.1.0"}""")

        assertEquals(Actualizaciones.SITIO, publicada?.url)
    }

    /**
     * De aqui sale un enlace que alguien va a tocar. Aunque el archivo sea
     * nuestro, un destino fuera del sitio del proyecto no se abre.
     */
    @Test
    fun `una url ajena al sitio se descarta entera`() {
        val publicada = Actualizaciones.interpreta(
            """{"versionCode":10100,"versionName":"1.1.0","url":"https://otro-lugar.example/apk"}"""
        )

        assertNull(publicada)
    }

    @Test
    fun `un archivo roto o vacio no tumba nada`() {
        assertNull(Actualizaciones.interpreta(null))
        assertNull(Actualizaciones.interpreta(""))
        assertNull(Actualizaciones.interpreta("no soy json"))
        assertNull(Actualizaciones.interpreta("""{"versionName":"1.1.0"}"""))
        assertNull(Actualizaciones.interpreta("""{"versionCode":0,"versionName":"1.1.0"}"""))
    }
}
