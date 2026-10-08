package com.carlosalbertoxw.ollin.finanzas

import android.Manifest
import android.os.Build
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.carlosalbertoxw.ollin.finanzas.data.prefs.Ajustes
import com.carlosalbertoxw.ollin.finanzas.data.prefs.ModoBloqueo
import com.carlosalbertoxw.ollin.finanzas.data.seguridad.ClavePin
import com.carlosalbertoxw.ollin.finanzas.data.seguridad.ControlBloqueo
import com.carlosalbertoxw.ollin.finanzas.data.seguridad.LlaveDelPin
import com.carlosalbertoxw.ollin.finanzas.ui.screens.BloqueoPantalla
import com.carlosalbertoxw.ollin.finanzas.ui.seguridad.DialogoPinActual
import com.carlosalbertoxw.ollin.finanzas.ui.theme.TemaOllin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

/**
 * El candado por PIN en pantalla, con el Keystore de verdad.
 *
 * Cada prueba arma su propio [ControlBloqueo] y no usa el del contenedor: ese
 * vive lo que vive el proceso, y lo que una prueba desbloqueara o castigara se
 * le pasaria a la siguiente. Las preferencias y lo que se guarda van en
 * memoria; el sello si es [LlaveDelPin], que es justo lo que la JVM no puede
 * probar.
 *
 * El reloj es de mentira y no avanza solo: con uno de verdad, la espera de un
 * segundo tras el quinto fallo se acabaria a media asercion.
 *
 * La actividad es [MainActivity] solo porque es la `FragmentActivity` que hay;
 * su contenido se reemplaza por la pantalla que se prueba.
 */
@RunWith(AndroidJUnit4::class)
class CandadoPinTest {

    /** El mismo permiso que concede `NavegacionTest`, por la misma razon. */
    @get:Rule(order = 0)
    val avisos = TestRule { siguiente, _ ->
        object : Statement() {
            override fun evaluate() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val instrumentacion = InstrumentationRegistry.getInstrumentation()
                    instrumentacion.uiAutomation.grantRuntimePermission(
                        instrumentacion.targetContext.packageName,
                        Manifest.permission.POST_NOTIFICATIONS
                    )
                }
                siguiente.evaluate()
            }
        }
    }

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private var ahora = 1_000_000L
    private var fallosGuardados = 0

    /**
     * Las preferencias nacen ya con el candado y los fallos puestos: el control
     * fija la espera en su primera lectura, como al reabrir la app, y una
     * primera lectura sin candado lo dejaria abierto.
     */
    private fun preparaControl(fallos: Int = 0): Pair<ControlBloqueo, Ajustes> {
        val sal = ClavePin.nuevaSal()
        val ajustes = Ajustes(
            modoBloqueo = ModoBloqueo.PIN,
            pinHash = runBlocking { ClavePin.huella(PIN, sal, LlaveDelPin) },
            pinSal = sal,
            pinFallos = fallos
        )
        val control = ControlBloqueo(
            preferencias = MutableStateFlow(ajustes),
            guardaFallos = { fallosGuardados = it },
            sello = LlaveDelPin,
            guardaHuella = { _, _ -> },
            reloj = { ahora }
        )
        // La primera lectura corre en el hilo principal; con fallos de mas, se
        // espera a que haya fijado su espera antes de dibujar nada.
        compose.waitUntil(timeoutMillis = 5_000) {
            ControlBloqueo.esperaMillis(fallos) == 0L || control.segundosDeEspera() > 0
        }
        return control to ajustes
    }

    private fun muestra(contenido: @Composable () -> Unit) {
        compose.activityRule.scenario.onActivity { actividad ->
            actividad.setContent { TemaOllin(oscuro = false) { contenido() } }
        }
        compose.waitForIdle()
    }

    private fun teclea(pin: String) {
        compose.onNode(hasSetTextAction()).performTextInput(pin)
    }

    private fun aparece(texto: String) {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText(texto, substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun elPinCorrectoAbre() {
        val (control, ajustes) = preparaControl()
        muestra { BloqueoPantalla(compose.activity, ajustes, control) }

        teclea(PIN)
        compose.onNodeWithText("Entrar").performClick()

        compose.waitUntil(timeoutMillis = 10_000) { !control.bloqueado.value }
    }

    @Test
    fun unPinIncorrectoAvisaYCuentaElFallo() {
        val (control, ajustes) = preparaControl()
        muestra { BloqueoPantalla(compose.activity, ajustes, control) }

        teclea("0000")
        compose.onNodeWithText("Entrar").performClick()

        aparece("PIN incorrecto")
        assertEquals(1, fallosGuardados)
        assertTrue("Un PIN incorrecto no abre", control.bloqueado.value)
    }

    /**
     * Con seis fallos guardados, al abrir ya hay espera: ni el PIN correcto
     * entra hasta que se cumple. Cumplida, el mismo PIN abre.
     */
    @Test
    fun conFallosGuardadosHayQueEsperarAunConElPinCorrecto() {
        val (control, ajustes) = preparaControl(fallos = 6)
        muestra { BloqueoPantalla(compose.activity, ajustes, control) }

        teclea(PIN)
        aparece("Demasiados intentos")
        compose.onNodeWithText("Entrar").assertIsNotEnabled()

        ahora += ControlBloqueo.ESPERA_MAXIMA_MILLIS
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Demasiados intentos", substring = true)
                .fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithText("Entrar").assertIsEnabled().performClick()

        compose.waitUntil(timeoutMillis = 10_000) { !control.bloqueado.value }
    }

    /**
     * El dialogo de Ajustes se abre con la app ya desbloqueada. Hasta la 1.2.0
     * comprobaba el PIN sin freno; ahora pasa por el mismo control y la misma
     * espera que la pantalla de bloqueo.
     */
    @Test
    fun elDialogoDeAjustesRespetaLaMismaEspera() {
        val (control, ajustes) = preparaControl(fallos = 6)
        var confirmado = false
        muestra {
            DialogoPinActual(
                ajustes = ajustes,
                bloqueo = control,
                alConfirmar = { confirmado = true },
                alCancelar = {}
            )
        }

        teclea(PIN)
        aparece("Demasiados intentos")
        compose.onNode(hasText("Confirmar")).assertIsNotEnabled()

        ahora += ControlBloqueo.ESPERA_MAXIMA_MILLIS
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Demasiados intentos", substring = true)
                .fetchSemanticsNodes().isEmpty()
        }
        compose.onNode(hasText("Confirmar")).assertIsEnabled().performClick()

        compose.waitUntil(timeoutMillis = 10_000) { confirmado }
    }

    @Test
    fun elDialogoDeAjustesCuentaLosFallos() {
        val (control, ajustes) = preparaControl()
        muestra {
            DialogoPinActual(
                ajustes = ajustes,
                bloqueo = control,
                alConfirmar = {},
                alCancelar = {}
            )
        }

        teclea("0000")
        compose.onNode(hasText("Confirmar")).performClick()

        aparece("PIN incorrecto")
        assertEquals(1, fallosGuardados)
    }

    private companion object {
        const val PIN = "2468"
    }
}
