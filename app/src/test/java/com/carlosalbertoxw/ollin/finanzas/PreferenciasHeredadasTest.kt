package com.carlosalbertoxw.ollin.finanzas

import android.app.Application
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.carlosalbertoxw.ollin.finanzas.data.prefs.AjustesRepositorio
import com.carlosalbertoxw.ollin.finanzas.data.prefs.ModoBloqueo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Actualizar por encima de una version anterior.
 *
 * Esta clase existe por una regresion que costo una version publicada: la 1.0.0
 * guardo la version disponible como **entero** bajo `version_publicada`, la
 * 1.0.1 pidio ese mismo nombre como **texto**, y DataStore --que guarda el tipo
 * junto al valor-- lanzo un ClassCastException dentro del Flow que alimenta el
 * arranque. La app se cerraba al abrirse, pero solo en los telefonos que ya
 * tenian la version anterior instalada.
 *
 * Ninguna de las otras pruebas podia verlo: todas empiezan con el disco vacio,
 * que es el unico escenario donde el problema no existe. Lo que se prueba aqui
 * es lo contrario --que hay algo escrito, y no lo escribio esta version-- y por
 * eso vive en su propio archivo en vez de colgar de las de ajustes.
 *
 * Al agregar una clave nueva, agrega tambien su caso aqui.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PreferenciasHeredadasTest {

    private val repositorio = AjustesRepositorio(ApplicationProvider.getApplicationContext())

    /** Tal cual las dejaba la 1.0.0 en el telefono, con los tipos de entonces. */
    private fun comoLasDejoLa100() = mutablePreferencesOf(
        intPreferencesKey("version_publicada") to 10_000,
        stringPreferencesKey("nombre_version_publicada") to "1.0.0",
        stringPreferencesKey("url_version_publicada") to "https://carlosalbertoxw.com/ollin-finanzas/",
        longPreferencesKey("ultima_busqueda_version") to 1_756_000_000_000L,
        booleanPreferencesKey("busca_actualizaciones") to true
    )

    @Test
    fun `unas preferencias de la 1_0_0 no tumban la app`() {
        // Sin la lectura protegida, esta linea lanza ClassCastException y con
        // ella se cierra la app en el arranque.
        val ajustes = repositorio.interpreta(comoLasDejoLa100())

        assertNull("La version vieja era un entero y no se puede leer como texto", ajustes.versionPublicada)
        assertEquals("Y el resto tiene que quedar en su valor de fabrica", 0L, ajustes.ultimaComprobacion)
    }

    /**
     * Lo que de verdad importa de la version anterior: el libro se abre igual y
     * el candado sigue puesto. Perder el aviso de actualizaciones es un precio
     * que se paga una vez; perder el modo de bloqueo dejaria la app abierta.
     */
    @Test
    fun `lo que la 1_0_0 guardo bien se sigue leyendo`() {
        val preferencias = comoLasDejoLa100().also {
            it[stringPreferencesKey("modo_bloqueo")] = ModoBloqueo.PIN.name
            it[stringPreferencesKey("pin_hash")] = "unahuella"
            it[stringPreferencesKey("pin_sal")] = "unasal"
            it[intPreferencesKey("hora_aviso")] = 7
            it[booleanPreferencesKey("muestra_tutoriales")] = false
        }

        val ajustes = repositorio.interpreta(preferencias)

        assertEquals(ModoBloqueo.PIN, ajustes.modoBloqueo)
        assertEquals("unahuella", ajustes.pinHash)
        assertEquals(7, ajustes.horaAviso)
        assertTrue("Los tutoriales apagados siguen apagados", !ajustes.muestraTutoriales)
    }

    /**
     * La misma trampa desde el otro lado: cualquier clave con un tipo que no
     * corresponde se trata como ausente, no como motivo para cerrar la app.
     */
    @Test
    fun `una clave con el tipo equivocado se ignora y no revienta`() {
        val revueltas = mutablePreferencesOf(
            stringPreferencesKey("hora_aviso") to "las siete",
            intPreferencesKey("modo_bloqueo") to 3,
            longPreferencesKey("muestra_tutoriales") to 1L
        )

        val ajustes = repositorio.interpreta(revueltas)

        assertEquals(9, ajustes.horaAviso)
        assertEquals(ModoBloqueo.NINGUNO, ajustes.modoBloqueo)
        assertTrue(ajustes.muestraTutoriales)
    }

    /** Una instalación nueva no lee nada y sale con lo de fábrica. */
    @Test
    fun `sin nada guardado salen los valores de fabrica`() {
        val ajustes = repositorio.interpreta(mutablePreferencesOf())

        assertEquals(9, ajustes.horaAviso)
        assertEquals(ModoBloqueo.NINGUNO, ajustes.modoBloqueo)
        assertTrue(ajustes.buscarActualizaciones)
        assertNull(ajustes.versionPublicada)
    }
}
