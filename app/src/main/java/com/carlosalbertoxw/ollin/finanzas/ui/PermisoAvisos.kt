package com.carlosalbertoxw.ollin.finanzas.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Pide el permiso de notificaciones si hace falta.
 *
 * Desde Android 13 `POST_NOTIFICATIONS` nace denegado: declararlo en el
 * manifiesto no basta. Sin pedirlo, el aviso diario de compromisos se
 * construia y se descartaba en silencio —la app parecia no tener
 * recordatorios— y no habia forma de notarlo desde dentro.
 *
 * Se pide con la app ya desbloqueada, no al arrancar: un dialogo del sistema
 * encima de la pantalla del candado no se entiende, porque todavia no se ha
 * visto de que app viene.
 *
 * Si la persona ya dijo que no dos veces, Android resuelve el permiso sin
 * enseñar nada. Volver a llamarlo no la molesta, asi que no hace falta
 * recordar aparte que ya se pregunto.
 */
@Composable
fun PideAvisos() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val contexto = LocalContext.current
    val lanzador = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Conceder o no es decision suya; la app funciona igual sin avisos. */ }

    LaunchedEffect(Unit) {
        val concedido = ContextCompat.checkSelfPermission(
            contexto,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!concedido) lanzador.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
