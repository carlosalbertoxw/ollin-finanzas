package com.carlosalbertoxw.ollin.finanzas.data.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.carlosalbertoxw.ollin.finanzas.MainActivity
import com.carlosalbertoxw.ollin.finanzas.OllinApp
import com.carlosalbertoxw.ollin.finanzas.R
import com.carlosalbertoxw.ollin.finanzas.data.db.Compromiso
import com.carlosalbertoxw.ollin.finanzas.data.prefs.HORA_AVISO_PREDETERMINADA
import com.carlosalbertoxw.ollin.finanzas.data.prefs.MINUTO_AVISO_PREDETERMINADO
import com.carlosalbertoxw.ollin.finanzas.domain.model.Dinero
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Avisos de compromisos por vencer. Un gasto anual como el seguro del carro
 * no deberia sorprenderte: la app lo sabe con meses de anticipacion.
 */
object Recordatorios {

    const val CANAL = "ollin_recordatorios"
    private const val CODIGO_DIARIO = 1001

    /**
     * El aviso lo lee una persona, no un sistema. `LocalDate.toString()` daba
     * "2026-08-16" en la barra de notificaciones.
     */
    private val FORMATO_FECHA: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d 'de' MMMM", Locale("es", "MX"))

    fun formateaFecha(fecha: LocalDate): String = fecha.format(FORMATO_FECHA)

    /**
     * "9:00 a. m." o "9:00", segun como traiga el reloj el telefono: enseñar
     * "9:00 p. m." a quien tiene el sistema en 24 horas se lee como un error de
     * la app.
     */
    private val FORMATO_HORA_12: DateTimeFormatter =
        DateTimeFormatter.ofPattern("h:mm a", Locale("es", "MX"))
    private val FORMATO_HORA_24: DateTimeFormatter =
        DateTimeFormatter.ofPattern("H:mm", Locale("es", "MX"))

    fun formateaHora(hora: Int, minuto: Int, de24Horas: Boolean = false): String =
        LocalTime.of(hora.coerceIn(0, 23), minuto.coerceIn(0, 59))
            .format(if (de24Horas) FORMATO_HORA_24 else FORMATO_HORA_12)

    fun creaCanal(contexto: Context) {
        val gestor = contexto.getSystemService(NotificationManager::class.java) ?: return
        val canal = NotificationChannel(
            CANAL,
            contexto.getString(R.string.canal_recordatorios),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = contexto.getString(R.string.canal_recordatorios_desc)
        }
        gestor.createNotificationChannel(canal)
    }

    /**
     * El proximo instante en que daran las [hora]:[minuto]: hoy si todavia no
     * han dado, mañana si ya pasaron.
     *
     * Nunca `now().plusDays(1)`. Como el arranque vuelve a mirar la alarma,
     * fijarla siempre a un dia vista la correria una jornada mas alla cada vez
     * que se abriera la app por la mañana, y el aviso no llegaria jamas.
     */
    fun proximoDisparo(
        ahora: LocalDateTime,
        hora: Int = HORA_AVISO_PREDETERMINADA,
        minuto: Int = MINUTO_AVISO_PREDETERMINADO
    ): LocalDateTime {
        val hoyALaHora = ahora.toLocalDate().atTime(hora.coerceIn(0, 23), minuto.coerceIn(0, 59))
        return if (ahora.isBefore(hoyALaHora)) hoyALaHora else hoyALaHora.plusDays(1)
    }

    /**
     * Programa la revision diaria. Se reprograma sola tras cada disparo, asi
     * que si ya hay una alarma en pie no se toca: volver a ponerla en cada
     * arranque la empujaria un dia mas alla cada vez.
     *
     * Por eso cambiar la hora en Ajustes no puede pasar por aqui: la alarma
     * vieja existe y este camino la respetaria. Para eso esta
     * [reprogramaRevisionDiaria].
     */
    fun programaRevisionDiaria(contexto: Context, hora: Int, minuto: Int) {
        if (yaProgramada(contexto)) return
        programa(contexto, hora, minuto)
    }

    /**
     * Mueve el aviso diario a una hora nueva. La alarma en pie apunta a la
     * anterior y hay que tumbarla: sin esto el cambio no se notaria hasta que
     * el telefono se reiniciara.
     */
    fun reprogramaRevisionDiaria(contexto: Context, hora: Int, minuto: Int) {
        val gestor = contexto.getSystemService(AlarmManager::class.java) ?: return
        gestor.cancel(intentPendiente(contexto))
        programa(contexto, hora, minuto)
    }

    private fun programa(contexto: Context, hora: Int, minuto: Int) {
        val gestor = contexto.getSystemService(AlarmManager::class.java) ?: return

        val disparo = proximoDisparo(LocalDateTime.now(), hora, minuto)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        // Inexacta a proposito: un recordatorio de finanzas no justifica gastar
        // bateria ni pedir el permiso de alarma exacta.
        gestor.setInexactRepeating(
            AlarmManager.RTC,
            disparo,
            AlarmManager.INTERVAL_DAY,
            intentPendiente(contexto)
        )
    }

    /**
     * FLAG_NO_CREATE devuelve null si no habia ninguna: es la forma de preguntar
     * sin crearla de paso. Un reinicio las borra, y por eso [ArranqueReceiver]
     * vuelve a programarla.
     */
    private fun yaProgramada(contexto: Context): Boolean =
        PendingIntent.getBroadcast(
            contexto,
            CODIGO_DIARIO,
            Intent(contexto, RecordatorioReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) != null

    private fun intentPendiente(contexto: Context): PendingIntent =
        PendingIntent.getBroadcast(
            contexto,
            CODIGO_DIARIO,
            Intent(contexto, RecordatorioReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun notifica(contexto: Context, id: Int, titulo: String, texto: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(contexto, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val abrir = PendingIntent.getActivity(
            contexto,
            id,
            Intent(contexto, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val aviso = NotificationCompat.Builder(contexto, CANAL)
            .setSmallIcon(R.drawable.ic_stat_ollin)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .setContentIntent(abrir)
            .setAutoCancel(true)
            .build()

        runCatching { NotificationManagerCompat.from(contexto).notify(id, aviso) }
    }

    /**
     * Compromisos cuyo proximo pago ya entro en su ventana de aviso, incluidos
     * los que se pasaron de fecha: el plan solo avanza cuando alguien da el
     * pago por cumplido o lo descarta, asi que uno atrasado sigue pendiente
     * hasta que se decida. Se ordenan por fecha, lo mas atrasado primero.
     */
    fun porVencer(compromisos: List<Compromiso>, hoy: LocalDate = LocalDate.now()): List<Pair<Compromiso, LocalDate>> =
        compromisos.filter { it.activo }.mapNotNull { c ->
            if ((c.pagosRestantes ?: Int.MAX_VALUE) <= 0) return@mapNotNull null
            val proximo = c.proximoPago
            val dias = java.time.temporal.ChronoUnit.DAYS.between(hoy, proximo)
            if (dias <= c.avisarDiasAntes.toLong()) c to proximo else null
        }.sortedBy { it.second }
}

class RecordatorioReceiver : BroadcastReceiver() {

    override fun onReceive(contexto: Context, intent: Intent) {
        val app = contexto.applicationContext as? OllinApp ?: return
        val pendiente = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val compromisos = app.contenedor.repositorio.listaCompromisos()
                val hoy = LocalDate.now()
                Recordatorios.porVencer(compromisos).forEachIndexed { i, (compromiso, fecha) ->
                    val texto = Recordatorios.formateaFecha(fecha)
                    val cuando = if (fecha.isBefore(hoy)) "vencio el $texto" else "el $texto"
                    Recordatorios.notifica(
                        contexto,
                        id = 2000 + i,
                        titulo = compromiso.nombre,
                        texto = "${Dinero.formatea(compromiso.montoCentavos)} $cuando"
                    )
                }
            } finally {
                pendiente.finish()
            }
        }
    }
}

/**
 * Un reinicio borra las alarmas del sistema. Esta las vuelve a poner, ya con
 * la hora que la persona haya elegido: leerla obliga a tocar disco, y de ahi
 * el [goAsync].
 */
class ArranqueReceiver : BroadcastReceiver() {
    override fun onReceive(contexto: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = contexto.applicationContext as? OllinApp ?: return
        val pendiente = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val ajustes = app.contenedor.ajustes.ajustes.first()
                Recordatorios.programaRevisionDiaria(
                    contexto,
                    ajustes.horaAviso,
                    ajustes.minutoAviso
                )
            } finally {
                pendiente.finish()
            }
        }
    }
}
