package com.carlosalbertoxw.ollin.finanzas

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carlosalbertoxw.ollin.finanzas.data.prefs.ModoBloqueo
import com.carlosalbertoxw.ollin.finanzas.ui.OllinRaiz
import com.carlosalbertoxw.ollin.finanzas.ui.PideAvisos
import com.carlosalbertoxw.ollin.finanzas.ui.nav.Rutas
import com.carlosalbertoxw.ollin.finanzas.ui.screens.BloqueoPantalla
import com.carlosalbertoxw.ollin.finanzas.ui.theme.TemaOllin

/**
 * FragmentActivity y no ComponentActivity: el dialogo de huella y credencial
 * del sistema se monta sobre el gestor de fragmentos de la actividad.
 */
class MainActivity : FragmentActivity() {

    companion object {
        /**
         * A que pantalla abrir, cuando se llega desde una notificacion.
         *
         * Se lee una sola vez, al crearse la actividad: si la app ya estaba
         * abierta, tocar el aviso la trae al frente y la deja donde estaba, que
         * es lo que espera quien la tenia a medio usar.
         */
        const val EXTRA_RUTA = "ollin.ruta"

        /**
         * Se delega en [Rutas] en vez de repetir la cadena: Archivo dejo de ser
         * pestaña y su ruta se mudo de sitio, y una copia suelta aqui habria
         * seguido apuntando al mismo texto sin que nada avisara si cambia.
         */
        const val RUTA_ARCHIVO = Rutas.ARCHIVO
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val app = application as OllinApp
        val contenedor = app.contenedor
        val bloqueo = contenedor.controlBloqueo
        val rutaInicial = intent?.getStringExtra(EXTRA_RUTA)

        setContent {
            // null mientras no se leen las preferencias del disco. Distinguirlo de
            // "sin bloqueo" evita ensenar un candado a quien no lo puso.
            val preferencias by contenedor.ajustes.ajustes
                .collectAsStateWithLifecycle(initialValue = null)
            val bloqueado by bloqueo.bloqueado.collectAsStateWithLifecycle()
            val fallo by app.arranqueFallido.collectAsStateWithLifecycle()
            val baseAbierta by app.baseAbierta.collectAsStateWithLifecycle()

            LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
                // Girar el telefono, cambiar de tema o de tamano de ventana
                // tambien detiene la actividad, pero para recrearla al instante:
                // no es salir de la app. Sin esto, ahora que solo las vueltas
                // esperadas tienen gracia, cada giro pediria otra vez la llave.
                if (!isChangingConfigurations) {
                    bloqueo.alIrAlFondo()
                    contenedor.avisoDeRespaldo.alIrAlFondo()
                }
            }
            LifecycleEventEffect(Lifecycle.Event.ON_START) {
                bloqueo.alVolverAlFrente()
                contenedor.avisoDeRespaldo.alVolverAlFrente()
            }

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
                val arranque = fallo
                when {
                    // Antes que nada, incluido el candado: si la base no abrio
                    // no hay libro que proteger, y esta pantalla no ensena ni
                    // un dato. Quedarse en el telon seria dejar a alguien
                    // mirando un fondo liso sin saber que ya no va a pasar nada.
                    arranque != null -> ArranqueFallido(arranque)

                    ajustes == null -> Telon()

                    bloqueado && ajustes.modoBloqueo != ModoBloqueo.NINGUNO -> BloqueoPantalla(
                        actividad = this,
                        ajustes = ajustes,
                        bloqueo = bloqueo
                    )

                    // No hay candado puesto, pero el control aun no lo sabe.
                    bloqueado -> Telon()

                    // El candado no necesita la base; el resto si. Suele ser un
                    // parpadeo: la base empieza a abrirse en cuanto arranca el
                    // proceso, antes de esta pantalla.
                    !baseAbierta -> Telon()

                    else -> {
                        // Aqui y no en onCreate: el permiso se pide con la app
                        // ya abierta, no sobre la pantalla del candado.
                        PideAvisos()
                        OllinRaiz(contenedor, rutaInicial)
                    }
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

/**
 * Cuando la base no se pudo abrir.
 *
 * Lo normal en Ollin es que los mensajes escondan lo interno: el texto crudo de
 * una excepcion habla de rutas y clases, no le sirve a nadie y de paso cuenta
 * como esta hecha la app. Aqui la balanza se inclina al otro lado. La app no va
 * a funcionar, no hay ninguna otra pantalla desde la que enterarse de nada, y
 * sin una linea que copiar no hay forma de reportarlo ni de distinguir "se me
 * corrompio la base" de "esta version no abre en mi telefono". Se ensena el
 * tipo y el mensaje, que es una linea; el detalle completo va a logcat y al
 * informe de fallos de Acerca de.
 */
@Composable
private fun ArranqueFallido(fallo: Throwable) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Ollin Finanzas no pudo abrir tu libro",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Text(
                "La base de datos no se pudo abrir, asi que la app se detuvo antes de " +
                    "tocar nada. Tus datos siguen donde estaban: no se ha borrado ni " +
                    "modificado nada.",
                Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Text(
                "Si acabas de instalar una version encima de otra, lo mas probable es " +
                    "que vengan de compilaciones distintas. Reinstalar desde el sitio " +
                    "suele resolverlo; desinstalar borra el libro, asi que exporta " +
                    "antes a Excel si puedes abrir la version anterior.",
                Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
            Text(
                "${fallo::class.java.simpleName}: ${fallo.message.orEmpty().take(200)}",
                Modifier.padding(top = 24.dp),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center
            )
        }
    }
}
