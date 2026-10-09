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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
 *
 * Todo PIN que se teclea en la app pasa por [intentaPin], tambien el que pide
 * Ajustes antes de cambiarlo o quitarlo. Si cada pantalla lo comprobara por su
 * cuenta, la que se olvidara del freno seria el atajo para adivinarlo.
 */
class ControlBloqueo(
    preferencias: Flow<Ajustes>,
    private val guardaFallos: suspend (Int) -> Unit,
    /** Sella las huellas del PIN. En la app es [LlaveDelPin]. */
    private val sello: ClavePin.Sello,
    /** Guarda una huella ya sellada en lugar de una vieja, con la misma sal. */
    private val guardaHuella: suspend (hash: String, sal: String) -> Unit,
    private val reloj: () -> Long = { SystemClock.elapsedRealtime() },
    private val ambito: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
) {

    /** En que quedo un intento de PIN. */
    sealed interface IntentoDePin {
        data object Correcto : IntentoDePin
        data object Incorrecto : IntentoDePin

        /** Ni se comprobo: todavia corre la espera de los fallos anteriores. */
        data class EnEspera(val segundos: Int) : IntentoDePin
    }

    /**
     * Un intento a la vez. Sin esto, dos toques seguidos leerian los dos que no
     * hay espera antes de que el primero registrara su fallo.
     */
    private val turno = Mutex()

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

    /**
     * Cierto mientras Ollin espera que el sistema le devuelva algo --el selector
     * de archivos, la pantalla de credencial-- y por eso su marcha al fondo no
     * cuenta como salir de la app.
     */
    private var vueltaEsperada = false

    init {
        ambito.launch {
            var primeraLectura = true
            preferencias.collect { actuales ->
                modo = actuales.modoBloqueo
                fallosDePin = actuales.pinFallos
                if (modo == ModoBloqueo.NINGUNO) _bloqueado.value = false

                // La espera vive en el reloj monotono, que no se puede guardar:
                // se reinicia con el telefono. Por eso al arrancar se vuelve a
                // cobrar entera la que tocan los fallos guardados. Sin esto,
                // cerrar la app despues de cada fallo daba un intento gratis
                // por arranque, y los diez mil PIN volvian a caber en una tarde.
                // Solo en la primera lectura: las siguientes son el eco de
                // [guardaFallos], que ya fijo su propia espera.
                if (primeraLectura) {
                    primeraLectura = false
                    val pendiente = esperaMillis(fallosDePin)
                    if (pendiente > 0) _esperaHasta.value = reloj() + pendiente
                }
            }
        }
    }

    fun desbloquea() {
        _bloqueado.value = false
        salidaEnMillis = null
        vueltaEsperada = false
    }

    /**
     * Avisa de que lo siguiente que va a mandar Ollin al fondo es una pantalla
     * del sistema de la que se espera volver: el selector de archivos al
     * importar o exportar, o la credencial del telefono. Solo esas salidas
     * tienen gracia.
     */
    fun esperaVueltaDelSistema() {
        vueltaEsperada = true
    }

    fun alIrAlFondo() {
        if (!_bloqueado.value) salidaEnMillis = reloj()
    }

    /**
     * Se usa el reloj monotono y no la hora del sistema: cambiar la hora del
     * telefono no debe poder alargar la gracia.
     *
     * Sin una vuelta esperada la gracia es cero y Ollin se cierra en cuanto
     * sale al fondo, que es justo el caso que el candado quiere cubrir: pulsar
     * Inicio y pasarle el telefono a alguien. La gracia se gasta en el primer
     * regreso, vuelva a tiempo o no: no se hereda al siguiente viaje.
     */
    fun alVolverAlFrente() {
        val salida = salidaEnMillis ?: return
        salidaEnMillis = null
        val gracia = if (vueltaEsperada) GRACIA_MILLIS else 0L
        vueltaEsperada = false
        if (modo == ModoBloqueo.NINGUNO) return
        if (reloj() - salida >= gracia) _bloqueado.value = true
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

    /**
     * Comprueba [pin] contra la huella guardada, respetando la espera.
     *
     * Acertar contra una huella de la 1.2.0 o anterior la vuelve a guardar
     * sellada con [sello]: se migra sola, sin pedirle nada a nadie. Si guardarla
     * falla se deja como estaba; volvera a intentarse en el siguiente acierto.
     */
    suspend fun intentaPin(
        pin: String,
        hash: String?,
        sal: String?
    ): IntentoDePin = turno.withLock {
        val espera = segundosDeEspera()
        if (espera > 0) return@withLock IntentoDePin.EnEspera(espera)

        when (ClavePin.verifica(pin, hash, sal, sello)) {
            ClavePin.Verificacion.INCORRECTO -> {
                registraFalloDePin()
                return@withLock IntentoDePin.Incorrecto
            }
            ClavePin.Verificacion.CORRECTO_SIN_SELLAR -> runCatching {
                // hash y sal no son nulos: con cualquiera de los dos nulo no se acierta.
                guardaHuella(ClavePin.huella(pin, sal!!, sello), sal)
            }
            ClavePin.Verificacion.CORRECTO -> Unit
        }
        registraAciertoDePin()
        IntentoDePin.Correcto
    }

    /** La huella y la sal de un PIN nuevo, ya selladas, listas para guardar. */
    suspend fun huellaNueva(pin: String): Pair<String, String> {
        val sal = ClavePin.nuevaSal()
        return ClavePin.huella(pin, sal, sello) to sal
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
         * Un minuto de gracia, solo para una vuelta esperada. Importar y
         * exportar abren el selector de archivos del sistema, que manda la app
         * al fondo; sin este margen, elegir un .xlsx te expulsaria de la app a
         * medio camino. Ver [esperaVueltaDelSistema].
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
