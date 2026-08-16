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
import kotlinx.coroutines.launch
import com.carlosalbertoxw.ollin.finanzas.MainActivity
import com.carlosalbertoxw.ollin.finanzas.OllinApp
import com.carlosalbertoxw.ollin.finanzas.R
import com.carlosalbertoxw.ollin.finanzas.data.db.Compromiso
import com.carlosalbertoxw.ollin.finanzas.domain.model.Dinero
import java.time.LocalDate
import java.time.ZoneId

/**
 * Avisos de compromisos por vencer. Un gasto anual como el seguro del carro
 * no deberia sorprenderte: la app lo sabe con meses de anticipacion.
 */
object Recordatorios {

    const val CANAL = "ollin_recordatorios"
    private const val CODIGO_DIARIO = 1001

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

    /** Programa una revision diaria a las 9:00. Se reprograma sola tras cada disparo. */
    fun programaRevisionDiaria(contexto: Context) {
        val gestor = contexto.getSystemService(AlarmManager::class.java) ?: return
        val disparo = LocalDate.now().plusDays(1).atTime(9, 0)
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
            val restantes = c.totalPagos?.let { it - c.pagosRealizados } ?: Int.MAX_VALUE
            if (restantes <= 0) return@mapNotNull null
            val proximo = c.fechaPrimerPago.plusMonths(c.pagosRealizados.toLong() * c.periodicidad.meses)
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
                    val cuando = if (fecha.isBefore(hoy)) "vencio el $fecha" else "el $fecha"
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

class ArranqueReceiver : BroadcastReceiver() {
    override fun onReceive(contexto: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Recordatorios.programaRevisionDiaria(contexto)
        }
    }
}
