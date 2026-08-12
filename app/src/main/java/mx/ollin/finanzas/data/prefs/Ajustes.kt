package mx.ollin.finanzas.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import mx.ollin.finanzas.data.excel.EsquemaExportacion
import mx.ollin.finanzas.data.excel.HojaExportable

private val Context.almacen by preferencesDataStore(name = "ollin_ajustes")

/** Con que se desbloquea Ollin Finanzas al abrirla. */
enum class ModoBloqueo(val etiqueta: String) {
    NINGUNO("Sin bloqueo"),
    /** El patron, PIN, contrasena o huella del propio telefono. */
    SISTEMA("Del telefono"),
    /** Un PIN exclusivo de Ollin Finanzas, distinto al del telefono. */
    PIN("PIN propio")
}

/** Preferencias de exportacion, apariencia y bloqueo. */
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
    val modoBloqueo: ModoBloqueo = ModoBloqueo.NINGUNO,
    /** Del PIN solo se guarda su huella derivada; el PIN en claro no se escribe nunca. */
    val pinHash: String? = null,
    val pinSal: String? = null
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
        val BLOQUEO = stringPreferencesKey("modo_bloqueo")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_SAL = stringPreferencesKey("pin_sal")
    }

    val ajustes: Flow<Ajustes> = contexto.almacen.data.map(::mapea)

    private fun mapea(p: Preferences): Ajustes = Ajustes(
        esquema = p[Claves.ESQUEMA]
            ?.let { runCatching { EsquemaExportacion.valueOf(it) }.getOrNull() }
            ?: EsquemaExportacion.EXTENDIDO,
        hojas = p[Claves.HOJAS]
            ?.mapNotNull { runCatching { HojaExportable.valueOf(it) }.getOrNull() }
            ?.toSet()
            ?.takeIf { it.isNotEmpty() }
            ?: HojaExportable.PREDETERMINADAS,
        corregirAlImportar = p[Claves.CORREGIR] ?: true,
        reemplazarAlImportar = p[Claves.REEMPLAZAR] ?: true,
        temaOscuro = when (p[Claves.TEMA]) {
            "oscuro" -> true
            "claro" -> false
            else -> null
        },
        colorDinamico = p[Claves.DINAMICO] ?: false,
        ultimoArchivo = p[Claves.ULTIMO_ARCHIVO],
        muestraSaldoInicial = p[Claves.SALDO_INICIAL] ?: true,
        muestraTutoriales = p[Claves.TUTORIALES] ?: true,
        modoBloqueo = p[Claves.BLOQUEO]
            ?.let { runCatching { ModoBloqueo.valueOf(it) }.getOrNull() }
            ?: ModoBloqueo.NINGUNO,
        pinHash = p[Claves.PIN_HASH],
        pinSal = p[Claves.PIN_SAL]
    )

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

    suspend fun guardaUltimoArchivo(uri: String?) {
        contexto.almacen.edit {
            if (uri == null) it.remove(Claves.ULTIMO_ARCHIVO) else it[Claves.ULTIMO_ARCHIVO] = uri
        }
    }
}
