package com.carlosalbertoxw.ollin.finanzas

import android.app.Application
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

    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
            runCatching { contenedor.buscadorDeActualizaciones.busca() }
        }
    }
}
