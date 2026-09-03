package com.carlosalbertoxw.ollin.finanzas.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.carlosalbertoxw.ollin.finanzas.data.excel.EsquemaExportacion
import com.carlosalbertoxw.ollin.finanzas.data.excel.HojaExportable

private val Context.almacen by preferencesDataStore(name = "ollin_ajustes")

/** Con que se desbloquea Ollin Finanzas al abrirla. */
enum class ModoBloqueo(val etiqueta: String) {
    NINGUNO("Sin bloqueo"),
    /** El patron, PIN, contrasena o huella del propio telefono. */
    SISTEMA("Del telefono"),
    /** Un PIN exclusivo de Ollin Finanzas, distinto al del telefono. */
    PIN("PIN propio")
}

/** La hora del aviso diario mientras nadie la mueva. */
const val HORA_AVISO_PREDETERMINADA = 9
const val MINUTO_AVISO_PREDETERMINADO = 0

/** Preferencias de exportacion, apariencia, avisos y bloqueo. */
data class Ajustes(
    val esquema: EsquemaExportacion = EsquemaExportacion.EXTENDIDO,
    val hojas: Set<HojaExportable> = HojaExportable.PREDETERMINADAS,
    val corregirAlImportar: Boolean = true,
    val reemplazarAlImportar: Boolean = true,
    val temaOscuro: Boolean? = null,          // null = sigue al sistema
    val colorDinamico: Boolean = false,
    val ultimoArchivo: String? = null,
    /** El saldo inicial solo se ocupa al dar de alta una cuenta; despues estorba. */
    val muestraSaldoInicial: Boolean = true,
    /**
     * Los tutoriales sirven mientras aprendes la app y estorban despues. Apagarlos
     * quita sus atajos del tablero y de las barras; la pantalla completa se sigue
     * pudiendo abrir desde Ajustes, para que apagarlos no sea un camino sin regreso.
     */
    val muestraTutoriales: Boolean = true,
    /**
     * Hora y minuto del aviso diario de compromisos. Las nueve de la mañana son
     * un punto de partida, no una ley: a quien se levanta a las cinco le llega
     * tarde y a quien revisa sus cuentas de noche no le sirve.
     */
    val horaAviso: Int = HORA_AVISO_PREDETERMINADA,
    val minutoAviso: Int = MINUTO_AVISO_PREDETERMINADO,
    /**
     * Preguntarle al sitio, una vez al dia, si hay una version mas nueva.
     *
     * Encendido de fabrica porque la app no se instala desde Play y nadie mas
     * avisa; apagarlo la deja sin ninguna salida a la red.
     */
    val buscarActualizaciones: Boolean = true,
    /** Cuando se pregunto por ultima vez, con respuesta. 0 = nunca. */
    val ultimaComprobacion: Long = 0L,
    /** Lo ultimo que el sitio dijo tener, y a donde manda por ello. */
    val versionPublicada: String? = null,
    val urlDeDescarga: String? = null,
    val notasDeVersion: String? = null,
    /**
     * Recordar cada semana que el respaldo es cosa tuya.
     *
     * Encendido de fabrica: la base va cifrada con una llave del Keystore que
     * no se respalda ni viaja, asi que el .xlsx que exportas es lo unico que
     * sobrevive a perder el telefono. Quien ya tiene la costumbre lo apaga.
     */
    val recuerdaRespaldo: Boolean = true,
    /** Cuando se exporto por ultima vez. 0 = nunca. */
    val ultimoRespaldo: Long = 0L,
    /**
     * Desde cuando se cuenta la semana mientras no haya ningun respaldo. Se
     * pone en el primer arranque que ve esta funcion, no en el de la app: quien
     * ya la tenia instalada empieza a contar hoy y no desde que la instalo.
     */
    val anclaDeRespaldo: Long = 0L,
    /** La version de la que ya se aviso, para no repetir el aviso cada dia. */
    val versionAvisada: String? = null,
    val modoBloqueo: ModoBloqueo = ModoBloqueo.NINGUNO,
    /** Del PIN solo se guarda su huella derivada; el PIN en claro no se escribe nunca. */
    val pinHash: String? = null,
    val pinSal: String? = null,
    /**
     * Intentos fallidos seguidos. En disco y no en memoria: si viviera en el
     * proceso, cerrar la app reiniciaria el contador y el freno contra la
     * fuerza bruta no serviria de nada.
     */
    val pinFallos: Int = 0
)

class AjustesRepositorio(private val contexto: Context) {

    private object Claves {
        val ESQUEMA = stringPreferencesKey("esquema")
        val HOJAS = stringSetPreferencesKey("hojas")
        val CORREGIR = booleanPreferencesKey("corregir_al_importar")
        val REEMPLAZAR = booleanPreferencesKey("reemplazar_al_importar")
        val TEMA = stringPreferencesKey("tema")
        val DINAMICO = booleanPreferencesKey("color_dinamico")
        val ULTIMO_ARCHIVO = stringPreferencesKey("ultimo_archivo")
        val SALDO_INICIAL = booleanPreferencesKey("muestra_saldo_inicial")
        val TUTORIALES = booleanPreferencesKey("muestra_tutoriales")
        val BUSCAR_ACTUALIZACIONES = booleanPreferencesKey("buscar_actualizaciones")
        val ULTIMA_COMPROBACION = longPreferencesKey("ultima_comprobacion")
        /**
         * Nombre nuevo a proposito. La 1.0.0 escribio un entero bajo
         * `version_publicada`, y DataStore guarda el tipo junto al valor:
         * pedirlo como texto lanza ClassCastException. Ver [DE_LA_1_0_0].
         */
        val VERSION_PUBLICADA = stringPreferencesKey("version_publicada_nombre")
        val URL_DESCARGA = stringPreferencesKey("url_descarga")
        val NOTAS_VERSION = stringPreferencesKey("notas_version")
        val RECUERDA_RESPALDO = booleanPreferencesKey("recuerda_respaldo")
        val ULTIMO_RESPALDO = longPreferencesKey("ultimo_respaldo")
        val ANCLA_RESPALDO = longPreferencesKey("ancla_respaldo")
        val VERSION_AVISADA = stringPreferencesKey("version_avisada")
        val HORA_AVISO = intPreferencesKey("hora_aviso")
        val MINUTO_AVISO = intPreferencesKey("minuto_aviso")
        val BLOQUEO = stringPreferencesKey("modo_bloqueo")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_SAL = stringPreferencesKey("pin_sal")
        val PIN_FALLOS = intPreferencesKey("pin_fallos")

        /**
         * Lo que escribio la 1.0.0 y ya nadie lee. Se barre en la primera
         * escritura para no dejarlo ocupando el archivo.
         *
         * `version_publicada` es la importante: guardaba un entero, y ese
         * nombre queda quemado para siempre porque cualquier lectura con otro
         * tipo revienta.
         */
        val DE_LA_1_0_0 = listOf(
            intPreferencesKey("version_publicada"),
            stringPreferencesKey("nombre_version_publicada"),
            stringPreferencesKey("url_version_publicada"),
            longPreferencesKey("ultima_busqueda_version"),
            booleanPreferencesKey("busca_actualizaciones")
        )
    }

    val ajustes: Flow<Ajustes> = contexto.almacen.data.map(::interpreta)

    /**
     * Traduce lo que hay en disco.
     *
     * `internal` para poder probarla con unas preferencias escritas a mano,
     * incluidas las que dejo una version anterior: es donde se descubrio que
     * las de la 1.0.0 cerraban la 1.0.1 al arrancar.
     */
    internal fun interpreta(p: Preferences): Ajustes = conLoGuardado(p.asMap())

    private fun conLoGuardado(p: Map<Preferences.Key<*>, Any>): Ajustes = Ajustes(
        esquema = p.lee(Claves.ESQUEMA)
            ?.let { runCatching { EsquemaExportacion.valueOf(it) }.getOrNull() }
            ?: EsquemaExportacion.EXTENDIDO,
        hojas = p.lee(Claves.HOJAS)
            ?.mapNotNull { runCatching { HojaExportable.valueOf(it) }.getOrNull() }
            ?.toSet()
            ?.takeIf { it.isNotEmpty() }
            ?: HojaExportable.PREDETERMINADAS,
        corregirAlImportar = p.lee(Claves.CORREGIR) ?: true,
        reemplazarAlImportar = p.lee(Claves.REEMPLAZAR) ?: true,
        temaOscuro = when (p.lee(Claves.TEMA)) {
            "oscuro" -> true
            "claro" -> false
            else -> null
        },
        colorDinamico = p.lee(Claves.DINAMICO) ?: false,
        ultimoArchivo = p.lee(Claves.ULTIMO_ARCHIVO),
        muestraSaldoInicial = p.lee(Claves.SALDO_INICIAL) ?: true,
        muestraTutoriales = p.lee(Claves.TUTORIALES) ?: true,
        buscarActualizaciones = p.lee(Claves.BUSCAR_ACTUALIZACIONES) ?: true,
        ultimaComprobacion = p.lee(Claves.ULTIMA_COMPROBACION) ?: 0L,
        versionPublicada = p.lee(Claves.VERSION_PUBLICADA),
        urlDeDescarga = p.lee(Claves.URL_DESCARGA),
        notasDeVersion = p.lee(Claves.NOTAS_VERSION),
        recuerdaRespaldo = p.lee(Claves.RECUERDA_RESPALDO) ?: true,
        ultimoRespaldo = p.lee(Claves.ULTIMO_RESPALDO) ?: 0L,
        anclaDeRespaldo = p.lee(Claves.ANCLA_RESPALDO) ?: 0L,
        versionAvisada = p.lee(Claves.VERSION_AVISADA),
        // Se recorta al leer y no solo al escribir: un valor imposible en
        // disco tiraria la alarma con un IllegalArgumentException al construir
        // la hora, y la app se quedaria sin avisos sin decir por que.
        horaAviso = (p.lee(Claves.HORA_AVISO) ?: HORA_AVISO_PREDETERMINADA).coerceIn(0, 23),
        minutoAviso = (p.lee(Claves.MINUTO_AVISO) ?: MINUTO_AVISO_PREDETERMINADO).coerceIn(0, 59),
        modoBloqueo = p.lee(Claves.BLOQUEO)
            ?.let { runCatching { ModoBloqueo.valueOf(it) }.getOrNull() }
            ?: ModoBloqueo.NINGUNO,
        pinHash = p.lee(Claves.PIN_HASH),
        pinSal = p.lee(Claves.PIN_SAL),
        pinFallos = p.lee(Claves.PIN_FALLOS) ?: 0
    )

    suspend fun guardaFallosDePin(fallos: Int) {
        contexto.almacen.edit {
            if (fallos == 0) it.remove(Claves.PIN_FALLOS) else it[Claves.PIN_FALLOS] = fallos
        }
    }

    /**
     * Las tres transiciones de bloqueo se escriben de golpe. Si el modo y el PIN
     * se guardaran por separado podria quedar un "modo PIN" sin PIN, y eso deja
     * la app cerrada sin llave.
     */
    suspend fun activaBloqueoSistema() {
        contexto.almacen.edit {
            it[Claves.BLOQUEO] = ModoBloqueo.SISTEMA.name
            it.remove(Claves.PIN_HASH)
            it.remove(Claves.PIN_SAL)
        }
    }

    suspend fun activaBloqueoPin(hash: String, sal: String) {
        contexto.almacen.edit {
            it[Claves.BLOQUEO] = ModoBloqueo.PIN.name
            it[Claves.PIN_HASH] = hash
            it[Claves.PIN_SAL] = sal
        }
    }

    suspend fun quitaBloqueo() {
        contexto.almacen.edit {
            it.remove(Claves.BLOQUEO)
            it.remove(Claves.PIN_HASH)
            it.remove(Claves.PIN_SAL)
        }
    }

    suspend fun guardaEsquema(esquema: EsquemaExportacion) {
        contexto.almacen.edit { it[Claves.ESQUEMA] = esquema.name }
    }

    suspend fun guardaHojas(hojas: Set<HojaExportable>) {
        contexto.almacen.edit {
            it[Claves.HOJAS] = HojaExportable.normaliza(hojas).map(HojaExportable::name).toSet()
        }
    }

    suspend fun guardaCorregir(valor: Boolean) {
        contexto.almacen.edit { it[Claves.CORREGIR] = valor }
    }

    suspend fun guardaReemplazar(valor: Boolean) {
        contexto.almacen.edit { it[Claves.REEMPLAZAR] = valor }
    }

    suspend fun guardaTema(oscuro: Boolean?) {
        contexto.almacen.edit {
            when (oscuro) {
                true -> it[Claves.TEMA] = "oscuro"
                false -> it[Claves.TEMA] = "claro"
                null -> it.remove(Claves.TEMA)
            }
        }
    }

    suspend fun guardaColorDinamico(valor: Boolean) {
        contexto.almacen.edit { it[Claves.DINAMICO] = valor }
    }

    suspend fun guardaMuestraSaldoInicial(valor: Boolean) {
        contexto.almacen.edit { it[Claves.SALDO_INICIAL] = valor }
    }

    suspend fun guardaMuestraTutoriales(valor: Boolean) {
        contexto.almacen.edit { it[Claves.TUTORIALES] = valor }
    }

    suspend fun guardaHoraDeAviso(hora: Int, minuto: Int) {
        contexto.almacen.edit {
            it[Claves.HORA_AVISO] = hora.coerceIn(0, 23)
            it[Claves.MINUTO_AVISO] = minuto.coerceIn(0, 59)
        }
    }

    /**
     * Encender y apagar limpia lo que dejo la ultima comprobacion, en los dos
     * sentidos y por razones distintas. Al apagarlo, porque si no quedaria en
     * pantalla el aviso de una version nueva que ya nadie va a volver a
     * comprobar. Al encenderlo, porque quien acaba de activarlo espera
     * enterarse ahora y no cuando venza el dia que corria desde una consulta de
     * hace meses.
     */
    suspend fun guardaBuscarActualizaciones(valor: Boolean) {
        contexto.almacen.edit {
            it[Claves.BUSCAR_ACTUALIZACIONES] = valor
            it.remove(Claves.ULTIMA_COMPROBACION)
            it.remove(Claves.VERSION_PUBLICADA)
            it.remove(Claves.URL_DESCARGA)
            it.remove(Claves.NOTAS_VERSION)
        }
    }

    /**
     * Lo que el sitio contesto, de una sola escritura: la version, su enlace,
     * sus notas y el momento. Por separado podria quedar una version sin enlace
     * a donde mandar, o un "comprobado hace un minuto" sin nada detras.
     */
    suspend fun guardaComprobacion(cuando: Long, version: String, url: String, notas: String?) {
        contexto.almacen.edit {
            // `remove` pide una clave de tipo concreto y estas son de tipos
            // distintos entre si. Da igual: la igualdad de una clave es su
            // nombre, que es lo unico que hace falta para borrarla.
            @Suppress("UNCHECKED_CAST")
            Claves.DE_LA_1_0_0.forEach { vieja -> it.remove(vieja as Preferences.Key<Any>) }
            it[Claves.ULTIMA_COMPROBACION] = cuando
            it[Claves.VERSION_PUBLICADA] = version
            it[Claves.URL_DESCARGA] = url
            if (notas.isNullOrBlank()) it.remove(Claves.NOTAS_VERSION) else it[Claves.NOTAS_VERSION] = notas
        }
    }

    /**
     * Encender o apagar el recordatorio reinicia la cuenta. Al encenderlo,
     * porque quien lo activa hoy no espera un aviso inmediato por no haber
     * exportado nunca; al apagarlo, porque la marca vieja no significa nada
     * cuando se vuelva a encender dentro de meses.
     */
    suspend fun guardaRecuerdaRespaldo(valor: Boolean, momento: Long) {
        contexto.almacen.edit {
            it[Claves.RECUERDA_RESPALDO] = valor
            it[Claves.ANCLA_RESPALDO] = momento
        }
    }

    /** Se exporto: la semana vuelve a contar desde aqui. */
    suspend fun guardaRespaldoHecho(momento: Long) {
        contexto.almacen.edit { it[Claves.ULTIMO_RESPALDO] = momento }
    }

    /** El punto de partida, la primera vez que la app ve esta funcion. */
    suspend fun guardaAnclaDeRespaldo(momento: Long) {
        contexto.almacen.edit { it[Claves.ANCLA_RESPALDO] = momento }
    }

    suspend fun guardaVersionAvisada(version: String) {
        contexto.almacen.edit { it[Claves.VERSION_AVISADA] = version }
    }

    suspend fun guardaUltimoArchivo(uri: String?) {
        contexto.almacen.edit {
            if (uri == null) it.remove(Claves.ULTIMO_ARCHIVO) else it[Claves.ULTIMO_ARCHIVO] = uri
        }
    }
}

/**
 * Lee una clave comprobando su tipo, no confiando en el.
 *
 * DataStore guarda el tipo junto al valor, asi que pedir como texto algo que
 * una version anterior escribio como entero revienta. Y revienta lejos: con
 * los genericos borrados, el `checkcast` no queda dentro de esta funcion sino
 * en el punto donde se usa el valor, asi que envolverla en un `runCatching` no
 * atrapa nada --se probo, y no sirvio--. La unica forma de comprobarlo de
 * verdad es preguntar por el tipo en tiempo de ejecucion, que es lo que hace
 * `as?` con un parametro `reified`.
 *
 * Importa porque esto corre dentro del Flow que alimenta el arranque y todas
 * las pantallas: una excepcion aqui no se queda en una preferencia perdida,
 * cierra la app en el telefono de quien actualiza. Un valor que no cuadra se
 * trata como ausente y se cae al de fabrica.
 *
 * La regla que evita llegar hasta aqui: **una clave no cambia de tipo nunca**.
 * Si el dato cambia de forma se estrena nombre, y el viejo se barre. Esto es la
 * red de abajo, para que el dia que se olvide no cueste una version.
 */
private inline fun <reified T> Map<Preferences.Key<*>, Any>.lee(
    clave: Preferences.Key<T>
): T? = this[clave] as? T
