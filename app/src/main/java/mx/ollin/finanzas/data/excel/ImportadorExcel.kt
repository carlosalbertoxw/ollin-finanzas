package mx.ollin.finanzas.data.excel

import mx.ollin.finanzas.data.db.Categoria
import mx.ollin.finanzas.data.db.CategoriaDao
import mx.ollin.finanzas.data.db.Cuenta
import mx.ollin.finanzas.data.db.CuentaDao
import mx.ollin.finanzas.data.db.MapeoDescripcionDao
import mx.ollin.finanzas.data.db.Movimiento
import mx.ollin.finanzas.data.db.MovimientoDao
import mx.ollin.finanzas.data.db.Semilla
import mx.ollin.finanzas.domain.model.Contraparte
import mx.ollin.finanzas.domain.model.Dinero
import mx.ollin.finanzas.domain.model.Medio
import mx.ollin.finanzas.domain.model.TipoCategoria
import mx.ollin.finanzas.domain.model.TipoCuenta
import mx.ollin.finanzas.domain.model.TipoMovimiento
import mx.ollin.finanzas.domain.model.normalizaClave
import java.io.InputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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
    /** Vacia los movimientos antes de importar. Si es falso, agrega. */
    val reemplazarTodo: Boolean = true
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
    val diagnosticos: List<Diagnostico> = emptyList()
) {
    val huboProblemas: Boolean get() = diagnosticos.any { it.severidad != Severidad.INFO }
}

/**
 * Lee un libro de Excel y lo vuelca en la base de Ollin Finanzas.
 *
 * No se limita a copiar: al entrar corrige lo que en una hoja de calculo se
 * degrada solo — tipos que contradicen al signo del importe, transferencias a
 * medias y contrapartes que dejaron de mantenerse a mano.
 */
class ImportadorExcel(
    private val cuentaDao: CuentaDao,
    private val categoriaDao: CategoriaDao,
    private val movimientoDao: MovimientoDao,
    private val mapeoDao: MapeoDescripcionDao
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

        val FORMATOS_FECHA = listOf(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy")
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
        val hoja = eligeHojaDeDatos(libro)
            ?: return ResultadoImportacion(
                diagnosticos = listOf(
                    Diagnostico(Severidad.ERROR, "No encontre ninguna hoja con columnas de Fecha, Cantidad y Cuenta.")
                )
            )
        return procesa(hoja, opciones)
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
        opciones: OpcionesImportacion
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

        corregidas.map { it.cuenta }.distinct().forEach { nombre ->
            val clave = nombre.normalizaClave()
            if (indiceCuentas.containsKey(clave)) return@forEach
            if (!opciones.crearCuentasFaltantes) {
                diagnosticos += Diagnostico(Severidad.ERROR, "La cuenta \"$nombre\" no existe en Ollin Finanzas.")
                return@forEach
            }
            val tipo = infiereTipoCuenta(nombre)
            val id = cuentaDao.inserta(
                Cuenta(
                    nombre = nombre,
                    tipo = tipo,
                    medioPorDefecto = Semilla.medioSugerido(tipo),
                    soloElectronico = infiereSoloElectronico(nombre, tipo),
                    orden = indiceCuentas.size
                )
            )
            indiceCuentas[clave] = Cuenta(id = id, nombre = nombre, tipo = tipo)
            cuentasCreadas += nombre
        }

        // ---- 4. Resolver categorias ----------------------------------------
        val categoriasCreadas = mutableListOf<String>()
        val indiceCategorias = categoriaDao.todas()
            .associateBy { it.nombre.normalizaClave() }
            .toMutableMap()
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

    // ------------------------------------------------------------- lectura

    private fun leeFecha(celda: CeldaLeida): LocalDate? {
        celda.numero?.let { return Ooxml.desdeSerial(it) }
        val texto = celda.texto?.trim().orEmpty()
        if (texto.isEmpty()) return null
        FORMATOS_FECHA.forEach { formato ->
            runCatching { return LocalDate.parse(texto, formato) }
        }
        return null
    }

    private fun leeImporte(celda: CeldaLeida): Long? {
        celda.numero?.let { return Dinero.aCentavos(it) }
        return celda.texto?.let(Dinero::parsea)
    }

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
