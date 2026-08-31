package com.carlosalbertoxw.ollin.finanzas

import android.app.Application
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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

            // De cortesia y sin prisa: si no hay red o no toca todavia, no
            // pasa nada y se vuelve a intentar en el siguiente arranque.
            runCatching { contenedor.comprobadorActualizaciones.compruebaSiToca() }
        }
    }
}
