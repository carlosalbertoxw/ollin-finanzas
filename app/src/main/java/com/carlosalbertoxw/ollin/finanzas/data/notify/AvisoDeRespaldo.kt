package com.carlosalbertoxw.ollin.finanzas.data.notify

import android.os.SystemClock
import com.carlosalbertoxw.ollin.finanzas.data.prefs.Ajustes
import com.carlosalbertoxw.ollin.finanzas.data.seguridad.ControlBloqueo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * El aviso de respaldo dentro de la app, arriba del tablero.
 *
 * La notificacion semanal se pierde entre las demas, y una vez descartada ya
 * no vuelve hasta la semana siguiente. Este aviso sale cada vez que se abre la
 * app mientras toque respaldar, y se va solo en cuanto se exporta.
 *
 * Se puede quitar con su boton, pero solo por esta vez: "ahora no" no es
 * "nunca". Vuelve la siguiente vez que se abra la app, que aqui significa
 * arrancarla de cero o regresar despues de haber estado fuera mas de un
 * minuto. Es el mismo minuto que el candado le concede a la vuelta del
 * selector de archivos, y por la misma razon: exportar o importar abre el
 * selector del sistema, que manda la app al fondo, y volver de ahi no es
 * abrirla otra vez. El candado es mas estricto --sin una vuelta esperada no da
 * gracia ninguna--, pero aqui equivocarse solo cuesta ver el aviso de mas.
 *
 * Quien no quiere el recordatorio lo apaga en Ajustes, y entonces no sale ni la
 * notificacion ni esto.
 *
 * Vive en el contenedor y no en el ViewModel del tablero, porque tiene que
 * sobrevivir a que se gire el telefono o se cambie de pestaña: si no, quitarlo
 * duraria hasta el siguiente giro.
 */
class AvisoDeRespaldo(
    private val reloj: () -> Long = { SystemClock.elapsedRealtime() }
) {

    private val _descartado = MutableStateFlow(false)
    val descartado: StateFlow<Boolean> = _descartado.asStateFlow()

    private var salidaEnMillis: Long? = null

    fun descarta() {
        _descartado.value = true
    }

    fun alIrAlFondo() {
        salidaEnMillis = reloj()
    }

    fun alVolverAlFrente() {
        val salida = salidaEnMillis ?: return
        salidaEnMillis = null
        if (reloj() - salida >= AUSENCIA_MILLIS) _descartado.value = false
    }

    companion object {
        /** Cuanto hay que estar fuera para que volver cuente como abrir la app. */
        const val AUSENCIA_MILLIS = ControlBloqueo.GRACIA_MILLIS

        /**
         * El texto del aviso, o nulo si no toca enseñarlo.
         *
         * Las mismas reglas que la notificacion --[Respaldos.toca] y el
         * interruptor de Ajustes-- para que los dos avisos nunca se contradigan.
         */
        fun texto(ajustes: Ajustes, descartado: Boolean, ahora: Long): String? {
            if (descartado || !ajustes.recuerdaRespaldo) return null
            if (!Respaldos.toca(ajustes.ultimoRespaldo, ajustes.anclaDeRespaldo, ahora)) return null
            return Respaldos.textoDelAviso(ajustes.ultimoRespaldo, ajustes.anclaDeRespaldo, ahora)
        }
    }
}
