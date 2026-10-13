package com.carlosalbertoxw.ollin.finanzas.data.seguridad

import android.os.SystemClock
import androidx.annotation.VisibleForTesting
import com.carlosalbertoxw.ollin.finanzas.data.prefs.Ajustes
import com.carlosalbertoxw.ollin.finanzas.data.prefs.ModoBloqueo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
 * Recibe el flujo de preferencias y las funciones que guardan, no el
 * [com.carlosalbertoxw.ollin.finanzas.data.prefs.AjustesRepositorio] entero. Es
 * codigo de seguridad y sus reglas --arrancar cerrado, la gracia, el freno--
 * tienen que poder probarse sin levantar DataStore, que lee de disco en su
 * propio dispatcher y vuelve las pruebas no deterministas. [reloj] se inyecta
 * por lo mismo: para no dormir de verdad.
 *
 * Todo PIN que se teclea en la app pasa por [intentaPin], tambien el que pide
 * Ajustes antes de cambiarlo o quitarlo. Si cada pantalla lo comprobara por su
 * cuenta, la que se olvidara del freno seria el atajo para adivinarlo.
 *
 * Es la misma clase en Ollin Actividades y en Ollin Finanzas: el candado se
 * comporta igual en las dos.
 */
class ControlBloqueo(
    private val preferencias: Flow<Ajustes>,
    /** Guarda la cuenta de fallos seguidos. Cero la borra. */
    private val guardaFallos: suspend (Int) -> Unit,
    /** Sella las huellas del PIN. En la app es [LlaveDelPin]. */
    private val sello: ClavePin.Sello,
    /** Guarda una huella ya sellada en lugar de una vieja, con la misma sal. */
    private val guardaHuella: suspend (hash: String, sal: String) -> Unit,
    private val reloj: () -> Long = { SystemClock.elapsedRealtime() },
    /**
     * `Main` a secas y no `Main.immediate`. Con `immediate`, construir el
     * control desde el hilo principal ejecuta el colector **dentro del
     * constructor** siempre que DataStore pueda servir de cache, y "arranca
     * bloqueada" deja de ser una garantia para depender de si la cache estaba
     * caliente.
     */
    private val ambito: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
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

    /**
     * Instante del reloj monotono hasta el que no se admite otro intento de PIN.
     * Las pantallas que piden el PIN leen de aqui la cuenta atras.
     */
    private val _esperaHasta = MutableStateFlow(0L)
    val esperaHasta: StateFlow<Long> = _esperaHasta.asStateFlow()

    private var modo = ModoBloqueo.NINGUNO
    private var fallosDePin = 0
    private var salidaEnMillis: Long? = null

    /**
     * Instante del reloj monotono en que se aviso que el sistema va a tapar a
     * Ollin --el selector de archivos, la pantalla de credencial--. Nulo si no
     * hay aviso pendiente. Ver [esperaVueltaDelSistema].
     */
    private var avisoDeVueltaEn: Long? = null

    /**
     * Cierto si la salida en curso es la que se aviso, y por eso no cuenta como
     * salir de la app. Se decide al irse al fondo y se gasta al volver.
     */
    private var salidaEsperada = false

    init {
        ambito.launch {
            var primeraLectura = true
            preferencias.collect { actuales ->
                modo = actuales.modoBloqueo
                fallosDePin = actuales.pinFallos
                if (modo == ModoBloqueo.NINGUNO) _bloqueado.value = false

                // La espera vive en el reloj monotono, que no se guarda: al
                // arrancar se vuelve a cobrar entera la que tocan los fallos que
                // si estan en disco. Asi cerrar la app no regala un intento, y no
                // hay hora guardada que enganar cambiando la del telefono. Solo
                // en la primera lectura: las siguientes son el eco de
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
        avisoDeVueltaEn = null
        salidaEsperada = false
    }

    /**
     * Avisa de que lo siguiente que va a mandar Ollin al fondo es una pantalla
     * del sistema de la que se espera volver: el selector de archivos al
     * importar o exportar, o la credencial del telefono. Solo esas salidas
     * tienen gracia.
     *
     * El aviso caduca a los [VIGENCIA_AVISO_MILLIS]: la pantalla del sistema
     * tapa a Ollin al instante o no la tapa. Sin caducidad, un selector que no
     * llega a abrirse dejaba el aviso armado, y la siguiente salida de verdad
     * --pulsar Inicio un rato despues-- se llevaba el minuto de gracia.
     */
    fun esperaVueltaDelSistema() {
        avisoDeVueltaEn = reloj()
    }

    /**
     * Retira el aviso de [esperaVueltaDelSistema] cuando la pantalla del
     * sistema no llego a abrirse: no hay gestor de archivos, el lanzamiento
     * fallo. La caducidad lo cubriria igual; esto lo cierra en el acto.
     */
    fun cancelaVueltaEsperada() {
        avisoDeVueltaEn = null
    }

    fun alIrAlFondo() {
        val aviso = avisoDeVueltaEn
        avisoDeVueltaEn = null
        if (_bloqueado.value) return
        val ahora = reloj()
        salidaEnMillis = ahora
        salidaEsperada = aviso != null && ahora - aviso <= VIGENCIA_AVISO_MILLIS
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
        val gracia = if (salidaEsperada) GRACIA_MILLIS else 0L
        salidaEsperada = false
        if (modo == ModoBloqueo.NINGUNO) return
        if (reloj() - salida >= gracia) _bloqueado.value = true
    }

    // ------------------------------------------------------- intentos de PIN

    /**
     * Segundos que faltan para poder volver a intentar. Cero si ya se puede.
     *
     * Redondea hacia arriba: mientras quede medio segundo, la pantalla dice
     * "1 s" y no "0 s" con el boton todavia apagado.
     */
    fun segundosDeEspera(): Int {
        val falta = _esperaHasta.value - reloj()
        return if (falta <= 0) 0 else ((falta + 999) / 1_000).toInt()
    }

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
     * La huella se lee de las preferencias en el momento del intento, no de lo
     * que la pantalla tenga a mano: una copia vieja --la de antes de cambiar el
     * PIN-- daria por bueno el anterior.
     */
    suspend fun intentaPin(pin: String): IntentoDePin {
        val actuales = preferencias.first()
        return intentaPin(pin, actuales.pinHash, actuales.pinSal)
    }

    /**
     * Acertar contra una huella de antes del sello la vuelve a guardar sellada
     * con [sello]: se migra sola, sin pedirle nada a nadie. Si guardarla falla
     * se deja como estaba; volvera a intentarse en el siguiente acierto.
     */
    @VisibleForTesting
    internal suspend fun intentaPin(pin: String, hash: String?, sal: String?): IntentoDePin =
        turno.withLock {
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

        /**
         * Lo que puede tardar la pantalla del sistema en tapar a Ollin despues
         * del aviso. Abrir una actividad es cosa de milisegundos; esto deja
         * margen para un telefono lento sin dejar el aviso vivo para despues.
         * Si se queda corto, lo peor es pedir la llave al volver del selector.
         */
        const val VIGENCIA_AVISO_MILLIS = 5_000L

        /** Fallos seguidos que se perdonan sin hacer esperar: teclear mal el PIN es normal. */
        const val FALLOS_DE_GRACIA = 3

        /** Tope de la espera. Mas alla, castigar mas solo estorba al dueno. */
        const val ESPERA_MAXIMA_MILLIS = 300_000L

        /** Segundos de espera a partir del cuarto fallo, uno por escalon. */
        private val ESCALA_SEGUNDOS = listOf(5, 15, 30, 60, 120, 300)

        /**
         * La espera que toca tras [fallos] intentos fallidos seguidos.
         *
         * PBKDF2 encarece cada intento, pero no lo suficiente: un PIN de cuatro
         * digitos son diez mil combinaciones y, a un par de decimas por
         * derivacion, quien tenga el telefono en la mano y sepa automatizar
         * pulsaciones las agota en menos de una hora. La espera crece para que
         * ese ataque deje de caber en una tarde --pasado el tope, cada intento
         * cuesta cinco minutos--, sin castigar a quien de verdad se equivoco dos
         * veces.
         */
        fun esperaMillis(fallos: Int): Long {
            if (fallos <= FALLOS_DE_GRACIA) return 0L
            val segundos = ESCALA_SEGUNDOS.getOrElse(fallos - FALLOS_DE_GRACIA - 1) {
                ESCALA_SEGUNDOS.last()
            }
            return (segundos * 1_000L).coerceAtMost(ESPERA_MAXIMA_MILLIS)
        }
    }
}
