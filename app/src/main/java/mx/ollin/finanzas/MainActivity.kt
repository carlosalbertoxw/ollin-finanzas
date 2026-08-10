package mx.ollin.finanzas

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.ollin.finanzas.data.prefs.ModoBloqueo
import mx.ollin.finanzas.ui.OllinRaiz
import mx.ollin.finanzas.ui.screens.BloqueoPantalla
import mx.ollin.finanzas.ui.theme.TemaOllin

/**
 * FragmentActivity y no ComponentActivity: el dialogo de huella y credencial
 * del sistema se monta sobre el gestor de fragmentos de la actividad.
 */
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val contenedor = (application as OllinApp).contenedor
        val bloqueo = contenedor.controlBloqueo

        setContent {
            // null mientras no se leen las preferencias del disco. Distinguirlo de
            // "sin bloqueo" evita ensenar un candado a quien no lo puso.
            val preferencias by contenedor.ajustes.ajustes
                .collectAsStateWithLifecycle(initialValue = null)
            val bloqueado by bloqueo.bloqueado.collectAsStateWithLifecycle()

            LifecycleEventEffect(Lifecycle.Event.ON_STOP) { bloqueo.alIrAlFondo() }
            LifecycleEventEffect(Lifecycle.Event.ON_START) { bloqueo.alVolverAlFrente() }

            // Con candado puesto se marca la ventana como segura: ni capturas de
            // pantalla ni miniatura en la vista de apps recientes, que es donde el
            // sistema deja tu patrimonio a la vista de cualquiera. Mientras no se
            // sabe, se asume que si (quitarlo de mas es peor que ponerlo de mas).
            val protegerVentana = preferencias?.modoBloqueo?.let { it != ModoBloqueo.NINGUNO } ?: true
            LaunchedEffect(protegerVentana) {
                if (protegerVentana) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }

            TemaOllin(
                oscuro = preferencias?.temaOscuro ?: isSystemInDarkTheme(),
                colorDinamico = preferencias?.colorDinamico ?: false
            ) {
                val ajustes = preferencias
                when {
                    ajustes == null -> Telon()

                    bloqueado && ajustes.modoBloqueo != ModoBloqueo.NINGUNO -> BloqueoPantalla(
                        actividad = this,
                        ajustes = ajustes,
                        alDesbloquear = bloqueo::desbloquea
                    )

                    // No hay candado puesto, pero el control aun no lo sabe.
                    bloqueado -> Telon()

                    else -> OllinRaiz(contenedor)
                }
            }
        }
    }
}

/** Fondo liso mientras se decide si hay candado. Nunca muestra datos. */
@Composable
private fun Telon() {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {}
}
