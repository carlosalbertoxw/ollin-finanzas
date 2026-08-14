package mx.ollin.finanzas.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Nota sobre fechas: `fecha` se guarda como dia epoch, asi que
 * `fecha * 86400` con 'unixepoch' da la fecha real para strftime.
 */

@Dao
interface CuentaDao {

    @Query("SELECT * FROM cuenta WHERE archivada = 0 ORDER BY orden, nombre")
    fun observaActivas(): Flow<List<Cuenta>>

    @Query("SELECT * FROM cuenta ORDER BY archivada, orden, nombre")
    fun observaTodas(): Flow<List<Cuenta>>

    @Query("SELECT * FROM cuenta ORDER BY archivada, orden, nombre")
    suspend fun todas(): List<Cuenta>

    @Query("SELECT * FROM cuenta WHERE id = :id")
    suspend fun porId(id: Long): Cuenta?

    @Query("SELECT * FROM cuenta WHERE nombre = :nombre LIMIT 1")
    suspend fun porNombre(nombre: String): Cuenta?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun inserta(cuenta: Cuenta): Long

    @Update
    suspend fun actualiza(cuenta: Cuenta)

    @Delete
    suspend fun elimina(cuenta: Cuenta)

    @Query("DELETE FROM cuenta WHERE id IN (:ids)")
    suspend fun eliminaPorIds(ids: List<Long>)

    @Query(
        """
        SELECT c.id AS cuentaId,
               c.nombre AS nombre,
               c.tipo AS tipo,
               c.limiteCentavos AS limiteCentavos,
               c.incluirEnPatrimonio AS incluirEnPatrimonio,
               COALESCE(SUM(m.importeCentavos), 0) AS saldoCentavos,
               COUNT(m.id) AS movimientos
        FROM cuenta c
        LEFT JOIN movimiento m ON m.cuentaId = c.id
        GROUP BY c.id
        ORDER BY c.orden, c.nombre
        """
    )
    fun observaSaldos(): Flow<List<SaldoCuenta>>
}

@Dao
interface CategoriaDao {

    @Query("SELECT * FROM categoria WHERE archivada = 0 ORDER BY orden, nombre")
    fun observaActivas(): Flow<List<Categoria>>

    @Query("SELECT * FROM categoria ORDER BY archivada, orden, nombre")
    fun observaTodas(): Flow<List<Categoria>>

    @Query("SELECT * FROM categoria ORDER BY archivada, orden, nombre")
    suspend fun todas(): List<Categoria>

    @Query("SELECT * FROM categoria WHERE id = :id")
    suspend fun porId(id: Long): Categoria?

    @Query("SELECT * FROM categoria WHERE nombre = :nombre AND (padreId IS :padreId) LIMIT 1")
    suspend fun porNombre(nombre: String, padreId: Long?): Categoria?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun inserta(categoria: Categoria): Long

    @Update
    suspend fun actualiza(categoria: Categoria)

    @Delete
    suspend fun elimina(categoria: Categoria)

    @Query("DELETE FROM categoria WHERE id IN (:ids)")
    suspend fun eliminaPorIds(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM categoria")
    suspend fun cuenta(): Int
}

@Dao
interface MovimientoDao {

    @Transaction
    @Query(
        """
        SELECT m.*, c.nombre AS nombreCuenta, c.tipo AS tipoCuenta, cat.nombre AS nombreCategoria
        FROM movimiento m
        JOIN cuenta c ON c.id = m.cuentaId
        LEFT JOIN categoria cat ON cat.id = m.categoriaId
        WHERE (:cuentaId IS NULL OR m.cuentaId = :cuentaId)
          AND (:categoriaId IS NULL OR m.categoriaId = :categoriaId)
          AND (:desde IS NULL OR m.fecha >= :desde)
          AND (:hasta IS NULL OR m.fecha <= :hasta)
          AND (:incluyeTraspasos = 1 OR m.tipo NOT IN ('TRANSFERENCIA_ENTRADA','TRANSFERENCIA_SALIDA'))
          AND (:texto IS NULL OR m.descripcion LIKE '%' || :texto || '%' OR m.nota LIKE '%' || :texto || '%')
        ORDER BY m.fecha DESC, m.id DESC
        LIMIT :limite OFFSET :desplazamiento
        """
    )
    fun observaFiltrados(
        cuentaId: Long?,
        categoriaId: Long?,
        desde: Long?,
        hasta: Long?,
        incluyeTraspasos: Boolean,
        texto: String?,
        limite: Int,
        desplazamiento: Int
    ): Flow<List<MovimientoDetallado>>

    @Query("SELECT * FROM movimiento WHERE id = :id")
    suspend fun porId(id: Long): Movimiento?

    @Query("SELECT * FROM movimiento WHERE grupoTransferencia = :grupo")
    suspend fun porGrupo(grupo: String): List<Movimiento>

    /** Una cuenta deberia tener un solo saldo inicial; sirve para avisar antes de duplicarlo. */
    @Query("SELECT * FROM movimiento WHERE cuentaId = :cuentaId AND tipo = 'BALANCE_INICIAL' LIMIT 1")
    suspend fun balanceInicialDe(cuentaId: Long): Movimiento?

    @Transaction
    @Query(
        """
        SELECT m.*, c.nombre AS nombreCuenta, c.tipo AS tipoCuenta, cat.nombre AS nombreCategoria
        FROM movimiento m
        JOIN cuenta c ON c.id = m.cuentaId
        LEFT JOIN categoria cat ON cat.id = m.categoriaId
        WHERE m.id IN (:ids)
        ORDER BY m.fecha DESC, m.id DESC
        """
    )
    fun observaPorIds(ids: List<Long>): Flow<List<MovimientoDetallado>>

    @Query("SELECT * FROM movimiento ORDER BY fecha, id")
    suspend fun todos(): List<Movimiento>

    @Query("SELECT COUNT(*) FROM movimiento")
    suspend fun cuenta(): Int

    @Query("SELECT COUNT(*) FROM movimiento")
    fun observaConteo(): Flow<Int>

    /** Cuentas y categorias que hoy sostienen un movimiento. Dice que del catalogo esta en uso. */
    @Query("SELECT DISTINCT cuentaId FROM movimiento")
    suspend fun cuentasConMovimientos(): List<Long>

    @Query("SELECT DISTINCT categoriaId FROM movimiento WHERE categoriaId IS NOT NULL")
    suspend fun categoriasConMovimientos(): List<Long>

    @Query(
        """
        SELECT categoriaId AS categoriaId, COUNT(*) AS movimientos
        FROM movimiento
        WHERE categoriaId IS NOT NULL
        GROUP BY categoriaId
        """
    )
    fun observaUsoPorCategoria(): Flow<List<UsoCategoria>>

    @Insert
    suspend fun inserta(movimiento: Movimiento): Long

    @Insert
    suspend fun insertaTodos(movimientos: List<Movimiento>): List<Long>

    @Update
    suspend fun actualiza(movimiento: Movimiento)

    @Delete
    suspend fun elimina(movimiento: Movimiento)

    @Query("DELETE FROM movimiento WHERE grupoTransferencia = :grupo")
    suspend fun eliminaGrupo(grupo: String)

    @Query("DELETE FROM movimiento WHERE id IN (:ids)")
    suspend fun eliminaPorIds(ids: List<Long>)

    @Query("DELETE FROM movimiento")
    suspend fun eliminaTodos()

    // ---------- Analitica ----------

    @Query(
        """
        SELECT strftime('%Y-%m', m.fecha * 86400, 'unixepoch') AS periodo,
               COALESCE(SUM(CASE WHEN m.tipo = 'ENTRADA' THEN m.importeCentavos ELSE 0 END), 0) AS ingresosCentavos,
               COALESCE(SUM(CASE WHEN m.tipo = 'SALIDA' AND COALESCE(cat.tipo,'GASTO') <> 'PATRIMONIO'
                                 THEN m.importeCentavos ELSE 0 END), 0) AS gastoConsumoCentavos,
               COALESCE(SUM(CASE WHEN m.tipo = 'SALIDA' AND cat.tipo = 'PATRIMONIO'
                                 THEN m.importeCentavos ELSE 0 END), 0) AS compraPatrimonioCentavos
        FROM movimiento m
        LEFT JOIN categoria cat ON cat.id = m.categoriaId
        WHERE m.tipo IN ('ENTRADA','SALIDA')
        GROUP BY periodo
        ORDER BY periodo
        """
    )
    fun observaFlujoMensual(): Flow<List<FlujoMes>>

    @Query(
        """
        SELECT COALESCE(SUM(m.importeCentavos), 0)
        FROM movimiento m
        LEFT JOIN categoria cat ON cat.id = m.categoriaId
        WHERE m.categoriaId = :categoriaId
          AND m.tipo IN ('ENTRADA','SALIDA')
          AND strftime('%Y-%m', m.fecha * 86400, 'unixepoch') = :periodo
        """
    )
    suspend fun totalCategoriaEnPeriodo(categoriaId: Long, periodo: String): Long
}

@Dao
interface PresupuestoDao {

    @Query("SELECT * FROM presupuesto WHERE anio = :anio AND mes = :mes")
    fun observaDelMes(anio: Int, mes: Int): Flow<List<Presupuesto>>

    @Query("SELECT * FROM presupuesto WHERE anio = :anio AND mes = :mes")
    suspend fun delMes(anio: Int, mes: Int): List<Presupuesto>

    @Query("SELECT * FROM presupuesto ORDER BY anio, mes")
    suspend fun todos(): List<Presupuesto>

    @Query("SELECT DISTINCT categoriaId FROM presupuesto")
    suspend fun categoriasConMeta(): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guarda(presupuesto: Presupuesto): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardaTodos(presupuestos: List<Presupuesto>)

    @Query("DELETE FROM presupuesto WHERE categoriaId = :categoriaId AND anio = :anio AND mes = :mes")
    suspend fun elimina(categoriaId: Long, anio: Int, mes: Int)

    @Query("DELETE FROM presupuesto")
    suspend fun eliminaTodos()
}

@Dao
interface CompromisoDao {

    @Query("SELECT * FROM compromiso ORDER BY activo DESC, fechaPrimerPago")
    fun observaTodos(): Flow<List<Compromiso>>

    @Query("SELECT * FROM compromiso ORDER BY activo DESC, fechaPrimerPago")
    suspend fun todos(): List<Compromiso>

    @Query("SELECT * FROM compromiso WHERE id = :id")
    suspend fun porId(id: Long): Compromiso?

    @Query("SELECT DISTINCT cuentaId FROM compromiso WHERE cuentaId IS NOT NULL")
    suspend fun cuentasReferenciadas(): List<Long>

    @Query("SELECT DISTINCT categoriaId FROM compromiso WHERE categoriaId IS NOT NULL")
    suspend fun categoriasReferenciadas(): List<Long>

    @Insert
    suspend fun inserta(compromiso: Compromiso): Long

    @Insert
    suspend fun insertaTodos(compromisos: List<Compromiso>): List<Long>

    @Update
    suspend fun actualiza(compromiso: Compromiso)

    @Delete
    suspend fun elimina(compromiso: Compromiso)

    @Query("DELETE FROM compromiso")
    suspend fun eliminaTodos()
}

@Dao
interface MapeoDescripcionDao {

    @Query("SELECT * FROM mapeo_descripcion")
    suspend fun todos(): List<MapeoDescripcion>

    @Query("SELECT categoriaId FROM mapeo_descripcion WHERE clave = :clave LIMIT 1")
    suspend fun categoriaDe(clave: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guarda(mapeo: MapeoDescripcion)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun guardaTodos(mapeos: List<MapeoDescripcion>)

    @Query("SELECT COUNT(*) FROM mapeo_descripcion")
    suspend fun cuenta(): Int

    /**
     * Esta tabla no tiene clave foranea contra categoria: se llena por nombre y
     * sobrevive a que la categoria se renombre. El precio es que al borrar una
     * categoria el mapeo queda apuntando al vacio, y el siguiente movimiento que
     * caiga en esa clave se guardaria con una categoria que ya no existe. Se
     * limpia despues de cualquier borrado de catalogo.
     */
    @Query("DELETE FROM mapeo_descripcion WHERE categoriaId NOT IN (SELECT id FROM categoria)")
    suspend fun eliminaHuerfanos(): Int
}
