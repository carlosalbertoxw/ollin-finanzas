package com.carlosalbertoxw.ollin.finanzas.data.diagnostico

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Guarda en el telefono el ultimo fallo que cerro la app.
 *
 * Ollin Finanzas no tiene analitica ni reporte de fallos, y no los va a tener:
 * nada sale del telefono. Pero sin ningun rastro, una app que se cierra al
 * abrirse --como la 1.0.1 en los telefonos que venian de la 1.0.0-- solo se
 * descubre cuando alguien lo cuenta, y entonces ya no hay con que diagnosticar.
 *
 * Esto es el punto medio: el informe se queda en el almacenamiento privado de
 * la app, *Acerca de* lo ensena tal cual y la persona decide si lo copia y a
 * quien se lo manda. Solo se guarda el ultimo; uno nuevo pisa al anterior.
 * Esta fuera del respaldo del sistema, igual que la base.
 */
object RegistroDeFallos {

    const val ARCHIVO = "ultimo-fallo.txt"

    /** Un informe no necesita mas: la traza que importa esta en las primeras lineas. */
    const val TOPE_CARACTERES = 16 * 1024

    /**
     * Se encadena delante del manejador que ya hubiera, sin reemplazarlo: el
     * sistema tiene que seguir cerrando el proceso y mostrando su dialogo.
     * Guardar el informe no puede convertirse en un segundo fallo, asi que todo
     * va dentro de un `runCatching`.
     */
    fun instala(contexto: Context, version: String) {
        val app = contexto.applicationContext
        val previo = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { hilo, error ->
            runCatching {
                archivo(app).writeText(
                    informe(error, version, System.currentTimeMillis(), Build.VERSION.SDK_INT)
                )
            }
            previo?.uncaughtException(hilo, error)
        }
    }

    fun lee(contexto: Context): String? =
        runCatching { archivo(contexto).takeIf { it.isFile }?.readText() }.getOrNull()

    fun borra(contexto: Context) {
        runCatching { archivo(contexto).delete() }
    }

    /**
     * El texto del informe. Separado para poder probarlo sin Android.
     *
     * Lleva la version de la app y la de Android porque son lo primero que se
     * pregunta, y nada mas del telefono: ni modelo, ni identificadores.
     */
    fun informe(error: Throwable, version: String, cuando: Long, sdk: Int): String {
        val fecha = DateTimeFormatter.ISO_OFFSET_DATE_TIME
            .format(Instant.ofEpochMilli(cuando).atZone(ZoneId.systemDefault()))
        val traza = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val texto = buildString {
            appendLine("Ollin Finanzas $version")
            appendLine("Android API $sdk")
            appendLine(fecha)
            appendLine()
            append(traza)
        }
        return if (texto.length <= TOPE_CARACTERES) texto
        else texto.take(TOPE_CARACTERES) + "\n[recortado]"
    }

    private fun archivo(contexto: Context) = File(contexto.applicationContext.filesDir, ARCHIVO)
}
