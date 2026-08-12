package mx.ollin.finanzas.data.seguridad

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import mx.ollin.finanzas.data.prefs.AjustesRepositorio
import mx.ollin.finanzas.data.prefs.ModoBloqueo

/**
 * Decide cuando Ollin Finanzas esta cerrada con llave.
 *
 * Vive en el contenedor y no en un ViewModel porque debe sobrevivir a que la
 * actividad se recree: si el estado se perdiera al girar el telefono, girarlo
 * seria la forma de saltarse el candado.
 */
class ControlBloqueo(ajustes: AjustesRepositorio) {

    private val ambito = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * Arranca bloqueada a proposito. Todavia no se sabe si hay candado puesto,
     * y equivocarse hacia el lado cerrado solo cuesta un parpadeo; hacia el
     * lado abierto ensena tus finanzas a quien no debia.
     */
    private val _bloqueado = MutableStateFlow(true)
    val bloqueado: StateFlow<Boolean> = _bloqueado.asStateFlow()

    private var modo = ModoBloqueo.NINGUNO
    private var salidaEnMillis: Long? = null

    init {
        ambito.launch {
            ajustes.ajustes.collect { preferencias ->
                modo = preferencias.modoBloqueo
                if (modo == ModoBloqueo.NINGUNO) _bloqueado.value = false
            }
        }
    }

    fun desbloquea() {
        _bloqueado.value = false
        salidaEnMillis = null
    }

    fun alIrAlFondo() {
        if (!_bloqueado.value) salidaEnMillis = SystemClock.elapsedRealtime()
    }

    /**
     * Se usa el reloj monotono y no la hora del sistema: cambiar la hora del
     * telefono no debe poder alargar la gracia.
     */
    fun alVolverAlFrente() {
        val salida = salidaEnMillis ?: return
        salidaEnMillis = null
        if (modo == ModoBloqueo.NINGUNO) return
        if (SystemClock.elapsedRealtime() - salida >= GRACIA_MILLIS) _bloqueado.value = true
    }

    companion object {
        /**
         * Un minuto de gracia. Importar y exportar abren el selector de archivos
         * del sistema, que manda la app al fondo; sin este margen, elegir un
         * .xlsx te expulsaria de la app a medio camino.
         */
        const val GRACIA_MILLIS = 60_000L
    }
}
