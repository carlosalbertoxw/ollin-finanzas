package mx.ollin.finanzas.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import mx.ollin.finanzas.data.seguridad.LlaveBase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        Cuenta::class,
        Categoria::class,
        Movimiento::class,
        Presupuesto::class,
        Compromiso::class,
        MapeoDescripcion::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Convertidores::class)
abstract class OllinDatabase : RoomDatabase() {

    abstract fun cuentaDao(): CuentaDao
    abstract fun categoriaDao(): CategoriaDao
    abstract fun movimientoDao(): MovimientoDao
    abstract fun presupuestoDao(): PresupuestoDao
    abstract fun compromisoDao(): CompromisoDao
    abstract fun mapeoDescripcionDao(): MapeoDescripcionDao

    companion object {
        private const val NOMBRE = "ollin.db"

        @Volatile private var instancia: OllinDatabase? = null

        /**
         * La base nace cifrada con AES-256 (SQLCipher). La frase la guarda
         * [LlaveBase] envuelta en el Keystore, asi que el archivo .db no sirve
         * de nada fuera de este telefono: copiarlo por adb o sacarlo de un
         * respaldo no revela un solo importe.
         *
         * No hay camino sin cifrar. Si SQLCipher no arranca, la app no abre: es
         * preferible a que un libro de finanzas funcione en claro sin avisar.
         */
        fun obten(contexto: Context): OllinDatabase =
            instancia ?: synchronized(this) {
                instancia ?: construye(contexto.applicationContext).also { instancia = it }
            }

        private fun construye(app: Context): OllinDatabase {
            System.loadLibrary("sqlcipher")
            val frase = LlaveBase.frase(app)
            return Room.databaseBuilder(app, OllinDatabase::class.java, NOMBRE)
                .openHelperFactory(SupportOpenHelperFactory(frase.toByteArray(Charsets.UTF_8)))
                // Las claves foraneas cuidan que no quede un movimiento sin cuenta.
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
        }
    }
}
