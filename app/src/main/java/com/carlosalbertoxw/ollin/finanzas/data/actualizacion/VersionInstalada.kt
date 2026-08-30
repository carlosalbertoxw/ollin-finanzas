package com.carlosalbertoxw.ollin.finanzas.data.actualizacion

import android.content.Context
import android.os.Build

/** La version que el telefono tiene puesta. */
data class VersionInstalada(val codigo: Int, val nombre: String) {

    val esConocida: Boolean get() = codigo > 0

    /** "1.0.0 · build 10000". */
    override fun toString(): String = if (esConocida) "$nombre · build $codigo" else nombre
}

/**
 * Se lee del paquete instalado y no de `BuildConfig`: asi la version que se
 * enseña —y con la que se compara la publicada— es la que de verdad esta
 * corriendo, sin tener que activar la generacion de BuildConfig.
 */
fun versionInstalada(contexto: Context): VersionInstalada =
    runCatching {
        val info = contexto.packageManager.getPackageInfo(contexto.packageName, 0)
        val codigo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION") info.versionCode.toLong()
        }
        VersionInstalada(codigo.toInt(), info.versionName ?: "desconocida")
    }.getOrElse { VersionInstalada(0, "desconocida") }
