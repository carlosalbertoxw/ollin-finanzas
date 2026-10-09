package com.carlosalbertoxw.ollin.finanzas

import android.app.Application
import android.util.Log
import com.carlosalbertoxw.ollin.finanzas.data.actualizaciones.Resultado
import com.carlosalbertoxw.ollin.finanzas.data.diagnostico.RegistroDeFallos
import com.carlosalbertoxw.ollin.finanzas.data.notify.Recordatorios
import com.carlosalbertoxw.ollin.finanzas.di.Contenedor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class OllinApp : Application() {

    lateinit var contenedor: Contenedor
        private set

    /**
     * Lo de arranque que no puede tumbar la app.
     *
     * Un `SupervisorJob` aisla a los hijos entre si, pero **no** se traga lo
     * que revienta: sin este manejador la excepcion sube al handler por
     * omision y cierra el proceso. Aqui dentro solo hay trabajo accesorio
     * --sembrar el catalogo, poner la alarma, preguntar por una version nueva--
     * y ninguno de los tres justifica que la app no abra. La app sin catalogo
     * sembrado se puede usar; la app que se cierra al abrirse, no.
     *
     * Va a logcat para que quede rastro: tragarselo en silencio esconderia un
     * fallo real, y lo que se quiere es que no sea mortal, no que no se sepa.
     */
    private val alcance = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, error ->
            Log.e("OllinApp", "Fallo el trabajo de arranque", error)
        }
    )

    private val _arranqueFallido = MutableStateFlow<Throwable?>(null)

    /**
     * Lo que impidio abrir la base, o nulo si abrio bien.
     *
     * Lo observa [MainActivity] para ensenar una pantalla que lo explique. Sin
     * esto, la base que no abre --SQLCipher que no carga, la llave del Keystore
     * que ya no esta-- solo dejaba una linea en logcat, y despues el primer
     * ViewModel que la tocaba reventaba en el hilo principal: la app se cerraba
     * nada mas abrirse, sin mensaje y sin nada que contarle a nadie.
     */
    val arranqueFallido: StateFlow<Throwable?> = _arranqueFallido.asStateFlow()

    private val _baseAbierta = MutableStateFlow(false)

    /**
     * Cierto en cuanto la base abrio. Hasta entonces [MainActivity] no monta
     * ninguna pantalla con datos: un ViewModel que la tocara antes la abriria
     * en el hilo principal, y si fallaba, la app se cerraria antes de que
     * [arranqueFallido] llegara a decir por que.
     */
    val baseAbierta: StateFlow<Boolean> = _baseAbierta.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        // Lo primero, para que tambien quede rastro de un fallo al construir
        // el contenedor o al abrir la base.
        RegistroDeFallos.instala(this, BuildConfig.VERSION_NAME)
        contenedor = Contenedor(this)
        Recordatorios.creaCanal(this)

        alcance.launch {
            if (!abreLaBase()) return@launch

            // La hora del aviso vive en disco, asi que programar la alarma deja
            // de ser instantaneo. Se hace fuera del hilo principal: onCreate()
            // corre antes de la primera pantalla y bloquearlo se ve como un
            // arranque lento.
            val ajustes = contenedor.ajustes.ajustes.first()
            Recordatorios.programaRevisionDiaria(
                this@OllinApp,
                ajustes.horaAviso,
                ajustes.minutoAviso
            )
            contenedor.sembrador.sembrarSiHaceFalta()

            // El punto desde el que se cuenta la semana del respaldo. Se pone
            // en el primer arranque que ve esta funcion y no en la instalacion:
            // quien ya tenia la app no merece un aviso el mismo dia que
            // actualiza por no haber exportado nunca.
            if (ajustes.anclaDeRespaldo <= 0L) {
                contenedor.ajustes.guardaAnclaDeRespaldo(System.currentTimeMillis())
            }

            // De cortesia y sin prisa: si no hay red o no toca todavia, no
            // pasa nada y se vuelve a intentar en el siguiente arranque.
            runCatching { contenedor.comprobadorActualizaciones.compruebaSiToca() }
                .getOrNull()
                ?.let { avisaDeVersionNueva(it) }
        }
    }

    /**
     * Abre la base aqui, fuera del hilo principal y antes que nadie.
     *
     * Abrirla carga la biblioteca nativa de SQLCipher, desenvuelve la frase del
     * Keystore y corre las migraciones, y el `by lazy` del contenedor es
     * sincronizado: quien llegue primero paga la espera. Sin esto, ese primero
     * podia ser un ViewModel en el hilo principal, y el arranque daba un tiron.
     *
     * `openHelper.writableDatabase` y no solo el objeto: Room no abre el
     * archivo hasta la primera consulta, y una llave equivocada solo se nota al
     * abrirlo.
     *
     * Es lo unico del arranque que si justifica no abrir la app, y por eso no
     * se deja al manejador del [alcance], que solo lo apuntaria en logcat.
     */
    private fun abreLaBase(): Boolean = try {
        contenedor.baseDeDatos.openHelper.writableDatabase
        _baseAbierta.value = true
        true
    } catch (cancelacion: CancellationException) {
        // No es un fallo: es que el alcance se cerro. Atraparla rompe la
        // cancelacion de las corrutinas.
        throw cancelacion
    } catch (fallo: Throwable) {
        // Throwable y no Exception: si SQLCipher no carga, lo que llega es un
        // UnsatisfiedLinkError, y ese es justo el caso en que la app no puede
        // hacer nada y tiene que decirlo bien.
        Log.e("OllinApp", "La base no se pudo abrir", fallo)
        // No cierra el proceso, asi que el manejador de RegistroDeFallos no lo
        // ve. Se guarda a mano: es el fallo que mas importa poder contar.
        RegistroDeFallos.guarda(this, fallo, BuildConfig.VERSION_NAME)
        _arranqueFallido.value = fallo
        false
    }

    /**
     * Avisa de una version nueva, una sola vez por version.
     *
     * Sin recordar de cual se aviso, la comprobacion diaria repetiria la misma
     * notificacion cada dia hasta que alguien actualice, y a la tercera se
     * apaga el canal entero --con lo que tambien se pierden los avisos de
     * compromisos, que son los que de verdad se usan a diario--.
     *
     * Lleva a Archivo y no al sitio de descarga a proposito: lo primero que
     * conviene hacer antes de actualizar es exportar el libro.
     */
    private suspend fun avisaDeVersionNueva(resultado: Resultado) {
        if (resultado !is Resultado.HayVersionNueva) return

        val version = resultado.publicada.version.toString()
        if (contenedor.ajustes.ajustes.first().versionAvisada == version) return

        Recordatorios.notifica(
            this,
            id = Recordatorios.ID_VERSION,
            titulo = "Hay una version nueva: $version",
            texto = "Exporta tu respaldo antes de actualizar. Lo de siempre: el .xlsx es lo " +
                "unico que sobrevive al cambio de telefono.",
            ruta = MainActivity.RUTA_ARCHIVO
        )
        contenedor.ajustes.guardaVersionAvisada(version)
    }
}
