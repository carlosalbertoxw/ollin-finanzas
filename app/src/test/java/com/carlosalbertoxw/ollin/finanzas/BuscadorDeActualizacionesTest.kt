package com.carlosalbertoxw.ollin.finanzas

import android.app.Application
import com.carlosalbertoxw.ollin.finanzas.data.actualizacion.Actualizaciones
import com.carlosalbertoxw.ollin.finanzas.data.actualizacion.BuscadorDeActualizaciones
import com.carlosalbertoxw.ollin.finanzas.data.actualizacion.ResultadoBusqueda
import com.carlosalbertoxw.ollin.finanzas.data.prefs.Ajustes
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Cuando se pregunta por una version nueva y que se guarda de la respuesta.
 *
 * Sin red y sin DataStore: el buscador recibe el flujo de preferencias, la
 * funcion que guarda, la consulta y el reloj, asi que aqui se le pasan cuatro
 * dobles y las reglas quedan a la vista. Robolectric solo hace falta por
 * `org.json`, que en la JVM pelona es un stub.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BuscadorDeActualizacionesTest {

    private val publicado =
        """{"versionCode":10100,"versionName":"1.1.0","url":"${Actualizaciones.SITIO}"}"""

    /** Lo ultimo que se mando a guardar, o null si no se guardo nada. */
    private var guardado: List<Any>? = null

    private fun buscador(
        ajustes: Ajustes = Ajustes(),
        respuesta: String? = publicado,
        instalada: Int = 10_000,
        ahora: Long = 1_000_000L
    ) = BuscadorDeActualizaciones(
        ajustes = flowOf(ajustes),
        guardaVersion = { codigo, nombre, url, momento ->
            guardado = listOf(codigo, nombre, url, momento)
        },
        versionInstalada = instalada,
        consulta = { respuesta },
        ahora = { ahora }
    )

    // ------------------------------------------------------------ cuando no

    @Test
    fun `apagada en ajustes no pregunta nada`() = runTest {
        val resultado = buscador(Ajustes(buscaActualizaciones = false)).busca()

        assertEquals(ResultadoBusqueda.OMITIDA, resultado)
        assertNull("No debio tocar la red ni guardar nada", guardado)
    }

    @Test
    fun `antes del dia no vuelve a preguntar`() = runTest {
        val ajustes = Ajustes(ultimaBusquedaDeVersion = 1_000_000L - 60_000)

        assertEquals(ResultadoBusqueda.OMITIDA, buscador(ajustes).busca())
        assertNull(guardado)
    }

    // ------------------------------------------------------------ cuando si

    @Test
    fun `cumplido el dia pregunta y guarda lo que encuentra`() = runTest {
        val ajustes = Ajustes(
            ultimaBusquedaDeVersion = 1_000_000L - Actualizaciones.INTERVALO_MS
        )

        assertEquals(ResultadoBusqueda.HAY_NOVEDAD, buscador(ajustes).busca())
        assertEquals(listOf(10_100, "1.1.0", Actualizaciones.SITIO, 1_000_000L), guardado)
    }

    @Test
    fun `con la misma version instalada se reporta al dia`() = runTest {
        val resultado = buscador(instalada = 10_100).busca()

        assertEquals(ResultadoBusqueda.AL_DIA, resultado)
        // Se guarda igual: la fecha del intento sirve aunque no haya novedad.
        assertTrue(guardado != null)
    }

    /**
     * Pedirla a mano tiene que funcionar aunque este apagada y aunque se acabe
     * de preguntar: un boton que no hace nada se lee como una app rota.
     */
    @Test
    fun `forzada ignora el interruptor y el intervalo`() = runTest {
        val ajustes = Ajustes(
            buscaActualizaciones = false,
            ultimaBusquedaDeVersion = 1_000_000L - 1
        )

        assertEquals(ResultadoBusqueda.HAY_NOVEDAD, buscador(ajustes).busca(forzada = true))
    }

    // ------------------------------------------------------ cuando falla

    /**
     * Un dia sin red no puede gastar el turno: si la fecha se guardara igual,
     * la app se quedaria otras 24 horas sin volver a intentarlo.
     */
    @Test
    fun `sin respuesta no se guarda la fecha del intento`() = runTest {
        val resultado = buscador(respuesta = null).busca()

        assertEquals(ResultadoBusqueda.SIN_RESPUESTA, resultado)
        assertNull(guardado)
    }

    @Test
    fun `un json ilegible se trata como si no hubiera respuesta`() = runTest {
        val resultado = buscador(respuesta = "<html>404</html>").busca()

        assertEquals(ResultadoBusqueda.SIN_RESPUESTA, resultado)
        assertNull(guardado)
    }
}
