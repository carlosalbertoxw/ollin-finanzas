package com.carlosalbertoxw.ollin.finanzas.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.carlosalbertoxw.ollin.finanzas.data.notify.Recordatorios

/**
 * Lo que pide el permiso de avisos justo cuando hace falta, y no antes.
 *
 * Se pide en contexto --al encender un aviso, al guardar algo que va a avisar--
 * y no al abrir la app: es lo que recomienda Android, y un dialogo del sistema
 * que aparece sin motivo a la vista se contesta que no por reflejo. Pedirlo en
 * el gesto que lo necesita deja claro para que es.
 *
 * Devuelve una funcion que recibe que hacer con la respuesta: `true` si ya se
 * puede avisar o se acaba de conceder. Si no hace falta pedir nada, responde en
 * el acto. Si la persona ya dijo que no dos veces, Android resuelve la peticion
 * sin ensenar nada y la respuesta es `false`: por eso Ajustes ofrece ademas el
 * atajo a los ajustes del telefono ([abreAjustesDeAvisos]).
 */
@Composable
fun recuerdaPidePermisoDeAvisos(): ((Boolean) -> Unit) -> Unit {
    val contexto = LocalContext.current
    // Que hacer cuando vuelva la respuesta. El launcher es uno solo y lo
    // comparten todos los que lo piden desde la misma pantalla.
    var alResponder by remember { mutableStateOf<(Boolean) -> Unit>({}) }
    val lanzador = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedido -> alResponder(concedido && Recordatorios.sePuedeAvisar(contexto)) }

    return remember(contexto, lanzador) {
        { responder: (Boolean) -> Unit ->
            val hacePedirlo = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    contexto,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            if (hacePedirlo) {
                alResponder = responder
                lanzador.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                responder(Recordatorios.sePuedeAvisar(contexto))
            }
        }
    }
}

/**
 * Los ajustes de notificaciones de la app en el sistema. Es el unico camino
 * cuando el permiso se nego dos veces o las notificaciones se apagaron a mano.
 */
fun Context.abreAjustesDeAvisos() {
    runCatching {
        startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/**
 * Avisa en Ajustes de que el sistema no deja sonar los avisos, con el atajo
 * para arreglarlo.
 *
 * Se ensena solo cuando pasa, y no como texto fijo: una advertencia permanente
 * sobre algo que casi siempre esta bien se deja de leer a la tercera vez. El
 * estado se relee al volver al frente, porque el permiso se concede en los
 * ajustes del telefono, o sea saliendo de Ollin y regresando.
 */
@Composable
fun AvisosEnRiesgo(pideAvisos: ((Boolean) -> Unit) -> Unit) {
    val contexto = LocalContext.current
    var puedeAvisar by remember { mutableStateOf(Recordatorios.sePuedeAvisar(contexto)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        puedeAvisar = Recordatorios.sePuedeAvisar(contexto)
    }
    if (puedeAvisar) return

    Text(
        "Las notificaciones de Ollin Finanzas están apagadas en el teléfono, " +
            "así que no vas a ver ningún aviso.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error
    )
    TextButton(onClick = {
        // Primero el dialogo del sistema; si ya se nego dos veces, Android no lo
        // ensena, y entonces solo quedan los ajustes del telefono.
        pideAvisos { concedido ->
            puedeAvisar = concedido
            if (!concedido) contexto.abreAjustesDeAvisos()
        }
    }) {
        Text("Permitir avisos")
    }
}
