package com.carlosalbertoxw.ollin.finanzas.data.excel

import androidx.room.withTransaction
import com.carlosalbertoxw.ollin.finanzas.data.db.Categoria
import com.carlosalbertoxw.ollin.finanzas.data.db.CategoriaDao
import com.carlosalbertoxw.ollin.finanzas.data.db.Compromiso
import com.carlosalbertoxw.ollin.finanzas.data.db.CompromisoDao
import com.carlosalbertoxw.ollin.finanzas.data.db.Cuenta
import com.carlosalbertoxw.ollin.finanzas.data.db.CuentaDao
import com.carlosalbertoxw.ollin.finanzas.data.db.MapeoDescripcionDao
import com.carlosalbertoxw.ollin.finanzas.data.db.Movimiento
import com.carlosalbertoxw.ollin.finanzas.data.db.MovimientoDao
import com.carlosalbertoxw.ollin.finanzas.data.db.OllinDatabase
import com.carlosalbertoxw.ollin.finanzas.data.db.Presupuesto
import com.carlosalbertoxw.ollin.finanzas.data.db.PresupuestoDao
import com.carlosalbertoxw.ollin.finanzas.data.db.Semilla
import com.carlosalbertoxw.ollin.finanzas.domain.model.Contraparte
import com.carlosalbertoxw.ollin.finanzas.domain.model.Medio
import com.carlosalbertoxw.ollin.finanzas.domain.model.TipoCategoria
import com.carlosalbertoxw.ollin.finanzas.domain.model.TipoCuenta
import com.carlosalbertoxw.ollin.finanzas.domain.model.TipoMovimiento
import com.carlosalbertoxw.ollin.finanzas.domain.model.normalizaClave
import java.io.InputStream
import java.time.LocalDate
import java.util.UUID
import kotlin.math.abs

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
 *
 * Recibe la base entera y no sus DAOs sueltos porque necesita abrir una
 * transaccion: importar reemplazando borra todo antes de escribir, y sin
 * atomicidad un fallo a medio camino deja el libro vacio. Ver [importa].
 */
class ImportadorExcel(private val db: OllinDatabase) {

    private val cuentaDao: CuentaDao = db.cuentaDao()
    private val categoriaDao: CategoriaDao = db.categoriaDao()
    private val movimientoDao: MovimientoDao = db.movimientoDao()
    private val mapeoDao: MapeoDescripcionDao = db.mapeoDescripcionDao()
    private val presupuestoDao: PresupuestoDao = db.presupuestoDao()
    private val compromisoDao: CompromisoDao = db.compromisoDao()

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

    /**
     * La lectura del archivo va fuera de la transaccion y toda la escritura
     * dentro.
     *
     * Con `reemplazarTodo` la importacion borra los movimientos antes de
     * insertar los nuevos, y entre una cosa y otra todavia crea categorias.
     * Suelto, cualquier tropiezo en ese tramo —disco lleno, una restriccion, o
     * que Android mate el proceso por memoria— dejaba el libro sin nada y sin
     * reemplazo: la peor perdida posible en una app cuyo valor es el registro.
     * Dentro de la transaccion, o entra el libro completo o no se toca nada.
     *
     * Parsear fuera tambien importa: es la parte que puede quedarse sin memoria
     * con un archivo grande, y asi revienta antes de haber borrado nada.
     */
    suspend fun importa(
        entrada: InputStream,
        opciones: OpcionesImportacion = OpcionesImportacion()
    ): ResultadoImportacion {
        val libro = XlsxLector.lee(entrada)

        return db.withTransaction {
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
            if (hoja == null) resultado.conAvisoDeHojaFaltante() else resultado
        }
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
            Diagnostico(
                Severidad.ERROR,
                "No encontre ninguna hoja con columnas de Fecha, Cantidad y Cuenta."
            )
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
            indices.containsKey(
                "fecha"
            ) && indices.containsKey("cantidad") && indices.containsKey("cuenta")
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

    /**
     * La hoja de movimientos, de punta a punta. Cada etapa es una funcion con
     * nombre y van en este orden porque cada una se apoya en la anterior: el
     * emparejado necesita los tipos ya corregidos, y la insercion, cuentas y
     * categorias resueltas. Los diagnosticos se acumulan en el orden en que
     * aparecen.
     */
    private suspend fun procesa(
        hoja: HojaLeida,
        opciones: OpcionesImportacion,
        diccionarios: LectorCatalogos.Diccionarios,
        delLibro: ClavesDelLibro
    ): ResultadoImportacion {
        val diagnosticos = mutableListOf<Diagnostico>()
        val lectura = leeRenglones(hoja, diagnosticos)

        if (lectura.filas.isEmpty()) {
            return ResultadoImportacion(
                filasLeidas = hoja.filas.size - 1,
                omitidos = lectura.omitidos,
                diagnosticos = diagnosticos + Diagnostico(
                    Severidad.ERROR,
                    "No hubo ningun renglon aprovechable."
                )
            )
        }

        val alineado = alineaTipos(lectura.filas, opciones, diagnosticos)
        val filas = alineado.filas
        val pares = emparejaTransferencias(filas, opciones, diagnosticos)
        val cuentas = resuelveCuentas(filas, diccionarios, opciones, diagnosticos, delLibro)
        val categorias = preparaCategorias(filas, diccionarios, opciones, diagnosticos, delLibro)
        val insercion = inserta(filas, cuentas.indice, pares.grupos, categorias, opciones)

        if (insercion.contrapartesRecalculadas > 0) {
            diagnosticos += Diagnostico(
                Severidad.INFO,
                "Recalcule la contraparte de ${insercion.contrapartesRecalculadas} movimientos: " +
                    "el archivo los marcaba como si intervinera un tercero, pero son traspasos " +
                    "entre cuentas tuyas."
            )
        }
        if (categorias.sinCategoria > 0) {
            diagnosticos += Diagnostico(
                Severidad.AVISO,
                "${categorias.sinCategoria} movimientos quedaron sin categoria. " +
                    "Puedes asignarlas en lote desde la lista."
            )
        }

        return ResultadoImportacion(
            filasLeidas = hoja.filas.size - 1,
            importados = insercion.importados,
            omitidos = lectura.omitidos + insercion.omitidos,
            cuentasCreadas = cuentas.creadas,
            categoriasCreadas = categorias.creadas,
            sinCategoria = categorias.sinCategoria,
            tiposCorregidos = alineado.corregidos,
            contrapartesRecalculadas = insercion.contrapartesRecalculadas,
            transferenciasEmparejadas = pares.emparejadas,
            transferenciasHuerfanas = pares.huerfanas,
            diagnosticos = diagnosticos
        )
    }

    // ---------------------------------------------- etapas de la hoja

    private class Lectura(val filas: List<FilaCruda>, val omitidos: Int)

    /**
     * Interpreta cada renglon con las columnas que se reconocieron. Uno sin
     * fecha, cantidad o cuenta no se puede ubicar en el libro: se omite y se
     * dice en que renglon estaba.
     */
    private fun leeRenglones(hoja: HojaLeida, diagnosticos: MutableList<Diagnostico>): Lectura {
        val columnas = mapaColumnas(hoja)
        val crudas = mutableListOf<FilaCruda>()
        var omitidos = 0

        hoja.filas.drop(1).forEachIndexed { indice, fila ->
            val numeroFila = indice + 2
            if (fila.all { it.estaVacia }) return@forEachIndexed

            fun celda(clave: String): CeldaLeida? = columnas[clave]?.let { fila.getOrNull(it) }

            val fecha = celda("fecha")?.let(::leeFecha)
            val importe = celda("cantidad")?.let(::leeImporte)
            val cuenta = celda("cuenta")?.comoTexto()?.trim()

            if (fecha == null || importe == null || cuenta.isNullOrBlank()) {
                omitidos++
                diagnosticos += Diagnostico(
                    Severidad.AVISO,
                    "Renglon incompleto (falta fecha, cantidad o cuenta). Se omitio.",
                    numeroFila
                )
                return@forEachIndexed
            }

            val descripcion = celda("descripcion")?.comoTexto()?.trim().orEmpty()
            val tipo = celda("tipo")?.comoTexto()?.let(TipoMovimiento::desdeEtiqueta)
                ?: infiereTipo(importe, descripcion)

            crudas += FilaCruda(
                numeroFila = numeroFila,
                fecha = fecha,
                importeCentavos = importe,
                cuenta = cuenta,
                categoria = celda("categoria")?.comoTexto()?.trim()?.ifBlank { null },
                descripcion = descripcion.ifBlank { "(sin descripcion)" },
                medio = celda("medio")?.comoTexto()?.let(Medio::desdeEtiqueta) ?: Medio.ELECTRONICO,
                tipo = tipo,
                contraparteArchivo = celda("contraparte")?.let(::leeContraparte),
                nota = celda("nota")?.comoTexto()?.trim()?.ifBlank { null }
            )
        }
        return Lectura(crudas, omitidos)
    }

    private class Alineado(val filas: List<FilaCruda>, val corregidos: Int)

    /**
     * El signo del importe manda sobre el Tipo escrito: si se contradicen, se
     * voltea el tipo y el importe no se toca. Los internos (saldo inicial,
     * ajuste de valor) aceptan cualquier signo.
     */
    private fun alineaTipos(
        filas: List<FilaCruda>,
        opciones: OpcionesImportacion,
        diagnosticos: MutableList<Diagnostico>
    ): Alineado {
        if (!opciones.corregirTipoSegunSigno) return Alineado(filas, 0)
        var corregidos = 0
        val alineadas = filas.map { fila ->
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
            corregidos++
            diagnosticos += Diagnostico(
                Severidad.AVISO,
                "El tipo decia \"${fila.tipo.etiqueta}\" pero el importe es " +
                    "${if (real > 0) "positivo" else "negativo"}. " +
                    "Se corrigio a \"${nuevo.etiqueta}\"; el importe no se toco.",
                fila.numeroFila
            )
            fila.copy(tipo = nuevo)
        }
        return Alineado(alineadas, corregidos)
    }

    private class Emparejado(
        /** numeroFila -> grupo de transferencia que comparten las dos patas. */
        val grupos: Map<Int, String>,
        val emparejadas: Int,
        val huerfanas: Int
    )

    /**
     * Junta cada salida de transferencia con una entrada de la misma fecha e
     * importe. Primero exige ademas la misma descripcion y despues la afloja,
     * para que una pareja floja no le gane el lugar a una exacta.
     */
    private fun emparejaTransferencias(
        filas: List<FilaCruda>,
        opciones: OpcionesImportacion,
        diagnosticos: MutableList<Diagnostico>
    ): Emparejado {
        if (!opciones.emparejarTransferencias) return Emparejado(emptyMap(), 0, 0)

        val grupos = HashMap<Int, String>()
        var emparejadas = 0
        val salidas = filas.filter { it.tipo == TipoMovimiento.TRANSFERENCIA_SALIDA }
            .toMutableList()
        val entradas = filas.filter { it.tipo == TipoMovimiento.TRANSFERENCIA_ENTRADA }
            .toMutableList()

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

        val huerfanas = salidas.size + entradas.size
        if (huerfanas > 0) {
            diagnosticos += Diagnostico(
                Severidad.AVISO,
                "Quedaron $huerfanas patas de transferencia sin pareja. " +
                    "Suelen ser movimientos que salen de una cuenta que no esta registrada."
            )
        }
        return Emparejado(grupos, emparejadas, huerfanas)
    }

    private class CuentasResueltas(
        /** Por nombre normalizado: las que ya habia y las que se crearon. */
        val indice: Map<String, Cuenta>,
        val creadas: List<String>
    )

    /**
     * Diccionarios va primero: ahi la naturaleza de la cuenta viene declarada,
     * y crearla desde el nombre del movimiento solo la adivina. Ademas entran
     * las cuentas que aun no tienen ningun movimiento.
     */
    private suspend fun resuelveCuentas(
        filas: List<FilaCruda>,
        diccionarios: LectorCatalogos.Diccionarios,
        opciones: OpcionesImportacion,
        diagnosticos: MutableList<Diagnostico>,
        delLibro: ClavesDelLibro
    ): CuentasResueltas {
        val creadas = mutableListOf<String>()
        val indice = cuentaDao.todas()
            .associateBy { it.nombre.normalizaClave() }
            .toMutableMap()

        diccionarios.cuentas.forEach { declarada ->
            delLibro.anotaCuenta(declarada.nombre)
            aseguraCuenta(
                nombre = declarada.nombre,
                tipoDeclarado = declarada.tipo,
                indice = indice,
                creadas = creadas,
                diagnosticos = diagnosticos,
                opciones = opciones,
                reportaFaltante = false
            )
        }

        filas.map { it.cuenta }.distinct().forEach { nombre ->
            delLibro.anotaCuenta(nombre)
            aseguraCuenta(nombre, null, indice, creadas, diagnosticos, opciones)
        }
        return CuentasResueltas(indice, creadas)
    }

    /**
     * Decide la categoria de cada movimiento mientras se inserta. Puede crear
     * las que nombra la columna Categoria, asi que vive dentro de la
     * transaccion y lleva la cuenta de lo creado y de lo que quedo sin
     * clasificar.
     */
    private inner class ResolutorCategorias(
        private val indice: MutableMap<String, Categoria>,
        private val mapeo: Map<String, Long>,
        private val opciones: OpcionesImportacion,
        val creadas: MutableList<String>
    ) {
        var sinCategoria = 0
            private set

        suspend fun resuelve(fila: FilaCruda): Long? {
            if (fila.tipo.esInterno) return null
            val clave = fila.descripcion.normalizaClave()
            if (clave in Semilla.DESCRIPCIONES_SIN_CATEGORIA) return null

            // a) la columna Categoria manda si viene
            fila.categoria?.let { nombre -> return deLaColumna(nombre, fila.tipo) }

            if (!opciones.autoCategorizar) { sinCategoria++; return null }

            // b) mapeo por descripcion, primero la variante especifica por tipo
            mapeo[Semilla.claveAlias(fila.descripcion, fila.tipo)]?.let { return it }
            mapeo[clave]?.let { return it }

            // c) coincidencia directa con una categoria existente
            indice[clave]?.let { return it.id }

            sinCategoria++
            return null
        }

        private suspend fun deLaColumna(nombre: String, tipo: TipoMovimiento): Long? {
            indice[nombre.normalizaClave()]?.let { return it.id }
            if (!opciones.autoCategorizar) return null
            val tipoCat = tipoCategoriaPara(tipo)
            val id = categoriaDao.inserta(Categoria(nombre = nombre, tipo = tipoCat, orden = 999))
            indice[nombre.normalizaClave()] = Categoria(id = id, nombre = nombre, tipo = tipoCat)
            creadas += nombre
            return id
        }
    }

    private suspend fun preparaCategorias(
        filas: List<FilaCruda>,
        diccionarios: LectorCatalogos.Diccionarios,
        opciones: OpcionesImportacion,
        diagnosticos: MutableList<Diagnostico>,
        delLibro: ClavesDelLibro
    ): ResolutorCategorias {
        val creadas = mutableListOf<String>()
        val indice = categoriaDao.todas()
            .associateBy { it.nombre.normalizaClave() }
            .toMutableMap()
        filas.mapNotNull { it.categoria }.forEach(delLibro::anotaCategoria)
        aplicaCategoriasDeDiccionario(
            diccionarios.categorias,
            filas,
            indice,
            creadas,
            diagnosticos,
            delLibro
        )
        val mapeo = mapeoDao.todos().associate { it.clave to it.categoriaId }
        return ResolutorCategorias(indice, mapeo, opciones, creadas)
    }

    private class Insercion(
        val importados: Int,
        val omitidos: Int,
        val contrapartesRecalculadas: Int
    )

    /**
     * Escribe los movimientos. Al reemplazar vacia primero, todavia dentro de
     * la transaccion de [importa]: si algo falla de aqui en adelante, los
     * movimientos de antes siguen donde estaban.
     */
    private suspend fun inserta(
        filas: List<FilaCruda>,
        cuentas: Map<String, Cuenta>,
        grupos: Map<Int, String>,
        categorias: ResolutorCategorias,
        opciones: OpcionesImportacion
    ): Insercion {
        if (opciones.reemplazarTodo) movimientoDao.eliminaTodos()

        var omitidos = 0
        var contrapartesRecalculadas = 0
        val aInsertar = mutableListOf<Movimiento>()

        for (fila in filas) {
            val cuenta = cuentas[fila.cuenta.normalizaClave()]
            if (cuenta == null) { omitidos++; continue }

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
                categoriaId = categorias.resuelve(fila),
                descripcion = fila.descripcion,
                medio = fila.medio,
                tipo = fila.tipo,
                contraparte = contraparte,
                grupoTransferencia = grupos[fila.numeroFila],
                nota = fila.nota
            )
        }

        movimientoDao.insertaTodos(aInsertar)
        return Insercion(aInsertar.size, omitidos, contrapartesRecalculadas)
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
                diagnosticos += Diagnostico(
                    Severidad.ERROR,
                    "La cuenta \"$nombre\" no existe en Ollin Finanzas."
                )
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
            diccionarios.categorias,
            emptyList(),
            indiceCategorias,
            categoriasCreadas,
            diagnosticos,
            delLibro
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
        val padresDeVivas = todas.filterNot { it.id in idsCandidatas }.mapNotNullTo(
            HashSet()
        ) { it.padreId }
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
                "Al reemplazar quite ${cuentasSobran.size} cuentas y " +
                    "$categoriasEliminadas categorias que quedaron sin un solo movimiento y " +
                    "que el libro no nombra" +
                    if (cuentasSobran.isEmpty()) {
                        "."
                    } else {
                        ": ${cuentasSobran.joinToString { it.nombre }}."
                    }
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
            texto.startsWith(
                "1"
            ) || texto.contains("propia") || texto.contains("mis cuentas") -> Contraparte.PROPIA
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

            clave.contains(
                "cartera"
            ) || clave.contains("efectivo") || clave.contains("caja") -> TipoCuenta.EFECTIVO
            clave.contains(
                "terreno"
            ) || clave.contains("cripto") || clave.contains("inmueble") -> TipoCuenta.ACTIVO
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
