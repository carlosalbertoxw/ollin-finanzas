package mx.ollin.finanzas.data.excel

import mx.ollin.finanzas.data.db.Categoria
import mx.ollin.finanzas.data.db.CategoriaDao
import mx.ollin.finanzas.data.db.Compromiso
import mx.ollin.finanzas.data.db.CompromisoDao
import mx.ollin.finanzas.data.db.Cuenta
import mx.ollin.finanzas.data.db.CuentaDao
import mx.ollin.finanzas.data.db.MapeoDescripcionDao
import mx.ollin.finanzas.data.db.Movimiento
import mx.ollin.finanzas.data.db.MovimientoDao
import mx.ollin.finanzas.data.db.Presupuesto
import mx.ollin.finanzas.data.db.PresupuestoDao
import mx.ollin.finanzas.data.db.Semilla
import mx.ollin.finanzas.domain.model.Contraparte
import mx.ollin.finanzas.domain.model.Medio
import mx.ollin.finanzas.domain.model.TipoCategoria
import mx.ollin.finanzas.domain.model.TipoCuenta
import mx.ollin.finanzas.domain.model.TipoMovimiento
import mx.ollin.finanzas.domain.model.normalizaClave
import java.io.InputStream
import java.time.LocalDate
import java.util.UUID
import kotlin.math.abs

enum class Severidad { INFO, AVISO, ERROR }

data class Diagnostico(
    val severidad: Severidad,
    val mensaje: String,
    val fila: Int? = null
)

data class OpcionesImportacion(
    /** Alinea el Tipo con el signo del importe cuando se contradicen. */
    val corregirTipoSegunSigno: Boolean = true,
    /** Recalcula Contraparte en vez de confiar en lo que traiga esa columna. */
    val derivarContraparte: Boolean = true,
    /** Empareja las dos patas de cada transferencia. */
    val emparejarTransferencias: Boolean = true,
    val crearCuentasFaltantes: Boolean = true,
    val autoCategorizar: Boolean = true,
    /**
     * Vacia los movimientos antes de importar y, ya sin ellos, se lleva tambien
     * las cuentas y categorias que quedaron sin uso y que el libro no nombra.
     * Si es falso, agrega y no borra nada.
     */
    val reemplazarTodo: Boolean = true,
    /** Lee tambien Diccionarios, Presupuesto y Compromisos cuando el libro las trae. */
    val importarOtrasHojas: Boolean = true
)

data class ResultadoImportacion(
    val filasLeidas: Int = 0,
    val importados: Int = 0,
    val omitidos: Int = 0,
    val cuentasCreadas: List<String> = emptyList(),
    val categoriasCreadas: List<String> = emptyList(),
    val sinCategoria: Int = 0,
    val tiposCorregidos: Int = 0,
    val contrapartesRecalculadas: Int = 0,
    val transferenciasEmparejadas: Int = 0,
    val transferenciasHuerfanas: Int = 0,
    /** Metas leidas de la pestaña Presupuesto. */
    val presupuestosImportados: Int = 0,
    /** Compromisos leidos de la pestaña Compromisos. */
    val compromisosImportados: Int = 0,
    /** Cuentas que al reemplazar quedaron sin uso y sin respaldo en el libro. */
    val cuentasEliminadas: List<String> = emptyList(),
    val categoriasEliminadas: Int = 0,
    val diagnosticos: List<Diagnostico> = emptyList()
) {
    val huboProblemas: Boolean get() = diagnosticos.any { it.severidad != Severidad.INFO }

    /**
     * Los diagnosticos como se muestran: un mensaje que se repite renglon por
     * renglon se cuenta una sola vez, con la lista de renglones a los que le
     * paso. Sin esto, un libro con doscientas filas incompletas devuelve
     * doscientas lineas identicas y ninguna se lee.
     */
    fun diagnosticosAgrupados(): List<DiagnosticoAgrupado> = diagnosticos
        .groupBy { it.severidad to it.mensaje }
        .map { (clave, iguales) ->
            DiagnosticoAgrupado(
                severidad = clave.first,
                mensaje = clave.second,
                filas = iguales.mapNotNull { it.fila }
            )
        }
        .sortedWith(compareBy({ ordenSeveridad(it.severidad) }, { -it.veces }))

    private fun ordenSeveridad(severidad: Severidad): Int = when (severidad) {
        Severidad.ERROR -> 0
        Severidad.AVISO -> 1
        Severidad.INFO -> 2
    }
}

/** Un mensaje de diagnostico con los renglones que lo provocaron. */
data class DiagnosticoAgrupado(
    val severidad: Severidad,
    val mensaje: String,
    val filas: List<Int>
) {
    val veces: Int get() = maxOf(filas.size, 1)
}

/**
 * Lee un libro de Excel y lo vuelca en la base de Ollin Finanzas.
 *
 * No se limita a copiar: al entrar corrige lo que en una hoja de calculo se
 * degrada solo — tipos que contradicen al signo del importe, transferencias a
 * medias y contrapartes que dejaron de mantenerse a mano.
 *
 * Registros es la hoja principal, pero no la unica: si el libro trae
 * Diccionarios, Presupuesto o Compromisos, tambien entran. Exportar el libro
 * completo y volver a importarlo devuelve el catalogo de cuentas y categorias,
 * las metas del mes y los pagos por venir, no solo los movimientos.
 */
class ImportadorExcel(
    private val cuentaDao: CuentaDao,
    private val categoriaDao: CategoriaDao,
    private val movimientoDao: MovimientoDao,
    private val mapeoDao: MapeoDescripcionDao,
    private val presupuestoDao: PresupuestoDao,
    private val compromisoDao: CompromisoDao
) {

    private companion object {
        val SINONIMOS: Map<String, List<String>> = mapOf(
            "fecha" to listOf("fecha", "date", "dia"),
            "cantidad" to listOf("cantidad", "importe", "monto", "amount"),
            "cuenta" to listOf("cuenta", "account"),
            "categoria" to listOf("categoria", "category", "rubro"),
            "descripcion" to listOf("descripcion", "concepto", "detalle", "description"),
            "medio" to listOf("medio", "forma de pago", "metodo"),
            "contraparte" to listOf("contraparte", "persona"),
            "tipo" to listOf("tipo", "type", "movimiento"),
            "nota" to listOf("nota", "notas", "comentario"),
            "compromiso" to listOf("compromiso")
        )
    }

    /** Fila intermedia: ya interpretada pero todavia sin ids de base. */
    private data class FilaCruda(
        val numeroFila: Int,
        val fecha: LocalDate,
        val importeCentavos: Long,
        val cuenta: String,
        val categoria: String?,
        val descripcion: String,
        val medio: Medio,
        val tipo: TipoMovimiento,
        val contraparteArchivo: Contraparte?,
        val nota: String?
    )

    suspend fun importa(
        entrada: InputStream,
        opciones: OpcionesImportacion = OpcionesImportacion()
    ): ResultadoImportacion {
        val libro = XlsxLector.lee(entrada)

        val diccionarios = if (opciones.importarOtrasHojas) {
            LectorCatalogos.leeDiccionarios(libro)
        } else {
            LectorCatalogos.Diccionarios()
        }

        val delLibro = ClavesDelLibro()
        val hoja = eligeHojaDeDatos(libro)
        var resultado = if (hoja != null) {
            procesa(hoja, opciones, diccionarios, delLibro)
        } else {
            // Un libro de puros catalogos tambien sirve: entra lo que traiga y
            // los movimientos se quedan como estaban. Vaciarlos aqui borraria
            // todo a cambio de nada.
            sinRegistros(diccionarios, opciones, delLibro)
        }

        if (opciones.importarOtrasHojas) {
            resultado = conPresupuestos(libro, resultado, opciones)
            resultado = conCompromisos(libro, resultado, opciones)
        }

        // Al final de todo: las metas y los compromisos tambien sostienen
        // catalogo, y barrer antes de leerlos se llevaria lo que ellos usan.
        if (hoja != null && opciones.reemplazarTodo) {
            resultado = purgaCatalogoSinUso(resultado, delLibro)
        }
        return if (hoja == null) resultado.conAvisoDeHojaFaltante() else resultado
    }

    /**
     * Nombres de cuenta y categoria que el libro menciona, ya normalizados.
     *
     * Al reemplazar, lo que no aparece aqui ni sostiene ningun dato es catalogo
     * que sobro de antes —tipicamente las cuentas de ejemplo que sembro la app
     * en el primer arranque— y estorba en cada desplegable.
     */
    private class ClavesDelLibro {
        val cuentas = HashSet<String>()
        val categorias = HashSet<String>()

        fun anotaCuenta(nombre: String) { cuentas += nombre.normalizaClave() }
        fun anotaCategoria(nombre: String) { categorias += nombre.normalizaClave() }
    }

    /**
     * Sin hoja de movimientos hay dos desenlaces distintos: si el libro traia
     * catalogos, metas o compromisos, la importacion sirvio y solo hay que
     * decir que los movimientos no se tocaron; si no traia nada, es un error.
     */
    private fun ResultadoImportacion.conAvisoDeHojaFaltante(): ResultadoImportacion {
        val entroAlgo = cuentasCreadas.isNotEmpty() || categoriasCreadas.isNotEmpty() ||
            presupuestosImportados > 0 || compromisosImportados > 0
        val aviso = if (entroAlgo) {
            Diagnostico(
                Severidad.AVISO,
                "El libro no trae una hoja de movimientos (Fecha, Cantidad y Cuenta). " +
                    "Entraron solo las otras pestañas; tus movimientos actuales quedaron como estaban."
            )
        } else {
            Diagnostico(Severidad.ERROR, "No encontre ninguna hoja con columnas de Fecha, Cantidad y Cuenta.")
        }
        return copy(diagnosticos = diagnosticos + aviso)
    }

    /** La hoja buena es la que trae, al menos, Fecha + Cantidad + Cuenta. */
    private fun eligeHojaDeDatos(libro: LibroLeido): HojaLeida? {
        val preferida = libro.hoja("Registros")
        if (preferida != null && mapaColumnas(preferida).esUtilizable()) return preferida
        return libro.hojas.firstOrNull { mapaColumnas(it).esUtilizable() }
    }

    private class MapaColumnas(private val indices: Map<String, Int>) {
        operator fun get(clave: String): Int? = indices[clave]
        fun esUtilizable(): Boolean =
            indices.containsKey("fecha") && indices.containsKey("cantidad") && indices.containsKey("cuenta")
    }

    private fun mapaColumnas(hoja: HojaLeida): MapaColumnas {
        val encabezado = hoja.filas.firstOrNull() ?: return MapaColumnas(emptyMap())
        val indices = mutableMapOf<String, Int>()
        encabezado.forEachIndexed { i, celda ->
            val texto = celda.comoTexto()?.normalizaClave() ?: return@forEachIndexed
            SINONIMOS.forEach { (clave, alias) ->
                if (!indices.containsKey(clave) && alias.any { it == texto }) indices[clave] = i
            }
        }
        return MapaColumnas(indices)
    }

    private suspend fun procesa(
        hoja: HojaLeida,
        opciones: OpcionesImportacion,
        diccionarios: LectorCatalogos.Diccionarios,
        delLibro: ClavesDelLibro
    ): ResultadoImportacion {
        val columnas = mapaColumnas(hoja)
        val diagnosticos = mutableListOf<Diagnostico>()
        val crudas = mutableListOf<FilaCruda>()
        var omitidos = 0

        hoja.filas.drop(1).forEachIndexed { indice, fila ->
            val numeroFila = indice + 2
            if (fila.all { it.estaVacia }) return@forEachIndexed

            val fecha = columnas["fecha"]?.let { fila.getOrNull(it) }?.let(::leeFecha)
            val importe = columnas["cantidad"]?.let { fila.getOrNull(it) }?.let(::leeImporte)
            val cuenta = columnas["cuenta"]?.let { fila.getOrNull(it) }?.comoTexto()?.trim()

            if (fecha == null || importe == null || cuenta.isNullOrBlank()) {
                omitidos++
                diagnosticos += Diagnostico(
                    Severidad.AVISO,
                    "Renglon incompleto (falta fecha, cantidad o cuenta). Se omitio.",
                    numeroFila
                )
                return@forEachIndexed
            }

            val descripcion = columnas["descripcion"]?.let { fila.getOrNull(it) }?.comoTexto()?.trim().orEmpty()
            val tipo = columnas["tipo"]?.let { fila.getOrNull(it) }?.comoTexto()
                ?.let(TipoMovimiento::desdeEtiqueta)
                ?: infiereTipo(importe, descripcion)

            crudas += FilaCruda(
                numeroFila = numeroFila,
                fecha = fecha,
                importeCentavos = importe,
                cuenta = cuenta,
                categoria = columnas["categoria"]?.let { fila.getOrNull(it) }?.comoTexto()?.trim()?.ifBlank { null },
                descripcion = descripcion.ifBlank { "(sin descripcion)" },
                medio = columnas["medio"]?.let { fila.getOrNull(it) }?.comoTexto()
                    ?.let(Medio::desdeEtiqueta) ?: Medio.ELECTRONICO,
                tipo = tipo,
                contraparteArchivo = columnas["contraparte"]?.let { fila.getOrNull(it) }
                    ?.let(::leeContraparte),
                nota = columnas["nota"]?.let { fila.getOrNull(it) }?.comoTexto()?.trim()?.ifBlank { null }
            )
        }

        if (crudas.isEmpty()) {
            return ResultadoImportacion(
                filasLeidas = hoja.filas.size - 1,
                omitidos = omitidos,
                diagnosticos = diagnosticos + Diagnostico(Severidad.ERROR, "No hubo ningun renglon aprovechable.")
            )
        }

        // ---- 1. Alinear Tipo con el signo del importe -----------------------
        var tiposCorregidos = 0
        val corregidas = crudas.map { fila ->
            if (!opciones.corregirTipoSegunSigno) return@map fila
            val esperado = fila.tipo.signoEsperado
            if (esperado == 0 || fila.importeCentavos == 0L) return@map fila
            val real = if (fila.importeCentavos > 0) 1 else -1
            if (real == esperado) return@map fila

            val nuevo = when (fila.tipo) {
                TipoMovimiento.TRANSFERENCIA_ENTRADA -> TipoMovimiento.TRANSFERENCIA_SALIDA
                TipoMovimiento.TRANSFERENCIA_SALIDA -> TipoMovimiento.TRANSFERENCIA_ENTRADA
                TipoMovimiento.ENTRADA -> TipoMovimiento.SALIDA
                TipoMovimiento.SALIDA -> TipoMovimiento.ENTRADA
                // Los internos aceptan cualquier signo: revaluar sube, depreciar baja.
                TipoMovimiento.BALANCE_INICIAL, TipoMovimiento.AJUSTE_VALOR -> fila.tipo
            }
            tiposCorregidos++
            diagnosticos += Diagnostico(
                Severidad.AVISO,
                "El tipo decia \"${fila.tipo.etiqueta}\" pero el importe es ${if (real > 0) "positivo" else "negativo"}. " +
                    "Se corrigio a \"${nuevo.etiqueta}\"; el importe no se toco.",
                fila.numeroFila
            )
            fila.copy(tipo = nuevo)
        }

        // ---- 2. Emparejar transferencias ------------------------------------
        val grupos = HashMap<Int, String>()   // numeroFila -> uuid
        var emparejadas = 0
        var huerfanas = 0
        if (opciones.emparejarTransferencias) {
            val salidas = corregidas.filter { it.tipo == TipoMovimiento.TRANSFERENCIA_SALIDA }.toMutableList()
            val entradas = corregidas.filter { it.tipo == TipoMovimiento.TRANSFERENCIA_ENTRADA }.toMutableList()

            // Primero la coincidencia estricta, luego se afloja la descripcion.
            listOf(true, false).forEach { exigeDescripcion ->
                val iterador = salidas.iterator()
                while (iterador.hasNext()) {
                    val salida = iterador.next()
                    val pareja = entradas.firstOrNull { entrada ->
                        entrada.fecha == salida.fecha &&
                            abs(entrada.importeCentavos) == abs(salida.importeCentavos) &&
                            (!exigeDescripcion || entrada.descripcion == salida.descripcion)
                    } ?: continue
                    val uuid = UUID.randomUUID().toString()
                    grupos[salida.numeroFila] = uuid
                    grupos[pareja.numeroFila] = uuid
                    entradas.remove(pareja)
                    iterador.remove()
                    emparejadas++
                }
            }
            huerfanas = salidas.size + entradas.size
            if (huerfanas > 0) {
                diagnosticos += Diagnostico(
                    Severidad.AVISO,
                    "Quedaron $huerfanas patas de transferencia sin pareja. " +
                        "Suelen ser movimientos que salen de una cuenta que no esta registrada."
                )
            }
        }

        // ---- 3. Resolver cuentas -------------------------------------------
        val cuentasCreadas = mutableListOf<String>()
        val indiceCuentas = cuentaDao.todas()
            .associateBy { it.nombre.normalizaClave() }
            .toMutableMap()

        // Diccionarios va primero: ahi la naturaleza de la cuenta viene
        // declarada, y crearla desde el nombre del movimiento solo la adivina.
        // Ademas entran las cuentas que aun no tienen ningun movimiento.
        diccionarios.cuentas.forEach { declarada ->
            delLibro.anotaCuenta(declarada.nombre)
            aseguraCuenta(
                nombre = declarada.nombre,
                tipoDeclarado = declarada.tipo,
                indice = indiceCuentas,
                creadas = cuentasCreadas,
                diagnosticos = diagnosticos,
                opciones = opciones,
                reportaFaltante = false
            )
        }

        corregidas.map { it.cuenta }.distinct().forEach { nombre ->
            delLibro.anotaCuenta(nombre)
            aseguraCuenta(nombre, null, indiceCuentas, cuentasCreadas, diagnosticos, opciones)
        }

        // ---- 4. Resolver categorias ----------------------------------------
        val categoriasCreadas = mutableListOf<String>()
        val indiceCategorias = categoriaDao.todas()
            .associateBy { it.nombre.normalizaClave() }
            .toMutableMap()
        corregidas.mapNotNull { it.categoria }.forEach(delLibro::anotaCategoria)
        aplicaCategoriasDeDiccionario(
            diccionarios.categorias, corregidas, indiceCategorias, categoriasCreadas, diagnosticos, delLibro
        )
        val mapeo = mapeoDao.todos().associate { it.clave to it.categoriaId }
        var sinCategoria = 0

        suspend fun resuelveCategoria(fila: FilaCruda): Long? {
            if (fila.tipo.esInterno) return null
            if (fila.descripcion.normalizaClave() in Semilla.DESCRIPCIONES_SIN_CATEGORIA) return null

            // a) la columna Categoria manda si viene
            fila.categoria?.let { nombre ->
                indiceCategorias[nombre.normalizaClave()]?.let { return it.id }
                if (!opciones.autoCategorizar) return null
                val tipoCat = tipoCategoriaPara(fila.tipo)
                val id = categoriaDao.inserta(Categoria(nombre = nombre, tipo = tipoCat, orden = 999))
                indiceCategorias[nombre.normalizaClave()] = Categoria(id = id, nombre = nombre, tipo = tipoCat)
                categoriasCreadas += nombre
                return id
            }

            if (!opciones.autoCategorizar) { sinCategoria++; return null }

            // b) mapeo por descripcion, primero la variante especifica por tipo
            mapeo[Semilla.claveAlias(fila.descripcion, fila.tipo)]?.let { return it }
            mapeo[fila.descripcion.normalizaClave()]?.let { return it }

            // c) coincidencia directa con una categoria existente
            indiceCategorias[fila.descripcion.normalizaClave()]?.let { return it.id }

            sinCategoria++
            return null
        }

        // ---- 5. Insertar -----------------------------------------------------
        if (opciones.reemplazarTodo) movimientoDao.eliminaTodos()

        var contrapartesRecalculadas = 0
        val aInsertar = mutableListOf<Movimiento>()

        for (fila in corregidas) {
            val cuenta = indiceCuentas[fila.cuenta.normalizaClave()]
            if (cuenta == null) { omitidos++; continue }

            val grupo = grupos[fila.numeroFila]
            val derivada = derivaContraparte(fila.tipo)
            val contraparte = if (opciones.derivarContraparte) {
                if (fila.contraparteArchivo != null && fila.contraparteArchivo != derivada) {
                    contrapartesRecalculadas++
                }
                derivada
            } else {
                fila.contraparteArchivo ?: derivada
            }

            aInsertar += Movimiento(
                fecha = fila.fecha,
                importeCentavos = fila.importeCentavos,
                cuentaId = cuenta.id,
                categoriaId = resuelveCategoria(fila),
                descripcion = fila.descripcion,
                medio = fila.medio,
                tipo = fila.tipo,
                contraparte = contraparte,
                grupoTransferencia = grupo,
                nota = fila.nota
            )
        }

        movimientoDao.insertaTodos(aInsertar)

        if (contrapartesRecalculadas > 0) {
            diagnosticos += Diagnostico(
                Severidad.INFO,
                "Recalcule la contraparte de $contrapartesRecalculadas movimientos: el archivo los marcaba " +
                    "como si intervinera un tercero, pero son traspasos entre cuentas tuyas."
            )
        }
        if (sinCategoria > 0) {
            diagnosticos += Diagnostico(
                Severidad.AVISO,
                "$sinCategoria movimientos quedaron sin categoria. Puedes asignarlas en lote desde la lista."
            )
        }

        return ResultadoImportacion(
            filasLeidas = hoja.filas.size - 1,
            importados = aInsertar.size,
            omitidos = omitidos,
            cuentasCreadas = cuentasCreadas,
            categoriasCreadas = categoriasCreadas,
            sinCategoria = sinCategoria,
            tiposCorregidos = tiposCorregidos,
            contrapartesRecalculadas = contrapartesRecalculadas,
            transferenciasEmparejadas = emparejadas,
            transferenciasHuerfanas = huerfanas,
            diagnosticos = diagnosticos
        )
    }

    // -------------------------------------------------- catalogos del libro

    /**
     * Crea la cuenta si no existe. [tipoDeclarado] gana sobre la inferencia por
     * el nombre: cuando el libro dice que "Terreno" es un activo, no hace falta
     * adivinarlo.
     */
    private suspend fun aseguraCuenta(
        nombre: String,
        tipoDeclarado: TipoCuenta?,
        indice: MutableMap<String, Cuenta>,
        creadas: MutableList<String>,
        diagnosticos: MutableList<Diagnostico>,
        opciones: OpcionesImportacion,
        reportaFaltante: Boolean = true
    ) {
        val clave = nombre.normalizaClave()
        if (indice.containsKey(clave)) return
        if (!opciones.crearCuentasFaltantes) {
            if (reportaFaltante) {
                diagnosticos += Diagnostico(Severidad.ERROR, "La cuenta \"$nombre\" no existe en Ollin Finanzas.")
            }
            return
        }
        val tipo = tipoDeclarado ?: infiereTipoCuenta(nombre)
        val id = cuentaDao.inserta(
            Cuenta(
                nombre = nombre,
                tipo = tipo,
                medioPorDefecto = Semilla.medioSugerido(tipo),
                soloElectronico = infiereSoloElectronico(nombre, tipo),
                orden = indice.size
            )
        )
        indice[clave] = Cuenta(id = id, nombre = nombre, tipo = tipo)
        creadas += nombre
    }

    /**
     * Vuelca las categorias de Diccionarios, con su grupo como padre.
     *
     * El tipo de categoria no viaja en esa hoja, asi que se deduce de como se
     * usa en Registros: lo que solo aparece en entradas es ingreso y el resto,
     * gasto. Una categoria de patrimonio vuelve como gasto — la hoja no guarda
     * esa distincion — y por eso se avisa cuando se crea alguna.
     */
    private suspend fun aplicaCategoriasDeDiccionario(
        declaradas: List<LectorCatalogos.CategoriaDeclarada>,
        crudas: List<FilaCruda>,
        indice: MutableMap<String, Categoria>,
        creadas: MutableList<String>,
        diagnosticos: MutableList<Diagnostico>,
        delLibro: ClavesDelLibro
    ) {
        if (declaradas.isEmpty()) return
        val usos = crudas.filter { it.categoria != null }
            .groupBy({ it.categoria!!.normalizaClave() }, { it.tipo })

        val antes = creadas.size
        declaradas.forEach { declarada ->
            delLibro.anotaCategoria(declarada.nombre)
            declarada.grupo?.let(delLibro::anotaCategoria)
            val tipo = tipoSegunUso(usos[declarada.nombre.normalizaClave()])
            val padre = declarada.grupo?.let { aseguraCategoria(it, null, tipo, indice, creadas) }
            aseguraCategoria(declarada.nombre, padre?.id, tipo, indice, creadas)
        }

        if (creadas.size > antes) {
            diagnosticos += Diagnostico(
                Severidad.INFO,
                "Cree ${creadas.size - antes} categorias que venian en la pestaña Diccionarios. " +
                    "La hoja no guarda si una categoria es de patrimonio, asi que entraron como " +
                    "gasto o ingreso segun su uso; revisalas si alguna era compra de patrimonio."
            )
        }
    }

    private suspend fun aseguraCategoria(
        nombre: String,
        padreId: Long?,
        tipo: TipoCategoria,
        indice: MutableMap<String, Categoria>,
        creadas: MutableList<String>
    ): Categoria {
        val clave = nombre.normalizaClave()
        indice[clave]?.let { return it }
        val id = categoriaDao.inserta(
            Categoria(nombre = nombre, padreId = padreId, tipo = tipo, orden = 999)
        )
        val creada = Categoria(id = id, nombre = nombre, padreId = padreId, tipo = tipo)
        indice[clave] = creada
        creadas += nombre
        return creada
    }

    private fun tipoSegunUso(tipos: List<TipoMovimiento>?): TipoCategoria = when {
        tipos.isNullOrEmpty() -> TipoCategoria.GASTO
        tipos.all { it == TipoMovimiento.ENTRADA } -> TipoCategoria.INGRESO
        tipos.all { it.esTransferencia || it.esInterno } -> TipoCategoria.TRASPASO
        else -> TipoCategoria.GASTO
    }

    /**
     * Aplica solo los catalogos, para el libro que no trae hoja de movimientos.
     * Aqui no hay Registros de donde deducir el tipo de las categorias, asi que
     * entran como gasto salvo que ya existieran.
     */
    private suspend fun sinRegistros(
        diccionarios: LectorCatalogos.Diccionarios,
        opciones: OpcionesImportacion,
        delLibro: ClavesDelLibro
    ): ResultadoImportacion {
        if (diccionarios.vacio) return ResultadoImportacion()

        val diagnosticos = mutableListOf<Diagnostico>()
        val cuentasCreadas = mutableListOf<String>()
        val categoriasCreadas = mutableListOf<String>()

        val indiceCuentas = cuentaDao.todas()
            .associateBy { it.nombre.normalizaClave() }
            .toMutableMap()
        diccionarios.cuentas.forEach { declarada ->
            delLibro.anotaCuenta(declarada.nombre)
            aseguraCuenta(
                nombre = declarada.nombre,
                tipoDeclarado = declarada.tipo,
                indice = indiceCuentas,
                creadas = cuentasCreadas,
                diagnosticos = diagnosticos,
                opciones = opciones,
                reportaFaltante = false
            )
        }

        val indiceCategorias = categoriaDao.todas()
            .associateBy { it.nombre.normalizaClave() }
            .toMutableMap()
        aplicaCategoriasDeDiccionario(
            diccionarios.categorias, emptyList(), indiceCategorias, categoriasCreadas, diagnosticos, delLibro
        )

        return ResultadoImportacion(
            cuentasCreadas = cuentasCreadas,
            categoriasCreadas = categoriasCreadas,
            diagnosticos = diagnosticos
        )
    }

    // ------------------------------------------------- limpieza al reemplazar

    /**
     * Quita del catalogo lo que quedo huerfano al reemplazar.
     *
     * "Reemplazar todo" vaciaba los movimientos pero dejaba en pie las cuentas y
     * categorias que ya no sostenian nada —entre ellas las de ejemplo que siembra
     * la app la primera vez—, asi que el telefono terminaba con un catalogo que
     * no correspondia a ningun archivo y ensuciaba cada desplegable.
     *
     * Se borra poco y con pruebas: nada que tenga un movimiento, una meta o un
     * compromiso detras, nada que el libro nombre —una cuenta puede venir en
     * Diccionarios sin un solo movimiento y sigue siendo parte del catalogo— y
     * ningun padre que todavia agrupe a una categoria viva.
     */
    private suspend fun purgaCatalogoSinUso(
        base: ResultadoImportacion,
        delLibro: ClavesDelLibro
    ): ResultadoImportacion {
        val diagnosticos = base.diagnosticos.toMutableList()

        val cuentasUsadas = movimientoDao.cuentasConMovimientos().toSet() +
            compromisoDao.cuentasReferenciadas().toSet()
        val cuentasSobran = cuentaDao.todas().filter {
            it.id !in cuentasUsadas && it.nombre.normalizaClave() !in delLibro.cuentas
        }
        if (cuentasSobran.isNotEmpty()) cuentaDao.eliminaPorIds(cuentasSobran.map { it.id })

        val categoriasUsadas = movimientoDao.categoriasConMovimientos().toSet() +
            presupuestoDao.categoriasConMeta().toSet() +
            compromisoDao.categoriasReferenciadas().toSet()
        val todas = categoriaDao.todas()
        val candidatas = todas.filter {
            it.id !in categoriasUsadas && it.nombre.normalizaClave() !in delLibro.categorias
        }
        val idsCandidatas = candidatas.mapTo(HashSet()) { it.id }
        val padresDeVivas = todas.filterNot { it.id in idsCandidatas }.mapNotNullTo(HashSet()) { it.padreId }
        val categoriasSobran = candidatas.filterNot { it.id in padresDeVivas }

        // Vaciar el catalogo entero no seria reemplazarlo sino desmantelarlo: sin
        // una sola categoria la proxima captura no tiene donde clasificar, y la
        // siembra del arranque las repondria igual. Un libro compacto, que no
        // trae categorias, cae justo en este caso.
        val loBorraTodo = categoriasSobran.size == todas.size && todas.isNotEmpty()
        if (categoriasSobran.isNotEmpty() && !loBorraTodo) {
            categoriaDao.eliminaPorIds(categoriasSobran.map { it.id })
            mapeoDao.eliminaHuerfanos()
        }
        val categoriasEliminadas = if (loBorraTodo) 0 else categoriasSobran.size

        if (cuentasSobran.isNotEmpty() || categoriasEliminadas > 0) {
            diagnosticos += Diagnostico(
                Severidad.INFO,
                "Al reemplazar quite ${cuentasSobran.size} cuentas y $categoriasEliminadas categorias " +
                    "que quedaron sin un solo movimiento y que el libro no nombra" +
                    if (cuentasSobran.isEmpty()) "." else ": ${cuentasSobran.joinToString { it.nombre }}."
            )
        }
        if (loBorraTodo) {
            diagnosticos += Diagnostico(
                Severidad.INFO,
                "El libro no trae categorias, asi que las tuyas se quedaron como estaban: " +
                    "borrarlas dejaria la captura sin donde clasificar."
            )
        }

        return base.copy(
            cuentasEliminadas = cuentasSobran.map { it.nombre },
            categoriasEliminadas = categoriasEliminadas,
            diagnosticos = diagnosticos
        )
    }

    // --------------------------------------------------------- Presupuesto

    /**
     * Las metas se cuelgan de categorias que ya existen; no se inventa ninguna.
     * Una meta apunta a una categoria por nombre, y crear la categoria aqui
     * significaria adivinar su naturaleza sin un solo movimiento que la
     * respalde.
     */
    private suspend fun conPresupuestos(
        libro: LibroLeido,
        base: ResultadoImportacion,
        opciones: OpcionesImportacion
    ): ResultadoImportacion {
        val metas = LectorCatalogos.leePresupuesto(libro)
        if (metas.isEmpty()) return base

        val diagnosticos = base.diagnosticos.toMutableList()
        val indice = categoriaDao.todas().associateBy { it.nombre.normalizaClave() }
        val aGuardar = LinkedHashMap<Triple<Long, Int, Int>, Presupuesto>()
        var sinCategoria = 0
        var sinPeriodo = 0

        metas.forEach { meta ->
            val categoria = indice[meta.categoria.normalizaClave()]
            if (categoria == null) { sinCategoria++; return@forEach }
            val periodo = meta.periodo
            if (periodo == null) { sinPeriodo++; return@forEach }
            // La ultima gana: si la hoja repite categoria y mes, el renglon de
            // mas abajo es el que el usuario dejo al final.
            aGuardar[Triple(categoria.id, periodo.year, periodo.monthValue)] = Presupuesto(
                categoriaId = categoria.id,
                anio = periodo.year,
                mes = periodo.monthValue,
                montoCentavos = abs(meta.montoCentavos)
            )
        }

        if (aGuardar.isNotEmpty()) {
            // Reemplazar tambien vale aqui: dejar las metas viejas mezcladas con
            // las del libro haria que ningun mes cuadrara con lo que muestra la
            // hoja. Pero se vacia solo cuando hay con que reponerlas.
            if (opciones.reemplazarTodo) presupuestoDao.eliminaTodos()
            presupuestoDao.guardaTodos(aGuardar.values.toList())
            diagnosticos += Diagnostico(
                Severidad.INFO,
                "Importe ${aGuardar.size} metas de la pestaña Presupuesto."
            )
        }
        if (sinCategoria > 0) {
            diagnosticos += Diagnostico(
                Severidad.AVISO,
                "$sinCategoria metas de presupuesto apuntan a categorias que no existen en " +
                    "Ollin Finanzas. Crea la categoria y vuelve a importar, o captura la meta a mano."
            )
        }
        if (sinPeriodo > 0) {
            diagnosticos += Diagnostico(
                Severidad.AVISO,
                "$sinPeriodo metas de presupuesto no dicen a que mes pertenecen y se omitieron."
            )
        }

        return base.copy(presupuestosImportados = aGuardar.size, diagnosticos = diagnosticos)
    }

    // --------------------------------------------------------- Compromisos

    private suspend fun conCompromisos(
        libro: LibroLeido,
        base: ResultadoImportacion,
        opciones: OpcionesImportacion
    ): ResultadoImportacion {
        val leidos = LectorCatalogos.leeCompromisos(libro)
        if (leidos.isEmpty()) return base

        val diagnosticos = base.diagnosticos.toMutableList()
        val cuentas = cuentaDao.todas().associateBy { it.nombre.normalizaClave() }
        val categorias = categoriaDao.todas().associateBy { it.nombre.normalizaClave() }

        // Un compromiso no tiene clave natural en la base, asi que al agregar
        // sobre lo que ya hay el nombre hace de identidad: importar dos veces el
        // mismo libro no debe dejar la lista duplicada. Al reemplazar no hay con
        // que chocar —lo viejo se va— pero el nombre sigue cuidando que la
        // propia hoja no repita.
        val yaExisten = if (opciones.reemplazarTodo) {
            HashSet<String>()
        } else {
            compromisoDao.todos().mapTo(HashSet()) { it.nombre.normalizaClave() }
        }
        var repetidos = 0
        var sinCuenta = 0
        var sinCategoria = 0
        val aInsertar = mutableListOf<Compromiso>()

        leidos.forEach { leido ->
            if (!yaExisten.add(leido.nombre.normalizaClave())) { repetidos++; return@forEach }
            val cuentaId = leido.cuenta?.let { nombre ->
                cuentas[nombre.normalizaClave()]?.id.also { if (it == null) sinCuenta++ }
            }
            val categoriaId = leido.categoria?.let { nombre ->
                categorias[nombre.normalizaClave()]?.id.also { if (it == null) sinCategoria++ }
            }
            val restantes = leido.totalPagos?.let { it - leido.pagosRealizados }
            aInsertar += Compromiso(
                nombre = leido.nombre,
                cuentaId = cuentaId,
                categoriaId = categoriaId,
                montoCentavos = leido.montoCentavos,
                periodicidad = leido.periodicidad,
                fechaPrimerPago = leido.fechaPrimerPago,
                totalPagos = leido.totalPagos,
                pagosRealizados = leido.pagosRealizados,
                // Lo que ya termino de pagarse entra apagado: sigue en la lista
                // como historia, pero no vuelve a avisar.
                activo = restantes == null || restantes > 0,
                notas = leido.notas
            )
        }

        if (aInsertar.isNotEmpty()) {
            if (opciones.reemplazarTodo) compromisoDao.eliminaTodos()
            compromisoDao.insertaTodos(aInsertar)
            diagnosticos += Diagnostico(
                Severidad.INFO,
                "Importe ${aInsertar.size} compromisos de la pestaña Compromisos."
            )
        }
        if (repetidos > 0) {
            diagnosticos += Diagnostico(
                Severidad.AVISO,
                "$repetidos compromisos ya existian con el mismo nombre y no se duplicaron."
            )
        }
        if (sinCuenta > 0) {
            diagnosticos += Diagnostico(
                Severidad.AVISO,
                "$sinCuenta compromisos nombran una cuenta que no existe; quedaron sin cuenta asignada."
            )
        }
        if (sinCategoria > 0) {
            diagnosticos += Diagnostico(
                Severidad.AVISO,
                "$sinCategoria compromisos nombran una categoria que no existe; quedaron sin " +
                    "categoria. Crea la categoria y vuelve a importar, o asignala a mano."
            )
        }

        return base.copy(compromisosImportados = aInsertar.size, diagnosticos = diagnosticos)
    }

    // ------------------------------------------------------------- lectura

    private fun leeFecha(celda: CeldaLeida): LocalDate? = LectorCatalogos.fecha(celda)

    private fun leeImporte(celda: CeldaLeida): Long? = LectorCatalogos.centavos(celda)

    private fun leeContraparte(celda: CeldaLeida): Contraparte? {
        celda.numero?.let { return Contraparte.desdeCodigo(it.toInt()) }
        val texto = celda.texto?.normalizaClave() ?: return null
        return when {
            texto.startsWith("1") || texto.contains("propia") || texto.contains("mis cuentas") -> Contraparte.PROPIA
            texto.startsWith("2") || texto.contains("tercero") -> Contraparte.TERCERO
            else -> null
        }
    }

    // ------------------------------------------------------------ inferencia

    private fun infiereTipo(importeCentavos: Long, descripcion: String): TipoMovimiento {
        val clave = descripcion.normalizaClave()
        if (clave.contains("balance inicial") || clave.contains("saldo inicial")) {
            return TipoMovimiento.BALANCE_INICIAL
        }
        // Sin columna Tipo, una revaluacion se reconoce por como la escribiste.
        if (clave.contains("ajuste de valor") || clave.contains("revaluacion") ||
            clave.contains("depreciacion") || clave.contains("plusvalia")
        ) {
            return TipoMovimiento.AJUSTE_VALOR
        }
        val esTraspaso = clave.contains("transferencia") || clave.contains("traspaso") ||
            clave.contains("pago tarjeta") || clave.contains("pago de tarjeta")
        return when {
            esTraspaso && importeCentavos >= 0 -> TipoMovimiento.TRANSFERENCIA_ENTRADA
            esTraspaso -> TipoMovimiento.TRANSFERENCIA_SALIDA
            importeCentavos >= 0 -> TipoMovimiento.ENTRADA
            else -> TipoMovimiento.SALIDA
        }
    }

    private fun infiereTipoCuenta(nombre: String): TipoCuenta {
        val clave = nombre.normalizaClave()
        return when {
            clave.contains("msi") || clave.contains("meses sin") -> TipoCuenta.CREDITO_MSI

            // "Prestamo a Juan" es dinero tuyo que va a volver: una cuenta por
            // cobrar, no una deuda. Va antes del caso general, que si es deuda.
            // El espacio final evita que "Prestamo Afirme" caiga aqui.
            clave.contains("prestamo a ") || clave.contains("prestamo para ") ||
                clave.contains("por cobrar") -> TipoCuenta.ACTIVO

            clave.contains("tarjeta") || clave.contains("credito") ||
                clave.contains("prestamo") || clave.contains("hipoteca") ||
                clave.contains("financiamiento") -> TipoCuenta.CREDITO

            clave.contains("cartera") || clave.contains("efectivo") || clave.contains("caja") -> TipoCuenta.EFECTIVO
            clave.contains("terreno") || clave.contains("cripto") || clave.contains("inmueble") -> TipoCuenta.ACTIVO
            else -> TipoCuenta.DEBITO
        }
    }

    /**
     * Solo lo que parece tarjeta queda atado al medio electronico. Un prestamo
     * familiar comparte tipo con la tarjeta pero si recibe dinero en mano, y
     * atarlo haria que la revision marcara como error un dato correcto.
     */
    private fun infiereSoloElectronico(nombre: String, tipo: TipoCuenta): Boolean {
        if (!tipo.esDeuda) return false
        val clave = nombre.normalizaClave()
        return clave.contains("tarjeta") || clave.contains("msi") || clave.contains("meses sin")
    }

    private fun tipoCategoriaPara(tipo: TipoMovimiento): TipoCategoria = when (tipo) {
        TipoMovimiento.ENTRADA -> TipoCategoria.INGRESO
        TipoMovimiento.SALIDA -> TipoCategoria.GASTO
        else -> TipoCategoria.TRASPASO
    }

    /**
     * La contraparte se deduce del tipo, no se pregunta: un traspaso entre tus
     * cuentas es propio y un ingreso o gasto involucra a alguien mas.
     */
    private fun derivaContraparte(tipo: TipoMovimiento): Contraparte =
        // Un traspaso o un saldo de apertura mueve dinero tuyo de un lado a otro.
        // Una entrada o una salida, por definicion, cruza contra alguien mas.
        if (tipo.esTransferencia || tipo.esInterno) Contraparte.PROPIA
        else Contraparte.TERCERO
}
