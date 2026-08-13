package mx.ollin.finanzas

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import mx.ollin.finanzas.data.db.Categoria
import mx.ollin.finanzas.data.db.Cuenta
import mx.ollin.finanzas.data.db.MapeoDescripcion
import mx.ollin.finanzas.data.db.Movimiento
import mx.ollin.finanzas.data.db.OllinDatabase
import mx.ollin.finanzas.data.repo.FinanzasRepositorio
import mx.ollin.finanzas.domain.model.Contraparte
import mx.ollin.finanzas.domain.model.Medio
import mx.ollin.finanzas.domain.model.TipoCategoria
import mx.ollin.finanzas.domain.model.TipoCuenta
import mx.ollin.finanzas.domain.model.TipoMovimiento
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Base de pruebas para lo que necesita DAO de verdad.
 *
 * La base se abre con [Room.inMemoryDatabaseBuilder], que **no** pasa por
 * `OllinDatabase.obten`: se salta SQLCipher a proposito, porque es una
 * biblioteca nativa de Android y en la JVM no existe. Lo que se prueba aqui es
 * el SQL y la logica que cuelga de el, no el cifrado; ese solo se puede
 * verificar en dispositivo.
 *
 * Se fuerza una [Application] pelona en vez de `OllinApp`: la de verdad siembra
 * el catalogo y programa notificaciones al arrancar, y ninguna de las dos cosas
 * tiene que pasar por debajo de una prueba.
 *
 * El `sdk = [34]` es obligatorio: Robolectric 4.14.1 no trae imagen para la 36
 * del `targetSdk`, y sin fijarlo cada prueba muere al arrancar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
abstract class BaseEnMemoria {

    protected lateinit var db: OllinDatabase

    protected val cuentaDao get() = db.cuentaDao()
    protected val categoriaDao get() = db.categoriaDao()
    protected val movimientoDao get() = db.movimientoDao()
    protected val mapeoDao get() = db.mapeoDescripcionDao()
    protected val presupuestoDao get() = db.presupuestoDao()
    protected val compromisoDao get() = db.compromisoDao()

    protected lateinit var repositorio: FinanzasRepositorio

    @Before
    fun abreLaBase() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(app, OllinDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repositorio = FinanzasRepositorio(db, app.contentResolver)
    }

    @After
    fun cierraLaBase() {
        db.close()
    }

    // ------------------------------------------------------------- siembra

    protected suspend fun nuevaCuenta(
        nombre: String,
        tipo: TipoCuenta = TipoCuenta.DEBITO,
        medioPorDefecto: Medio = if (tipo == TipoCuenta.EFECTIVO) Medio.EFECTIVO else Medio.ELECTRONICO,
        soloElectronico: Boolean = tipo == TipoCuenta.CREDITO || tipo == TipoCuenta.CREDITO_MSI
    ): Long = cuentaDao.inserta(
        Cuenta(
            nombre = nombre,
            tipo = tipo,
            medioPorDefecto = medioPorDefecto,
            soloElectronico = soloElectronico
        )
    )

    protected suspend fun nuevaCategoria(
        nombre: String,
        tipo: TipoCategoria = TipoCategoria.GASTO
    ): Long = categoriaDao.inserta(Categoria(nombre = nombre, tipo = tipo))

    /**
     * Las claves foraneas estan activas, asi que [cuentaId] tiene que existir
     * de verdad: no se puede sembrar un movimiento colgando de la nada.
     */
    protected suspend fun nuevoMovimiento(
        cuentaId: Long,
        centavos: Long,
        tipo: TipoMovimiento,
        descripcion: String,
        categoriaId: Long? = null,
        fecha: LocalDate = LocalDate.of(2026, 1, 15),
        medio: Medio = Medio.ELECTRONICO,
        contraparte: Contraparte = if (tipo.esTransferencia || tipo.esInterno) {
            Contraparte.PROPIA
        } else {
            Contraparte.TERCERO
        },
        grupoTransferencia: String? = null
    ): Long = movimientoDao.inserta(
        Movimiento(
            fecha = fecha,
            importeCentavos = centavos,
            cuentaId = cuentaId,
            categoriaId = categoriaId,
            descripcion = descripcion,
            medio = medio,
            tipo = tipo,
            contraparte = contraparte,
            grupoTransferencia = grupoTransferencia
        )
    )

    protected suspend fun nuevoMapeo(clave: String, categoriaId: Long) =
        mapeoDao.guarda(MapeoDescripcion(clave = clave, categoriaId = categoriaId))
}
