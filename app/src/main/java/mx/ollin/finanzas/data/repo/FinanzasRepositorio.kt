package mx.ollin.finanzas.data.repo

import android.content.ContentResolver
import android.net.Uri
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import mx.ollin.finanzas.data.db.Categoria
import mx.ollin.finanzas.data.db.Compromiso
import mx.ollin.finanzas.data.db.Cuenta
import mx.ollin.finanzas.data.db.MapeoDescripcion
import mx.ollin.finanzas.data.db.Movimiento
import mx.ollin.finanzas.data.db.MovimientoDetallado
import mx.ollin.finanzas.data.db.OllinDatabase
import mx.ollin.finanzas.data.db.Presupuesto
import mx.ollin.finanzas.data.db.SaldoCuenta
import mx.ollin.finanzas.data.db.UsoCategoria
import mx.ollin.finanzas.data.excel.DatosExportacion
import mx.ollin.finanzas.data.excel.EsquemaExportacion
import mx.ollin.finanzas.data.excel.ExportadorExcel
import mx.ollin.finanzas.data.excel.HojaExportable
import mx.ollin.finanzas.data.excel.ImportadorExcel
import mx.ollin.finanzas.data.excel.OpcionesImportacion
import mx.ollin.finanzas.data.excel.ResultadoImportacion
import mx.ollin.finanzas.domain.model.Contraparte
import mx.ollin.finanzas.domain.model.TipoMovimiento
import mx.ollin.finanzas.domain.model.normalizaClave
import java.io.OutputStream
import java.time.LocalDate
import java.util.UUID

/**
 * Punto unico de acceso a los datos. Concentra tambien las reglas que no deben
 * poder saltarse desde la interfaz: una transferencia siempre genera sus dos
 * patas y la contraparte siempre se deriva.
 */
class FinanzasRepositorio(
    private val db: OllinDatabase,
    private val resolver: ContentResolver
) {

    private val cuentas = db.cuentaDao()
    private val categorias = db.categoriaDao()
    private val movimientos = db.movimientoDao()
    private val presupuestos = db.presupuestoDao()
    private val compromisos = db.compromisoDao()
    private val mapeos = db.mapeoDescripcionDao()

    // ------------------------------------------------------------ consultas

    fun observaCuentas(): Flow<List<Cuenta>> = cuentas.observaActivas()
    fun observaTodasLasCuentas(): Flow<List<Cuenta>> = cuentas.observaTodas()
    fun observaSaldos(): Flow<List<SaldoCuenta>> = cuentas.observaSaldos()
    fun observaCategorias(): Flow<List<Categoria>> = categorias.observaActivas()
    fun observaTodasLasCategorias(): Flow<List<Categoria>> = categorias.observaTodas()
    fun observaUsoDeCategorias(): Flow<List<UsoCategoria>> = movimientos.observaUsoPorCategoria()
    fun observaCompromisos(): Flow<List<Compromiso>> = compromisos.observaTodos()
    fun observaFlujoMensual() = movimientos.observaFlujoMensual()
    fun observaConteoMovimientos(): Flow<Int> = movimientos.observaConteo()

    fun observaMovimientos(
        cuentaId: Long? = null,
        categoriaId: Long? = null,
        desde: LocalDate? = null,
        hasta: LocalDate? = null,
        incluyeTraspasos: Boolean = true,
        texto: String? = null,
        limite: Int = 300,
        desplazamiento: Int = 0
    ): Flow<List<MovimientoDetallado>> = movimientos.observaFiltrados(
        cuentaId = cuentaId,
        categoriaId = categoriaId,
        desde = desde?.toEpochDay(),
        hasta = hasta?.toEpochDay(),
        incluyeTraspasos = incluyeTraspasos,
        texto = texto?.ifBlank { null },
        limite = limite,
        desplazamiento = desplazamiento
    )

    /** Los movimientos exactos que senala un hallazgo de calidad. */
    fun observaMovimientosPorIds(ids: List<Long>): Flow<List<MovimientoDetallado>> =
        if (ids.isEmpty()) flowOf(emptyList()) else movimientos.observaPorIds(ids)

    fun observaPresupuestos(anio: Int, mes: Int): Flow<List<Presupuesto>> =
        presupuestos.observaDelMes(anio, mes)

    suspend fun cuenta(id: Long): Cuenta? = cuentas.porId(id)
    suspend fun categoria(id: Long): Categoria? = categorias.porId(id)
    suspend fun movimiento(id: Long): Movimiento? = movimientos.porId(id)
    /** Las dos patas de una transferencia, para poder editarla como una sola cosa. */
    suspend fun movimientosDeGrupo(grupo: String): List<Movimiento> = movimientos.porGrupo(grupo)
    suspend fun balanceInicialDe(cuentaId: Long): Movimiento? = movimientos.balanceInicialDe(cuentaId)
    suspend fun compromiso(id: Long): Compromiso? = compromisos.porId(id)
    suspend fun listaCuentas(): List<Cuenta> = cuentas.todas()
    suspend fun listaCategorias(): List<Categoria> = categorias.todas()
    suspend fun listaCompromisos(): List<Compromiso> = compromisos.todos()
    suspend fun totalCategoriaEnPeriodo(categoriaId: Long, periodo: String): Long =
        movimientos.totalCategoriaEnPeriodo(categoriaId, periodo)

    // -------------------------------------------------------------- escritura

    /**
     * Guarda un movimiento simple. La contraparte no se recibe: se deriva del
     * tipo, que es lo que evita que el campo se degrade con el tiempo.
     */
    suspend fun guardaMovimiento(movimiento: Movimiento): Long {
        val normalizado = movimiento.copy(
            contraparte = if (movimiento.tipo.esTransferencia || movimiento.tipo.esInterno)
                Contraparte.PROPIA else Contraparte.TERCERO,
            actualizadoEn = System.currentTimeMillis()
        )
        return if (normalizado.id == 0L) {
            val id = movimientos.inserta(normalizado)
            aprendeCategoria(normalizado)
            id
        } else {
            movimientos.actualiza(normalizado)
            aprendeCategoria(normalizado)
            normalizado.id
        }
    }

    /**
     * Una transferencia se captura una vez y produce las dos patas unidas por
     * el mismo grupo. Es lo que impide que quede media transferencia colgando.
     *
     * Al editar no se actualizan las patas: se borran y se vuelven a escribir
     * dentro de una sola transaccion. Asi el par siempre nace completo, incluso
     * cuando lo que habia era una pata suelta ([idsAReemplazar]) o un grupo con
     * mas renglones de los que debia.
     */
    suspend fun guardaTransferencia(
        fecha: LocalDate,
        importeCentavos: Long,
        cuentaOrigenId: Long,
        cuentaDestinoId: Long,
        descripcion: String,
        nota: String? = null,
        grupoExistente: String? = null,
        idsAReemplazar: List<Long> = emptyList(),
        categoriaId: Long? = null
    ) {
        require(importeCentavos > 0) { "El importe de una transferencia se captura en positivo" }
        require(cuentaOrigenId != cuentaDestinoId) { "El origen y el destino no pueden ser la misma cuenta" }

        val grupo = grupoExistente ?: UUID.randomUUID().toString()

        val origen = cuentas.porId(cuentaOrigenId) ?: error("Cuenta origen inexistente")
        val destino = cuentas.porId(cuentaDestinoId) ?: error("Cuenta destino inexistente")
        // Una compra de patrimonio conserva su categoria ("Inmuebles") aunque viaje
        // como transferencia; el traspaso generico es solo el valor por omision.
        val categoriaFinal = categoriaId ?: categorias.todas()
            .firstOrNull { it.nombre.normalizaClave() == "transferencia entre cuentas" }?.id

        db.withTransaction {
            grupoExistente?.let { movimientos.eliminaGrupo(it) }
            if (idsAReemplazar.isNotEmpty()) movimientos.eliminaPorIds(idsAReemplazar)
            movimientos.insertaTodos(
                listOf(
                    Movimiento(
                        fecha = fecha,
                        importeCentavos = -importeCentavos,
                        cuentaId = cuentaOrigenId,
                        categoriaId = categoriaFinal,
                        descripcion = descripcion,
                        medio = origen.medioPorDefecto,
                        tipo = TipoMovimiento.TRANSFERENCIA_SALIDA,
                        contraparte = Contraparte.PROPIA,
                        grupoTransferencia = grupo,
                        nota = nota
                    ),
                    Movimiento(
                        fecha = fecha,
                        importeCentavos = importeCentavos,
                        cuentaId = cuentaDestinoId,
                        categoriaId = categoriaFinal,
                        descripcion = descripcion,
                        medio = destino.medioPorDefecto,
                        tipo = TipoMovimiento.TRANSFERENCIA_ENTRADA,
                        contraparte = Contraparte.PROPIA,
                        grupoTransferencia = grupo,
                        nota = nota
                    )
                )
            )
        }
    }

    /** Borrar una pata borra la otra: media transferencia no es un estado valido. */
    suspend fun eliminaMovimiento(movimiento: Movimiento) {
        val grupo = movimiento.grupoTransferencia
        if (grupo != null) movimientos.eliminaGrupo(grupo) else movimientos.elimina(movimiento)
    }

    /** Recuerda la categoria que elegiste para esa descripcion y la reusa despues. */
    private suspend fun aprendeCategoria(movimiento: Movimiento) {
        val categoriaId = movimiento.categoriaId ?: return
        if (movimiento.tipo.esTransferencia) return
        mapeos.guarda(
            MapeoDescripcion(
                clave = movimiento.descripcion.normalizaClave(),
                categoriaId = categoriaId,
                generadoPorSistema = false
            )
        )
    }

    suspend fun guardaCuenta(cuenta: Cuenta): Long =
        if (cuenta.id == 0L) cuentas.inserta(cuenta) else { cuentas.actualiza(cuenta); cuenta.id }

    /** Sirve para avisar del nombre repetido antes de que el indice unico rebote el guardado. */
    suspend fun cuentaPorNombre(nombre: String): Cuenta? = cuentas.porNombre(nombre)

    suspend fun eliminaCuenta(cuenta: Cuenta) = cuentas.elimina(cuenta)

    suspend fun guardaCategoria(categoria: Categoria): Long =
        if (categoria.id == 0L) categorias.inserta(categoria) else { categorias.actualiza(categoria); categoria.id }

    suspend fun eliminaCategoria(categoria: Categoria) = categorias.elimina(categoria)

    suspend fun guardaPresupuesto(presupuesto: Presupuesto) = presupuestos.guarda(presupuesto)
    suspend fun eliminaPresupuesto(categoriaId: Long, anio: Int, mes: Int) =
        presupuestos.elimina(categoriaId, anio, mes)

    /** Copia las metas de un mes al siguiente, que es como se arma un presupuesto real. */
    suspend fun copiaPresupuesto(desdeAnio: Int, desdeMes: Int, haciaAnio: Int, haciaMes: Int): Int {
        val origen = presupuestos.delMes(desdeAnio, desdeMes)
        if (origen.isEmpty()) return 0
        presupuestos.guardaTodos(
            origen.map { Presupuesto(categoriaId = it.categoriaId, anio = haciaAnio, mes = haciaMes, montoCentavos = it.montoCentavos) }
        )
        return origen.size
    }

    suspend fun guardaCompromiso(compromiso: Compromiso): Long =
        if (compromiso.id == 0L) compromisos.inserta(compromiso)
        else { compromisos.actualiza(compromiso); compromiso.id }

    suspend fun eliminaCompromiso(compromiso: Compromiso) = compromisos.elimina(compromiso)

    /**
     * Da por pagada una mensualidad y apaga el plan si con esa se acabo. Se
     * llama despues de guardar el movimiento, no antes: el contador avanza
     * cuando el gasto existe de verdad, no cuando alguien abrio la captura.
     */
    suspend fun avanzaCompromiso(compromisoId: Long) {
        val c = compromisos.porId(compromisoId) ?: return
        val pagados = c.pagosRealizados + 1
        val terminado = c.totalPagos?.let { pagados >= it } ?: false
        compromisos.actualiza(c.copy(pagosRealizados = pagados, activo = !terminado))
    }

    // ---------------------------------------------------------- import/export

    suspend fun importa(uri: Uri, opciones: OpcionesImportacion): ResultadoImportacion =
        withContext(Dispatchers.IO) {
            val importador = ImportadorExcel(
                cuentas, categorias, movimientos, mapeos, presupuestos, compromisos
            )
            resolver.openInputStream(uri)?.use { importador.importa(it, opciones) }
                ?: error("No se pudo abrir el archivo seleccionado")
        }

    suspend fun exporta(
        uri: Uri,
        esquema: EsquemaExportacion,
        hojas: Set<HojaExportable>
    ) = withContext(Dispatchers.IO) {
        val datos = DatosExportacion(
            cuentas = cuentas.todas().filter { !it.archivada },
            categorias = categorias.todas(),
            movimientos = movimientos.todos(),
            presupuestos = presupuestos.todos(),
            compromisos = compromisos.todos()
        )
        abreParaEscribir(uri).use { salida ->
            ExportadorExcel(datos, esquema, hojas).escribeEn(salida)
        }
    }

    /**
     * Abre el destino elegido en el selector del sistema.
     *
     * Se pide "wt" (escribir truncando) porque al sobrescribir un archivo mas
     * grande, sin truncar quedaria la cola del viejo pegada al final y el .xlsx
     * saldria corrupto. Pero no todos los proveedores de documentos soportan
     * ese modo: varios gestores de archivos y los de nube revientan con "wt" y
     * solo aceptan "w". Un archivo recien creado por el selector nace vacio,
     * asi que "w" basta y es preferible a no poder exportar.
     */
    private fun abreParaEscribir(uri: Uri): OutputStream {
        runCatching { resolver.openOutputStream(uri, "wt") }
            .getOrNull()
            ?.let { return it }
        return resolver.openOutputStream(uri, "w")
            ?: error("No se pudo escribir en el archivo seleccionado")
    }

    suspend fun datosParaExportar(): DatosExportacion = withContext(Dispatchers.IO) {
        DatosExportacion(
            cuentas = cuentas.todas().filter { !it.archivada },
            categorias = categorias.todas(),
            movimientos = movimientos.todos(),
            presupuestos = presupuestos.todos(),
            compromisos = compromisos.todos()
        )
    }
}
