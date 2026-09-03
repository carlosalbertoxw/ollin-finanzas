package com.carlosalbertoxw.ollin.finanzas

import android.app.Application
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.carlosalbertoxw.ollin.finanzas.data.actualizaciones.Resultado
import com.carlosalbertoxw.ollin.finanzas.data.notify.Recordatorios
import com.carlosalbertoxw.ollin.finanzas.di.Contenedor

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

    override fun onCreate() {
        super.onCreate()
        contenedor = Contenedor(this)
        Recordatorios.creaCanal(this)

        alcance.launch {
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
            texto = "Exporta tu respaldo antes de actualizar. Lo de siempre: el .xlsx es lo unico que sobrevive al cambio de telefono.",
            ruta = MainActivity.RUTA_ARCHIVO
        )
        contenedor.ajustes.guardaVersionAvisada(version)
    }
}