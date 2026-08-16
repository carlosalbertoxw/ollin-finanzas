package com.carlosalbertoxw.ollin.finanzas.data.seguridad

import android.os.SystemClock
import com.carlosalbertoxw.ollin.finanzas.data.prefs.Ajustes
import com.carlosalbertoxw.ollin.finanzas.data.prefs.ModoBloqueo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Decide cuando Ollin Finanzas esta cerrada con llave.
 *
 * Vive en el contenedor y no en un ViewModel porque debe sobrevivir a que la
 * actividad se recree: si el estado se perdiera al girar el telefono, girarlo
 * seria la forma de saltarse el candado.
 *
 * Recibe el flujo de preferencias y la funcion que guarda los fallos, no el
 * [AjustesRepositorio] entero. Es codigo de seguridad y sus reglas —arrancar
 * cerrado, la gracia, el freno— tienen que poder probarse sin levantar
 * DataStore, que lee de disco en su propio dispatcher y volvia las pruebas no
 * deterministas. [reloj] se inyecta por lo mismo: para no dormir de verdad.
 */
class ControlBloqueo(
    preferencias: Flow<Ajustes>,
    private val guardaFallos: suspend (Int) -> Unit,
    private val reloj: () -> Long = { SystemClock.elapsedRealtime() },
    private val ambito: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
) {

    /**
     * Arranca bloqueada a proposito. Todavia no se sabe si hay candado puesto,
     * y equivocarse hacia el lado cerrado solo cuesta un parpadeo; hacia el
     * lado abierto ensena tus finanzas a quien no debia.
     */
    private val _bloqueado = MutableStateFlow(true)
    val bloqueado: StateFlow<Boolean> = _bloqueado.asStateFlow()

    /** Instante del reloj monotono hasta el que no se admite otro intento de PIN. */
    private val _esperaHasta = MutableStateFlow(0L)
    val esperaHasta: StateFlow<Long> = _esperaHasta.asStateFlow()

    private var modo = ModoBloqueo.NINGUNO
    private var fallosDePin = 0
    private var salidaEnMillis: Long? = null

    init {
        ambito.launch {
            preferencias.collect { actuales ->
                modo = actuales.modoBloqueo
                fallosDePin = actuales.pinFallos
                if (modo == ModoBloqueo.NINGUNO) _bloqueado.value = false
            }
        }
    }

    fun desbloquea() {
        _bloqueado.value = false
        salidaEnMillis = null
    }

    fun alIrAlFondo() {
        if (!_bloqueado.value) salidaEnMillis = reloj()
    }

    /**
     * Se usa el reloj monotono y no la hora del sistema: cambiar la hora del
     * telefono no debe poder alargar la gracia.
     */
    fun alVolverAlFrente() {
        val salida = salidaEnMillis ?: return
        salidaEnMillis = null
        if (modo == ModoBloqueo.NINGUNO) return
        if (reloj() - salida >= GRACIA_MILLIS) _bloqueado.value = true
    }

    // ------------------------------------------------------- intentos de PIN

    /** Segundos que faltan para poder volver a intentar. Cero si ya se puede. */
    fun segundosDeEspera(): Int =
        ((_esperaHasta.value - reloj()) / 1000L).coerceAtLeast(0L).toInt()

    /**
     * Un PIN de cuatro digitos son diez mil combinaciones. PBKDF2 ya hace que
     * cada intento cueste, pero sin freno alguien con el telefono en la mano
     * puede seguir probando indefinidamente. El conteo se guarda en disco: si
     * viviera en memoria, cerrar la app seria la forma de reiniciarlo.
     */
    suspend fun registraFalloDePin() {
        val fallos = fallosDePin + 1
        fallosDePin = fallos
        _esperaHasta.value = reloj() + esperaMillis(fallos)
        guardaFallos(fallos)
    }

    /** Acertar limpia la cuenta: el freno es contra el que adivina, no contra ti. */
    suspend fun registraAciertoDePin() {
        fallosDePin = 0
        _esperaHasta.value = 0L
        guardaFallos(0)
        desbloquea()
    }

    companion object {
        /**
         * Un minuto de gracia. Importar y exportar abren el selector de archivos
         * del sistema, que manda la app al fondo; sin este margen, elegir un
         * .xlsx te expulsaria de la app a medio camino.
         */
        const val GRACIA_MILLIS = 60_000L

        /** Los primeros cuatro fallos salen gratis: teclear mal el PIN es normal. */
        const val FALLOS_DE_GRACIA = 4

        /** Tope de la espera. Mas alla, castigar mas solo estorba al dueno. */
        const val ESPERA_MAXIMA_MILLIS = 300_000L

        /**
         * Duplica en cada fallo a partir del quinto: 1 s, 2 s, 4 s... hasta el
         * tope. Diez mil intentos dejan de caber en una tarde.
         */
        fun esperaMillis(fallos: Int): Long {
            if (fallos <= FALLOS_DE_GRACIA) return 0L
            val pasos = (fallos - FALLOS_DE_GRACIA - 1).coerceAtMost(20)
            return (1_000L shl pasos).coerceAtMost(ESPERA_MAXIMA_MILLIS)
        }
    }
}
