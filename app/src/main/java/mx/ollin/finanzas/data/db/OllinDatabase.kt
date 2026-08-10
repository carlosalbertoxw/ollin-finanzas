package mx.ollin.finanzas.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 2,
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

        /**
         * Se va la columna `tipo` de compromiso (MSI, suscripcion, anual...):
         * era una etiqueta que no decidia nada. Quien clasifica el compromiso
         * ahora es su categoria, que ademas sirve para generar el gasto.
         *
         * SQLite no sabe borrar columnas en versiones viejas, asi que se recrea
         * la tabla y se copia. Los indices se rehacen igual que en la v1: si no
         * coinciden con los que Room espera, la validacion de esquema truena al
         * abrir.
         */
        private val DE_1_A_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `compromiso_nuevo` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `nombre` TEXT NOT NULL,
                        `cuentaId` INTEGER,
                        `categoriaId` INTEGER,
                        `montoCentavos` INTEGER NOT NULL,
                        `periodicidad` TEXT NOT NULL,
                        `fechaPrimerPago` INTEGER NOT NULL,
                        `totalPagos` INTEGER,
                        `pagosRealizados` INTEGER NOT NULL,
                        `activo` INTEGER NOT NULL,
                        `avisarDiasAntes` INTEGER NOT NULL,
                        `notas` TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `compromiso_nuevo`
                        (id, nombre, cuentaId, categoriaId, montoCentavos, periodicidad,
                         fechaPrimerPago, totalPagos, pagosRealizados, activo, avisarDiasAntes, notas)
                    SELECT id, nombre, cuentaId, categoriaId, montoCentavos, periodicidad,
                           fechaPrimerPago, totalPagos, pagosRealizados, activo, avisarDiasAntes, notas
                    FROM `compromiso`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `compromiso`")
                db.execSQL("ALTER TABLE `compromiso_nuevo` RENAME TO `compromiso`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_compromiso_cuentaId` ON `compromiso` (`cuentaId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_compromiso_categoriaId` ON `compromiso` (`categoriaId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_compromiso_activo` ON `compromiso` (`activo`)")
            }
        }

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
                .addMigrations(DE_1_A_2)
                .build()
        }
    }
}
