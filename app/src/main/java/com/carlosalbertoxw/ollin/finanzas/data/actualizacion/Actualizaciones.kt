package com.carlosalbertoxw.ollin.finanzas.data.actualizacion

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Lo que el sitio publica en `version.json`. */
data class VersionPublicada(
    val codigo: Int,
    val nombre: String,
    val url: String
)

/**
 * Si hay una version mas nueva que la instalada.
 *
 * Ollin Finanzas no se instala desde Play, asi que nadie le avisa a nadie de
 * una version nueva: sin esto, quien tenga el APK se queda con el para siempre
 * sin enterarse. La comprobacion pregunta por un archivo estatico del sitio y
 * **no manda nada**: ni identificador, ni datos del libro, ni la version que
 * traes. Es un GET a una direccion fija, una vez al dia, y se puede apagar en
 * Ajustes.
 *
 * La red vive aqui y solo aqui. Es la unica salida de la app.
 */
object Actualizaciones {

    const val SITIO = "https://carlosalbertoxw.com/ollin-finanzas/"
    const val URL_VERSION = SITIO + "version.json"

    /** Una vez al dia. Un libro de finanzas no cambia de version en una tarde. */
    const val INTERVALO_MS = 24L * 60 * 60 * 1000

    private const val TIEMPO_ESPERA_MS = 8_000

    /**
     * Tope de lectura. El archivo son doscientos bytes; leer sin limite deja
     * que cualquier respuesta inesperada crezca dentro de la memoria de la app.
     */
    private const val TOPE_BYTES = 64 * 1024

    /**
     * Si toca preguntar.
     *
     * El reloj movido hacia atras cuenta como "toca": si solo se mirara
     * `ahora - ultima >= intervalo`, adelantar el reloj un año y devolverlo
     * dejaria la comprobacion congelada hasta alcanzar de nuevo esa fecha.
     */
    fun toca(ultimaBusqueda: Long, ahora: Long): Boolean =
        ultimaBusqueda <= 0L || ahora < ultimaBusqueda || ahora - ultimaBusqueda >= INTERVALO_MS

    /** Hay novedad solo si lo publicado es estrictamente mas nuevo. */
    fun hayNovedad(publicada: Int, instalada: Int): Boolean =
        publicada > 0 && instalada > 0 && publicada > instalada

    /**
     * Lee el json publicado. Devuelve null ante cualquier cosa que no sea lo
     * esperado: esto es un aviso de cortesia, y un archivo raro o a medias no
     * puede tumbar el arranque de la app.
     */
    fun interpreta(json: String?): VersionPublicada? {
        if (json.isNullOrBlank()) return null
        return runCatching {
            val raiz = JSONObject(json)
            val codigo = raiz.getInt("versionCode")
            val nombre = raiz.getString("versionName")
            val url = raiz.optString("url").ifBlank { SITIO }
            // Solo se abre lo que apunte al sitio del proyecto. El archivo es
            // nuestro, pero de aqui sale un enlace que la persona va a tocar:
            // si alguna vez sirviera otra cosa, que no sea un destino libre.
            if (codigo <= 0 || nombre.isBlank() || !url.startsWith(SITIO)) return null
            VersionPublicada(codigo, nombre, url)
        }.getOrNull()
    }

    /**
     * El GET. Sin cookies, sin cabeceras que digan quien eres y con un
     * User-Agent propio: el que trae Android de fabrica nombra el modelo del
     * telefono y su version del sistema, y no hace falta para pedir un archivo.
     */
    suspend fun descarga(url: String = URL_VERSION): String? = withContext(Dispatchers.IO) {
        runCatching {
            val conexion = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIEMPO_ESPERA_MS
                readTimeout = TIEMPO_ESPERA_MS
                useCaches = false
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "OllinFinanzas")
            }
            try {
                if (conexion.responseCode != HttpURLConnection.HTTP_OK) return@runCatching null
                conexion.inputStream.bufferedReader().use { lector ->
                    val destino = CharArray(TOPE_BYTES)
                    val leidos = lector.read(destino, 0, TOPE_BYTES)
                    if (leidos <= 0) null else String(destino, 0, leidos)
                }
            } finally {
                conexion.disconnect()
            }
        }.getOrNull()
    }
}
