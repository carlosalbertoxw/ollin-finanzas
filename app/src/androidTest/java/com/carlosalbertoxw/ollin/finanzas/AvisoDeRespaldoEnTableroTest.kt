package com.carlosalbertoxw.ollin.finanzas

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.carlosalbertoxw.ollin.finanzas.data.notify.Respaldos
import com.carlosalbertoxw.ollin.finanzas.ui.nav.Destino
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

/**
 * El aviso de respaldo arriba del tablero, sobre la app de verdad.
 *
 * Las reglas --cuando toca, cuanto dura quitarlo-- ya se prueban en la JVM.
 * Aqui se prueba lo que solo existe en un dispositivo: que el tablero escuche
 * DataStore y que la tarjeta aparezca, se quite con la cruz y se vaya sola
 * cuando hay un respaldo.
 *
 * Un solo test para enseñarlo y quitarlo, a proposito: lo quitado vive en el
 * contenedor, que dura lo que el proceso, y el proceso es el mismo para todas
 * las pruebas de la suite. Partido en dos, el segundo dependeria del orden.
 */
@RunWith(AndroidJUnit4::class)
class AvisoDeRespaldoEnTableroTest {

    private val app: OllinApp
        get() = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as OllinApp

    /**
     * Prepara el disco antes de que arranque la actividad: permiso de avisos
     * concedido (ver NavegacionTest) y una semana y un dia sin respaldo.
     *
     * Primero se espera a que el arranque ponga su ancla. `OllinApp` la pone en
     * una corrutina si no habia ninguna, y si esa corrutina llegara despues de
     * esta escritura la pisaria con "ahora" y el aviso no saldria.
     */
    @get:Rule(order = 0)
    val preparacion = TestRule { siguiente, _ ->
        object : Statement() {
            override fun evaluate() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val instrumentacion = InstrumentationRegistry.getInstrumentation()
                    instrumentacion.uiAutomation.grantRuntimePermission(
                        instrumentacion.targetContext.packageName,
                        Manifest.permission.POST_NOTIFICATIONS
                    )
                }
                runBlocking {
                    val ajustes = app.contenedor.ajustes
                    val limite = System.currentTimeMillis() + 10_000
                    while (ajustes.ajustes.first().anclaDeRespaldo <= 0L &&
                        System.currentTimeMillis() < limite
                    ) {
                        kotlinx.coroutines.delay(100)
                    }
                    val hace8Dias = System.currentTimeMillis() - Respaldos.CADA_MS - 24L * 60 * 60 * 1000
                    ajustes.guardaAnclaDeRespaldo(hace8Dias)
                    ajustes.guardaRespaldoHecho(0L)
                }
                siguiente.evaluate()
            }
        }
    }

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun esperaAQueAbraLaApp() {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText(Destino.TABLERO.titulo)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Deja el disco como si se hubiera respaldado: las demas pruebas no esperan la tarjeta. */
    @After
    fun dejaElRespaldoAlDia() {
        runBlocking { app.contenedor.ajustes.guardaRespaldoHecho(System.currentTimeMillis()) }
    }

    private fun esperaAviso(presente: Boolean) {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText(TITULO).fetchSemanticsNodes().isNotEmpty() == presente
        }
    }

    @Test
    fun elAvisoSaleArribaYLaCruzLoQuita() {
        esperaAviso(presente = true)
        compose.onNodeWithText("Todavia no has exportado tu libro", substring = true).assertExists()

        compose.onNodeWithContentDescription("Quitar el aviso").performClick()

        esperaAviso(presente = false)
    }

    /** Exportar escribe la fecha del respaldo, y con eso basta para que se vaya. */
    @Test
    fun unRespaldoHechoLoQuitaSolo() {
        // Si otro test ya lo quito en este proceso no hay nada que ver aparecer,
        // pero si que no reaparece tras el respaldo; eso es lo que importa aqui.
        runBlocking { app.contenedor.ajustes.guardaRespaldoHecho(System.currentTimeMillis()) }

        esperaAviso(presente = false)
    }

    private companion object {
        const val TITULO = "Respalda tu libro"
    }
}
