package com.carlosalbertoxw.ollin.finanzas.ui.seguridad

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.carlosalbertoxw.ollin.finanzas.data.seguridad.ControlBloqueo
import kotlinx.coroutines.delay

/**
 * Segundos que faltan para poder volver a probar el PIN, o cero si se puede ya.
 *
 * La espera la decide [ControlBloqueo], no la pantalla: aqui solo se lleva la
 * cuenta atras para ensenarla. Por eso la pantalla de bloqueo y el dialogo de
 * Ajustes que pide el PIN actual comparten la misma, y abrir uno despues de
 * fallar en el otro no regala un intento.
 */
@Composable
fun segundosDeEsperaPin(bloqueo: ControlBloqueo): Int {
    val hasta by bloqueo.esperaHasta.collectAsState()
    var restantes by remember(hasta) { mutableIntStateOf(bloqueo.segundosDeEspera()) }
    LaunchedEffect(hasta) {
        while (restantes > 0) {
            delay(1_000)
            restantes = bloqueo.segundosDeEspera()
        }
    }
    return restantes
}

/** "Espera 1:05" / "Espera 20 s", para el boton mientras corre el castigo. */
fun textoDeEspera(segundos: Int): String = when {
    segundos >= 60 -> "Espera %d:%02d".format(segundos / 60, segundos % 60)
    else -> "Espera $segundos s"
}
