package com.carlosalbertoxw.ollin.finanzas

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.carlosalbertoxw.ollin.finanzas.ui.nav.Destino
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Navegacion sobre la app de verdad: base cifrada, catalogo sembrado y las
 * pantallas reales. Es lo que las pruebas de JVM no pueden cubrir, porque
 * SQLCipher y el Keystore solo existen en un dispositivo.
 *
 * Los nombres van en camelCase y no entre backticks como en las pruebas de JVM:
 * los espacios en nombres de metodo solo son legales desde la API 30, y el
 * minSdk del proyecto es 26.
 */
@RunWith(AndroidJUnit4::class)
class NavegacionTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    /**
     * La app arranca bloqueada y solo se abre cuando termina de leer las
     * preferencias del disco. Sin esta espera, la prueba mira el telon y falla
     * por una carrera que no tiene nada que ver con lo que se esta probando.
     */
    @Before
    fun esperaAQueAbraLaApp() {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText(Destino.TABLERO.titulo)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * Todo se busca en el arbol **sin fusionar**.
     *
     * `NavigationBarItem` y el boton flotante marcan `mergeDescendants`, asi que
     * en el arbol fusionado sus textos dejan de ser nodos propios y se hunden en
     * el del contenedor: cualquier selector por descendiente encuentra cero.
     *
     * Se apunta al contenedor y no al texto suelto porque el titulo tambien
     * puede aparecer dentro de la pantalla, y entonces el selector seria ambiguo.
     */
    private fun nodo(matcher: SemanticsMatcher): SemanticsNodeInteraction =
        compose.onNode(matcher, useUnmergedTree = true)

    private fun pestana(destino: Destino): SemanticsMatcher =
        isSelectable() and hasAnyDescendant(hasText(destino.titulo))

    private val botonCapturar: SemanticsMatcher =
        hasClickAction() and hasAnyDescendant(hasText("Capturar"))

    @Test
    fun arrancaEnElTableroConLasCincoPestanas() {
        Destino.entries.forEach { destino ->
            nodo(pestana(destino)).assertExists()
        }
        nodo(pestana(Destino.TABLERO)).assertIsSelected()
    }

    @Test
    fun cadaPestanaAbreSuSeccion() {
        Destino.entries.forEach { destino ->
            nodo(pestana(destino)).performClick()
            compose.waitForIdle()
            nodo(pestana(destino)).assertIsSelected()
        }
    }

    @Test
    fun volverALaPestanaAnteriorLaDejaSeleccionada() {
        nodo(pestana(Destino.ANALITICA)).performClick()
        compose.waitForIdle()
        nodo(pestana(Destino.ANALITICA)).assertIsSelected()

        nodo(pestana(Destino.TABLERO)).performClick()
        compose.waitForIdle()
        nodo(pestana(Destino.TABLERO)).assertIsSelected()
    }

    /** La captura no es pestana, asi que la barra de abajo se retira con ella. */
    @Test
    fun elBotonDeCapturarAbreLaCapturaYSePuedeVolver() {
        nodo(botonCapturar).performClick()
        compose.waitForIdle()
        nodo(pestana(Destino.TABLERO)).assertDoesNotExist()

        Espresso.pressBack()
        compose.waitForIdle()
        nodo(pestana(Destino.TABLERO)).assertIsSelected()
    }
}
