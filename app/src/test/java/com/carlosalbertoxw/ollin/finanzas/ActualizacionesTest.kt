package com.carlosalbertoxw.ollin.finanzas

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.carlosalbertoxw.ollin.finanzas.data.actualizaciones.ComprobadorActualizaciones
import com.carlosalbertoxw.ollin.finanzas.data.actualizaciones.Resultado
import com.carlosalbertoxw.ollin.finanzas.data.actualizaciones.Version
import com.carlosalbertoxw.ollin.finanzas.data.actualizaciones.siguienteSalto
import com.carlosalbertoxw.ollin.finanzas.data.prefs.AjustesRepositorio
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * El aviso de actualizaciones, sin red.
 *
 * La descarga entra por parametro, asi que todo lo que decide algo --comparar
 * versiones, interpretar el JSON, saber si toca preguntar-- se prueba con un
 * texto escrito a mano. Lo unico que queda sin cubrir es el `HttpURLConnection`
 * en si, que no toma ninguna decision salvo el salto, y ese si esta probado.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ActualizacionesTest {

    private lateinit var ajustes: AjustesRepositorio

    @Before
    fun preparar() {
        ajustes = AjustesRepositorio(ApplicationProvider.getApplicationContext())
        // El delegado de DataStore cachea una instancia por nombre y ese cache
        // sobrevive de una prueba a la siguiente: sin devolver estas dos a su
        // sitio, el resultado de la suite dependeria del orden.
        runBlocking {
            ajustes.guardaBuscarActualizaciones(true)
            ajustes.guardaComprobacion(0L, "0.0.0", SITIO, null)
        }
    }

    private fun comprobador(
        instalada: String? = "1.0.0",
        respuesta: String = JSON_1_1_0
    ) = ComprobadorActualizaciones(
        ajustes = ajustes,
        instalada = Version.de(instalada),
        url = "https://ejemplo.invalido/version.json",
        descarga = { respuesta }
    )

    // --------------------------------------------------------------- versiones

    @Test
    fun `una version mayor es posterior aunque el texto ordene al reves`() {
        val diez = Version.de("1.10.0")!!
        val nueve = Version.de("1.9.0")!!

        assertTrue("1.10.0 tiene que ser posterior a 1.9.0", diez > nueve)
        assertTrue("y el orden alfabetico dice lo contrario", "1.10.0" < "1.9.0")
    }

    @Test
    fun `la v del tag y el sufijo de depuracion no cambian de version`() {
        assertEquals(Version(1, 2, 3), Version.de("v1.2.3"))
        assertEquals(Version(1, 2, 3), Version.de("1.2.3-debug"))
        assertEquals(Version(1, 2, 0), Version.de("1.2"))
    }

    @Test
    fun `lo que no es un numero de version no se inventa`() {
        assertNull(Version.de(null))
        assertNull(Version.de(""))
        assertNull(Version.de("ultima"))
        assertNull(Version.de("1.2.3.4"))
    }

    // ------------------------------------------------------------ el version.json

    @Test
    fun `interpreta el archivo que publica el sitio`() {
        val publicada = ComprobadorActualizaciones.lee(JSON_1_1_0)

        assertEquals(Version(1, 1, 0), publicada?.version)
        assertEquals("$SITIO" + "descarga.apk", publicada?.url)
        assertEquals("2026-09-15", publicada?.publicadaEn)
    }

    /** Sin apk se manda al sitio, que es donde estan las instrucciones. */
    @Test
    fun `sin enlace al apk se cae al sitio`() {
        val publicada = ComprobadorActualizaciones.lee(
            """{"version":"1.1.0","sitio":"$SITIO"}"""
        )

        assertEquals(SITIO, publicada?.url)
    }

    /**
     * De aqui sale un enlace que alguien va a tocar. Uno en claro que llegara
     * desde fuera acabaria abriendo el navegador en una descarga que cualquiera
     * en medio de la red puede cambiar.
     */
    @Test
    fun `un enlace que no es https se descarta entero`() {
        assertNull(
            ComprobadorActualizaciones.lee(
                """{"version":"1.1.0","apk":"http://ejemplo.invalido/x.apk"}"""
            )
        )
    }

    @Test
    fun `un archivo roto o incompleto no interpreta nada`() {
        assertNull(ComprobadorActualizaciones.lee(""))
        assertNull(ComprobadorActualizaciones.lee("no soy json"))
        assertNull(ComprobadorActualizaciones.lee("""{"sitio":"$SITIO"}"""))
        assertNull(ComprobadorActualizaciones.lee("""{"version":"ultima","sitio":"$SITIO"}"""))
    }

    // ------------------------------------------------------------ el salto

    @Test
    fun `un 301 hacia https se sigue`() {
        assertEquals(SITIO, siguienteSalto(301, SITIO))
    }

    /** La razon entera de seguir las redirecciones a mano. */
    @Test
    fun `un salto hacia http no se sigue`() {
        assertNull(siguienteSalto(301, "http://ejemplo.invalido/version.json"))
    }

    @Test
    fun `sin 3xx no hay salto`() {
        assertNull(siguienteSalto(200, SITIO))
        assertNull(siguienteSalto(301, null))
    }

    // ------------------------------------------------------ cuando se pregunta

    @Test
    fun `apagada en ajustes no pregunta nada`() = runTest {
        ajustes.guardaBuscarActualizaciones(false)

        assertEquals(Resultado.NoTocaba, comprobador().compruebaSiToca())
    }

    @Test
    fun `antes del dia no vuelve a preguntar`() = runTest {
        val ahora = 5_000_000_000L
        ajustes.guardaComprobacion(ahora, "1.0.0", SITIO, null)

        val resultado = comprobador().compruebaSiToca(ahora + ComprobadorActualizaciones.UN_DIA_MS - 1)

        assertEquals(Resultado.NoTocaba, resultado)
    }

    @Test
    fun `cumplido el dia pregunta y avisa de la version nueva`() = runTest {
        val ahora = 5_000_000_000L
        ajustes.guardaComprobacion(ahora, "1.0.0", SITIO, null)

        val resultado = comprobador().compruebaSiToca(ahora + ComprobadorActualizaciones.UN_DIA_MS)

        assertTrue("Se esperaba una version nueva, salio: $resultado", resultado is Resultado.HayVersionNueva)
        assertEquals("1.1.0", ajustes.ajustes.first().versionPublicada)
    }

    /**
     * Atrasar el reloj del telefono no puede dejar la comprobacion congelada
     * hasta que la fecha vuelva a alcanzar la marca guardada.
     */
    @Test
    fun `un reloj movido hacia atras no congela la comprobacion`() = runTest {
        ajustes.guardaComprobacion(9_000_000_000L, "1.0.0", SITIO, null)

        val resultado = comprobador().compruebaSiToca(1_000L)

        assertTrue(resultado is Resultado.HayVersionNueva)
    }

    @Test
    fun `con la misma version instalada se esta al dia`() = runTest {
        assertEquals(Resultado.AlDia, comprobador(instalada = "1.1.0").compruebaAhora())
    }

    @Test
    fun `una version publicada anterior a la instalada no es novedad`() = runTest {
        assertEquals(Resultado.AlDia, comprobador(instalada = "2.0.0").compruebaAhora())
    }

    // ------------------------------------------------------------- los fallos

    /**
     * Un dia sin red no puede gastar el turno: la marca se queda como estaba
     * para reintentar en el siguiente arranque y no dentro de otras 24 horas.
     */
    @Test
    fun `sin respuesta no se mueve la marca de la ultima comprobacion`() = runTest {
        val roto = ComprobadorActualizaciones(
            ajustes = ajustes,
            instalada = Version.de("1.0.0"),
            url = "https://ejemplo.invalido/version.json",
            descarga = { error("sin red") }
        )

        val resultado = roto.compruebaAhora(7_000L)

        assertTrue(resultado is Resultado.Fallo)
        assertEquals(0L, ajustes.ajustes.first().ultimaComprobacion)
    }

    @Test
    fun `un json ilegible se reporta como fallo y no como novedad`() = runTest {
        val resultado = comprobador(respuesta = "<html>404</html>").compruebaAhora()

        assertTrue(resultado is Resultado.Fallo)
    }

    private companion object {
        const val SITIO = "https://carlosalbertoxw.github.io/ollin-finanzas/"

        val JSON_1_1_0 = """
            {
              "version": "1.1.0",
              "publicada": "2026-09-15",
              "apk": "${SITIO}descarga.apk",
              "sitio": "$SITIO",
              "notas": "Arregla lo que estorbaba."
            }
        """.trimIndent()
    }
}
