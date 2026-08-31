package com.carlosalbertoxw.ollin.finanzas.data.actualizaciones

import com.carlosalbertoxw.ollin.finanzas.data.prefs.AjustesRepositorio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Lo que el sitio dice de la ultima version publicada. */
data class VersionPublicada(
    val version: Version,
    /** De donde se baja el APK. Se abre en el navegador, nunca se descarga sola. */
    val url: String,
    val notas: String? = null,
    val publicadaEn: String? = null
)

/** En que quedo una comprobacion. */
sealed interface Resultado {
    /** Ollin esta al dia, o el sitio anuncia una version anterior a la instalada. */
    data object AlDia : Resultado

    data class HayVersionNueva(val publicada: VersionPublicada) : Resultado

    /** Sin red, sitio caido, JSON ilegible. Nunca se le ensena al usuario en crudo. */
    data class Fallo(val motivo: String) : Resultado

    /** Ni se intento: el interruptor esta apagado o todavia no toca. */
    data object NoTocaba : Resultado
}

/**
 * Pregunta una vez al dia si hay una version mas nueva.
 *
 * Ollin se distribuye fuera de la tienda, asi que no hay nadie que avise de un
 * fallo corregido: sin esto, quien instalo el APK en marzo sigue con el de
 * marzo para siempre. Por eso la comprobacion nace encendida, y por eso hace lo
 * minimo que sirve para cumplir ese fin y nada mas.
 *
 * **Que sale del telefono.** Una peticion GET a un archivo estatico. No lleva
 * identificador, ni la version instalada, ni un solo importe del libro: la
 * comparacion ocurre aqui dentro, con el JSON ya descargado. Lo unico que el
 * otro extremo puede deducir es que alguien, desde una IP, pidio ese archivo
 * --lo mismo que si se abriera la direccion en el navegador--. Ver
 * docs/seguridad.md.
 *
 * **Que no hace.** No descarga el APK ni lo instala: cuando hay version nueva,
 * la pantalla de Acerca de ensena un boton que abre el sitio en el navegador.
 * Una app que se actualiza sola necesita permisos de instalacion y se convierte
 * en un vector de entrega; abrir un enlace lo decide quien mira la pantalla.
 */
class ComprobadorActualizaciones(
    private val ajustes: AjustesRepositorio,
    /** La que corre en este telefono, de `BuildConfig.VERSION_NAME`. */
    private val instalada: Version?,
    private val url: String,
    /**
     * La descarga, aislada para poder probar el resto sin red. La de verdad es
     * [descargaTexto]; las pruebas pasan una que devuelve un JSON escrito a mano.
     */
    private val descarga: suspend (String) -> String = ::descargaTexto
) {

    /**
     * Lo que hay que llamar al arrancar la app.
     *
     * Decide sola si toca. Se apoya en el reloj del telefono, que se puede
     * mover, y da igual: lo peor que consigue quien lo adelante es comprobar de
     * mas, y una peticion de 200 bytes de mas no le hace dano a nadie. Lo que
     * no puede pasar es preguntar en cada arranque, que con la app abierta
     * veinte veces al dia serian veinte peticiones para saber lo mismo.
     */
    suspend fun compruebaSiToca(ahora: Long = System.currentTimeMillis()): Resultado {
        val preferencias = ajustes.ajustes.first()
        if (!preferencias.buscarActualizaciones) return Resultado.NoTocaba

        val transcurrido = ahora - preferencias.ultimaComprobacion
        // El valor absoluto cubre el reloj movido hacia atras: sin el, atrasar
        // la fecha del telefono dejaria la comprobacion congelada hasta que
        // volviera a alcanzar la marca guardada.
        if (kotlin.math.abs(transcurrido) < UN_DIA_MS) return Resultado.NoTocaba

        return compruebaAhora(ahora)
    }

    /** La comprobacion a mano, desde el boton de Acerca de. No mira el reloj. */
    suspend fun compruebaAhora(ahora: Long = System.currentTimeMillis()): Resultado {
        val cuerpo = runCatching { descarga(url) }
            .getOrElse { return Resultado.Fallo("No se pudo consultar el sitio.") }

        val publicada = lee(cuerpo)
            ?: return Resultado.Fallo("El sitio respondió algo que no entendí.")

        // La marca se guarda pase lo que pase con la comparacion: la pregunta
        // ya se hizo. Un fallo si deja la marca intacta, para reintentar al
        // siguiente arranque en vez de esperar otro dia entero.
        ajustes.guardaComprobacion(
            cuando = ahora,
            version = publicada.version.toString(),
            url = publicada.url,
            notas = publicada.notas
        )

        val esNueva = instalada == null || publicada.version > instalada
        return if (esNueva) Resultado.HayVersionNueva(publicada) else Resultado.AlDia
    }

    companion object {
        const val UN_DIA_MS = 24 * 60 * 60 * 1000L

        /**
         * Interpreta el `version.json` del sitio.
         *
         * Publica porque es la parte que si tiene aristas --campos ausentes,
         * versiones mal escritas, direcciones que no son https-- y se prueba
         * sola, sin levantar un servidor.
         */
        fun lee(json: String): VersionPublicada? {
            val objeto = runCatching { JSONObject(json) }.getOrNull() ?: return null
            val version = Version.de(objeto.optString("version").takeIf { it.isNotBlank() })
                ?: return null

            // Solo https, y solo si viene. Un enlace en claro que llegara desde
            // fuera acabaria abriendo el navegador en una descarga manipulable
            // por cualquiera que este en medio de la red.
            val url = objeto.optString("apk")
                .takeIf { it.startsWith("https://") }
                ?: objeto.optString("sitio").takeIf { it.startsWith("https://") }
                ?: return null

            return VersionPublicada(
                version = version,
                url = url,
                notas = objeto.optString("notas").takeIf { it.isNotBlank() },
                publicadaEn = objeto.optString("publicada").takeIf { it.isNotBlank() }
            )
        }
    }
}

/**
 * Un GET y nada mas, con `HttpURLConnection` del propio Android.
 *
 * Sin OkHttp ni Retrofit: son megabytes y miles de metodos para una peticion
 * que ocurre una vez al dia y devuelve un objeto de cinco campos. Es el mismo
 * criterio con el que el `.xlsx` se escribe a mano en vez de traer Apache POI.
 *
 * El tope de tamano no es paranoia gratuita: es lo unico que entra a la app
 * desde la red, y sin limite un archivo enorme --o un servidor que nunca cierra
 * la respuesta-- agota la memoria del telefono.
 */
private suspend fun descargaTexto(url: String): String = withContext(Dispatchers.IO) {
    var actual = url

    // Un salto y solo uno. La direccion va compilada dentro de cada APK y no se
    // puede cambiar en los que ya estan instalados, asi que tiene que sobrevivir
    // a que el sitio se mude --un dominio propio delante de GitHub Pages deja el
    // .github.io devolviendo un 301 para siempre--. Mas de un salto no hace
    // falta para eso y si permite que una cadena de redirecciones de vueltas.
    repeat(2) {
        when (val respuesta = pide(actual)) {
            is Respuesta.Cuerpo -> return@withContext respuesta.texto
            is Respuesta.Mudanza -> actual = respuesta.destino
        }
    }

    error("Demasiadas redirecciones")
}

private sealed interface Respuesta {
    data class Cuerpo(val texto: String) : Respuesta

    /** Un 3xx con destino valido. */
    data class Mudanza(val destino: String) : Respuesta
}

private fun pide(url: String): Respuesta {
    val conexion = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 10_000
        readTimeout = 10_000
        // Las redirecciones se siguen a mano, no automaticamente: asi se puede
        // exigir que el destino tambien sea https. `HttpURLConnection` ni
        // siquiera sigue solo las que cambian de protocolo, y una que se
        // quedara en http dejaria la respuesta viajando en claro.
        instanceFollowRedirects = false
        setRequestProperty("Accept", "application/json")
    }

    try {
        val codigo = conexion.responseCode

        val mudanza = siguienteSalto(codigo, conexion.getHeaderField("Location"))
        if (mudanza != null) return Respuesta.Mudanza(mudanza)

        if (codigo != HttpURLConnection.HTTP_OK) error("El sitio respondio $codigo")

        return Respuesta.Cuerpo(
            conexion.inputStream.bufferedReader().use { lector ->
                // Se lee en bucle porque `read` no promete llenar el arreglo:
                // devuelve lo que haya llegado, y una respuesta troceada o una
                // red lenta la entregan en varios pedazos. Leerla de una sola
                // llamada funciona casi siempre y falla justo cuando la red va
                // mal, que es el peor momento para partir un JSON por la mitad.
                val bufer = CharArray(TOPE_CARACTERES)
                var total = 0

                while (total < TOPE_CARACTERES) {
                    val leidos = lector.read(bufer, total, TOPE_CARACTERES - total)
                    if (leidos < 0) break
                    total += leidos
                }

                // Si se lleno el tope, la respuesta era mas larga de lo que este
                // archivo puede ser: se descarta entera en vez de leer un JSON
                // cortado, que en el mejor caso no interpreta y en el peor si.
                if (total >= TOPE_CARACTERES) error("La respuesta pasa del tope")

                String(bufer, 0, total)
            }
        )
    } finally {
        conexion.disconnect()
    }
}

/**
 * A donde saltar, o nulo si no hay que saltar a ningun sitio.
 *
 * Separada de la conexion porque es la unica parte de la descarga que decide
 * algo, y asi se prueba sin levantar un servidor. Ver [ActualizacionesTest].
 *
 * **Solo https.** Es toda la razon por la que las redirecciones se siguen a
 * mano: un 301 desde https que apunte a http dejaria la respuesta viajando en
 * claro, y quien este en medio de la red podria anunciar la version que
 * quisiera con el enlace de descarga que quisiera.
 */
internal fun siguienteSalto(codigo: Int, destino: String?): String? {
    if (codigo !in 300..399) return null
    val limpio = destino?.trim().orEmpty()
    return limpio.takeIf { it.startsWith("https://", ignoreCase = true) }
}

/** 64 K caracteres. El archivo real ronda los 400 bytes; esto es holgura, no expectativa. */
private const val TOPE_CARACTERES = 64 * 1024
