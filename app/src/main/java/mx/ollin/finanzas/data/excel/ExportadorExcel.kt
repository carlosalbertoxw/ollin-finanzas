package mx.ollin.finanzas.data.excel

import mx.ollin.finanzas.data.db.Categoria
import mx.ollin.finanzas.data.db.Movimiento
import mx.ollin.finanzas.domain.model.Contraparte
import mx.ollin.finanzas.domain.model.Dinero
import mx.ollin.finanzas.domain.model.Medio
import mx.ollin.finanzas.domain.model.TipoCategoria
import mx.ollin.finanzas.domain.model.TipoCuenta
import mx.ollin.finanzas.domain.model.TipoMovimiento
import java.io.OutputStream
import java.time.YearMonth
import kotlin.math.abs

/**
 * Convierte los datos de Ollin en un libro de Excel.
 *
 * Las hojas de analisis llevan formulas reales (SUMIFS) apuntando a Registros,
 * ademas del valor ya calculado como cache. Asi la hoja se ve correcta al
 * abrirla en cualquier visor y sigue viva si editas un movimiento a mano.
 */
class ExportadorExcel(
    private val datos: DatosExportacion,
    private val esquema: EsquemaExportacion,
    seleccion: Set<HojaExportable>
) {

    private val hojasElegidas = HojaExportable.normaliza(seleccion)

    // Referencias a columnas de Registros, resueltas segun el esquema.
    private val colFecha = Ooxml.letraColumna(esquema.columnaFecha)
    private val colCantidad = Ooxml.letraColumna(esquema.columnaCantidad)
    private val colCuenta = Ooxml.letraColumna(esquema.columnaCuenta)
    private val colGrupo = Ooxml.letraColumna(esquema.columnaAgrupacion)
    private val colTipo = Ooxml.letraColumna(esquema.columnaTipo)

    private val ultimaFila = maxOf(datos.ultimaFilaRegistros, 2)
    private val meses = datos.meses

    fun escribeEn(salida: OutputStream) {
        val hojas = buildList {
            // El orden de las pestañas sigue el de lectura natural: primero el
            // analisis, luego el detalle, y los catalogos al final.
            if (HojaExportable.BALANCE in hojasElegidas) add(hojaBalance())
            if (HojaExportable.INGRESOS_EGRESOS in hojasElegidas) add(hojaIngresosEgresos())
            if (HojaExportable.PRESUPUESTO in hojasElegidas) add(hojaPresupuesto())
            if (HojaExportable.TRANSFERENCIAS in hojasElegidas) add(hojaTransferencias())
            if (HojaExportable.COMPROMISOS in hojasElegidas) add(hojaCompromisos())
            add(hojaRegistros())
            if (HojaExportable.DICCIONARIOS in hojasElegidas) add(hojaDiccionarios())
        }
        XlsxEscritor(hojas).escribeEn(salida)
    }

    // ------------------------------------------------------------ Registros

    private fun hojaRegistros(): Hoja {
        val columnas = esquema.columnas
        val filas = mutableListOf<List<Celda>>()
        filas += columnas.map { Celda.Texto(it, Estilo.ENCABEZADO) }

        datos.movimientosOrdenados.forEach { m ->
            filas += when (esquema) {
                EsquemaExportacion.EXTENDIDO -> filaExtendida(m)
                EsquemaExportacion.COMPACTO -> filaCompacta(m)
            }
        }

        val rango = "A1:${Ooxml.letraColumna(columnas.size)}$ultimaFila"
        val incluyeDiccionarios = HojaExportable.DICCIONARIOS in hojasElegidas

        return Hoja(
            nombre = "Registros",
            filas = filas,
            anchos = anchosRegistros(),
            congelarTrasFila = 1,
            validaciones = if (incluyeDiccionarios) validacionesRegistros() else emptyList(),
            // Tabla de Excel de verdad: el filtro y el formato crecen solos al
            // agregar renglones a mano.
            tabla = TablaExcel(
                nombre = "tblRegistros",
                rango = rango,
                encabezados = columnas
            )
        )
    }

    private fun filaExtendida(m: Movimiento): List<Celda> {
        val ym = YearMonth.from(m.fecha)
        val compromiso = m.compromisoId?.let { id -> datos.compromisos.firstOrNull { it.id == id } }
        return listOf(
            Celda.Fecha(m.fecha),
            Celda.Numero(Dinero.aDoubleParaHoja(m.importeCentavos), Estilo.DINERO),
            Celda.Texto(datos.nombreCuenta(m.cuentaId)),
            Celda.Texto(datos.nombreCategoria(m.categoriaId)),
            Celda.Texto(m.descripcion),
            Celda.Texto(m.medio.etiqueta),
            Celda.Texto(if (m.contraparte == Contraparte.PROPIA) "Propia" else "Tercero"),
            Celda.Texto(m.tipo.etiqueta),
            Celda.Texto(ym.toString()),
            Celda.Numero(ym.year.toDouble(), Estilo.ENTERO),
            Celda.Texto(m.nota.orEmpty()),
            Celda.Texto(compromiso?.nombre.orEmpty())
        )
    }

    private fun filaCompacta(m: Movimiento): List<Celda> = listOf(
        Celda.Fecha(m.fecha),
        Celda.Numero(Dinero.aDoubleParaHoja(m.importeCentavos), Estilo.DINERO),
        Celda.Texto(datos.nombreCuenta(m.cuentaId)),
        Celda.Texto(m.descripcion),
        Celda.Texto(m.medio.etiqueta),
        Celda.Numero(m.contraparte.codigo.toDouble(), Estilo.ENTERO),
        Celda.Texto(m.tipo.etiqueta),
        Celda.Numero(m.fecha.monthValue.toDouble(), Estilo.ENTERO)
    )

    private fun anchosRegistros(): List<AnchoColumna> = when (esquema) {
        EsquemaExportacion.EXTENDIDO -> listOf(
            AnchoColumna(1, 12.0), AnchoColumna(2, 14.0), AnchoColumna(3, 22.0),
            AnchoColumna(4, 22.0), AnchoColumna(5, 28.0), AnchoColumna(6, 12.0),
            AnchoColumna(7, 12.0), AnchoColumna(8, 20.0), AnchoColumna(9, 10.0),
            AnchoColumna(10, 8.0), AnchoColumna(11, 30.0), AnchoColumna(12, 20.0)
        )
        EsquemaExportacion.COMPACTO -> listOf(
            AnchoColumna(1, 12.0), AnchoColumna(2, 14.0), AnchoColumna(3, 22.0),
            AnchoColumna(4, 28.0), AnchoColumna(5, 12.0), AnchoColumna(6, 12.0),
            AnchoColumna(7, 20.0), AnchoColumna(8, 7.0)
        )
    }

    private fun validacionesRegistros(): List<ValidacionLista> {
        val fin = maxOf(ultimaFila, 2000)
        val lista = mutableListOf<ValidacionLista>()
        val cuentas = datos.cuentas.size + 1
        lista += ValidacionLista(
            "${colCuenta}2:${colCuenta}${fin}",
            Ooxml.refHoja("Diccionarios", "\$A\$2:\$A\$$cuentas")
        )
        if (esquema == EsquemaExportacion.EXTENDIDO) {
            // Solo las categorias hoja, que son las que se capturan.
            val cats = datos.categorias.count { it.padreId != null } + 1
            lista += ValidacionLista(
                "${colGrupo}2:${colGrupo}${fin}",
                Ooxml.refHoja("Diccionarios", "\$C\$2:\$C\$$cats")
            )
        }
        // El rango se deriva de los tipos que existen. Fijarlo a mano dejaba
        // fuera del desplegable a cualquier tipo agregado despues.
        val tipos = TipoMovimiento.entries.size + 1
        lista += ValidacionLista(
            "${colTipo}2:${colTipo}${fin}",
            Ooxml.refHoja("Diccionarios", "\$G\$2:\$G\$$tipos")
        )
        return lista
    }

    // --------------------------------------------------------- Diccionarios

    private fun hojaDiccionarios(): Hoja {
        val filas = mutableListOf<List<Celda>>()
        filas += listOf(
            Celda.Texto("Cuentas", Estilo.ENCABEZADO),
            Celda.Texto("Naturaleza", Estilo.ENCABEZADO),
            Celda.Texto("Categorias", Estilo.ENCABEZADO),
            Celda.Texto("Grupo", Estilo.ENCABEZADO),
            Celda.Texto("Medio", Estilo.ENCABEZADO),
            Celda.Texto("Contraparte", Estilo.ENCABEZADO),
            Celda.Texto("Tipo", Estilo.ENCABEZADO)
        )

        val cuentas = datos.cuentas
        val categorias = datos.categorias.filter { it.padreId != null }
            .ifEmpty { datos.categorias }
        val medios = Medio.entries
        val contrapartes = Contraparte.entries
        val tipos = TipoMovimiento.entries

        val total = maxOf(cuentas.size, categorias.size, medios.size, contrapartes.size, tipos.size)
        for (i in 0 until total) {
            filas += listOf(
                Celda.Texto(cuentas.getOrNull(i)?.nombre.orEmpty()),
                Celda.Texto(cuentas.getOrNull(i)?.tipo?.etiqueta.orEmpty()),
                Celda.Texto(categorias.getOrNull(i)?.nombre.orEmpty()),
                Celda.Texto(categorias.getOrNull(i)?.let { hija ->
                    datos.categoriaPorId[hija.padreId]?.nombre.orEmpty()
                }.orEmpty()),
                Celda.Texto(medios.getOrNull(i)?.etiqueta.orEmpty()),
                Celda.Texto(contrapartes.getOrNull(i)?.let { "${it.codigo} - ${it.etiqueta}" }.orEmpty()),
                Celda.Texto(tipos.getOrNull(i)?.etiqueta.orEmpty())
            )
        }

        return Hoja(
            nombre = "Diccionarios",
            filas = filas,
            anchos = (1..7).map { AnchoColumna(it, 24.0) },
            congelarTrasFila = 1
        )
    }

    // -------------------------------------------------------------- Balance

    private fun hojaBalance(): Hoja {
        val filas = mutableListOf<List<Celda>>()
        filas += fila(Celda.Texto("Balance por cuenta", Estilo.TITULO))
        filas += fila(Celda.Texto("Saldo vivo = suma de todos los movimientos de la cuenta.", Estilo.TENUE))
        filas += fila()

        var totalLiquido = 0L
        var totalDeuda = 0L
        var totalNoLiquido = 0L

        TipoCuenta.entries.forEach { tipo ->
            val delTipo = datos.cuentas.filter { it.tipo == tipo }
            if (delTipo.isEmpty()) return@forEach

            filas += listOf(
                Celda.Texto(tipo.etiqueta, Estilo.SUBTITULO),
                Celda.Texto("Saldo", Estilo.SUBTITULO),
                Celda.Texto("Movs.", Estilo.SUBTITULO)
            )
            val primeraFilaGrupo = filas.size + 1

            delTipo.forEach { cuenta ->
                val saldo = saldoDe(cuenta.nombre)
                when {
                    tipo.esDeuda -> totalDeuda += saldo
                    tipo.esLiquida -> totalLiquido += saldo
                    else -> totalNoLiquido += saldo
                }
                filas += listOf(
                    Celda.Texto(cuenta.nombre),
                    Celda.Formula(
                        "SUMIFS(${rango(colCantidad)},${rango(colCuenta)},\$A${filas.size + 1})",
                        cache = centavosADouble(saldo),
                        estilo = if (saldo < 0) Estilo.DINERO_NEGATIVO else Estilo.DINERO
                    ),
                    Celda.Formula(
                        "COUNTIFS(${rango(colCuenta)},\$A${filas.size + 1})",
                        cache = datos.movimientos.count {
                            datos.nombreCuenta(it.cuentaId) == cuenta.nombre
                        }.toDouble(),
                        estilo = Estilo.ENTERO
                    )
                )
            }

            val ultimaFilaGrupo = filas.size
            filas += listOf(
                Celda.Texto("Subtotal ${tipo.etiqueta}", Estilo.NEGRITA),
                Celda.Formula(
                    "SUM(B$primeraFilaGrupo:B$ultimaFilaGrupo)",
                    cache = centavosADouble(delTipo.sumOf { saldoDe(it.nombre) }),
                    estilo = Estilo.DINERO_TOTAL
                )
            )
            filas += fila()
        }

        val patrimonio = totalLiquido + totalDeuda + totalNoLiquido
        val gastoMensual = gastoConsumoMensualPromedio()

        filas += fila(Celda.Texto("Resumen", Estilo.TITULO))
        filas += renglonValor("Liquidez (efectivo + banco + inversion)", totalLiquido)
        filas += renglonValor("Deuda de tarjetas", totalDeuda)
        filas += renglonValor("Patrimonio no liquido", totalNoLiquido)
        filas += listOf(
            Celda.Texto("Patrimonio neto", Estilo.NEGRITA),
            Celda.Numero(centavosADouble(patrimonio), Estilo.DINERO_TOTAL)
        )
        filas += fila()
        filas += listOf(
            Celda.Texto("Gasto de consumo mensual promedio", Estilo.NEGRITA),
            Celda.Numero(centavosADouble(gastoMensual), Estilo.DINERO)
        )
        filas += listOf(
            Celda.Texto("Meses de fondo de emergencia cubiertos", Estilo.NEGRITA),
            Celda.Numero(
                if (gastoMensual > 0) totalLiquido.toDouble() / gastoMensual else 0.0,
                Estilo.NORMAL
            )
        )
        filas += fila(Celda.Texto("Solo cuenta la liquidez; el patrimonio no liquido no paga la despensa.", Estilo.TENUE))

        return Hoja(
            nombre = "Balance",
            filas = filas,
            anchos = listOf(AnchoColumna(1, 40.0), AnchoColumna(2, 18.0), AnchoColumna(3, 10.0))
        )
    }

    // ---------------------------------------------------- Ingresos - Egresos

    private fun hojaIngresosEgresos(): Hoja {
        val filas = mutableListOf<List<Celda>>()
        filas += fila(Celda.Texto("Ingresos y egresos por mes", Estilo.TITULO))
        filas += fila(
            Celda.Texto(
                if (esquema == EsquemaExportacion.EXTENDIDO)
                    "Agrupado por categoria. Las compras de patrimonio van aparte: no son gasto."
                else
                    "Agrupado por descripcion: el modo compacto no exporta categoria.",
                Estilo.TENUE
            )
        )
        filas += fila()

        if (esquema == EsquemaExportacion.EXTENDIDO) {
            bloquePorCategoria(filas, TipoCategoria.GASTO, TipoMovimiento.SALIDA, "Gastos")
            filas += fila()
            bloquePorCategoria(filas, TipoCategoria.PATRIMONIO, TipoMovimiento.SALIDA, "Compra de patrimonio (no es gasto)")
            filas += fila()
            bloquePorCategoria(filas, TipoCategoria.INGRESO, TipoMovimiento.ENTRADA, "Ingresos")
        } else {
            bloquePorDescripcion(filas, TipoMovimiento.SALIDA, "Egresos")
            filas += fila()
            bloquePorDescripcion(filas, TipoMovimiento.ENTRADA, "Ingresos")
        }

        filas += fila()
        bloqueFlujoNeto(filas)

        return Hoja(
            nombre = "Ingresos - Egresos",
            filas = filas,
            anchos = listOf(AnchoColumna(1, 34.0)) + (2..(meses.size + 2)).map { AnchoColumna(it, 14.0) },
            congelarTrasFila = 0
        )
    }

    private fun encabezadoMeses(etiquetaPrimera: String): List<Celda> =
        listOf(Celda.Texto(etiquetaPrimera, Estilo.ENCABEZADO)) +
            meses.map { Celda.Texto(it.toString(), Estilo.ENCABEZADO) } +
            Celda.Texto("Total", Estilo.ENCABEZADO)

    private fun bloquePorCategoria(
        filas: MutableList<List<Celda>>,
        tipoCategoria: TipoCategoria,
        tipoMovimiento: TipoMovimiento,
        titulo: String
    ) {
        filas += fila(Celda.Texto(titulo, Estilo.SUBTITULO))
        filas += encabezadoMeses("Categoria")

        val grupos = datos.agrupadasPorPadre(tipoCategoria)
        val filasHijas = mutableListOf<Int>()

        grupos.forEach { (padre, hijas) ->
            if (hijas.isEmpty()) return@forEach
            filas += fila(Celda.Texto(padre.nombre, Estilo.NEGRITA))
            hijas.forEach { hija ->
                val numeroFila = filas.size + 1
                filasHijas += numeroFila
                filas += renglonCategoria(hija, tipoMovimiento, numeroFila)
            }
        }

        // Total del bloque: suma solo las filas hoja, sin duplicar los padres.
        val totalFila = mutableListOf<Celda>(Celda.Texto("Total $titulo", Estilo.NEGRITA))
        meses.forEachIndexed { i, ym ->
            val letra = Ooxml.letraColumna(i + 2)
            val expr = if (filasHijas.isEmpty()) "0" else filasHijas.joinToString("+") { "$letra$it" }
            totalFila += Celda.Formula(
                expr,
                cache = centavosADouble(
                    datos.categorias.filter { it.tipo == tipoCategoria && it.padreId != null }
                        .sumOf { totalCategoriaMes(it.id, tipoMovimiento, ym) }
                ),
                estilo = Estilo.DINERO_TOTAL
            )
        }
        val letraTotal = Ooxml.letraColumna(meses.size + 2)
        totalFila += Celda.Formula(
            if (filasHijas.isEmpty()) "0" else filasHijas.joinToString("+") { "$letraTotal$it" },
            cache = centavosADouble(
                datos.categorias.filter { it.tipo == tipoCategoria && it.padreId != null }
                    .sumOf { cat -> meses.sumOf { totalCategoriaMes(cat.id, tipoMovimiento, it) } }
            ),
            estilo = Estilo.DINERO_TOTAL
        )
        filas += totalFila
    }

    /**
     * Un renglon de categoria. Necesita saber en que fila va a caer porque las
     * formulas SUMIFS toman el nombre de la categoria de su propia celda A,
     * asi el usuario puede renombrarla en la hoja y el calculo la sigue.
     */
    private fun renglonCategoria(
        categoria: Categoria,
        tipo: TipoMovimiento,
        numeroFila: Int
    ): List<Celda> {
        // Sin sangria: el texto de esta celda es el criterio literal del SUMIFS,
        // y "   Gasolina" no coincidiria con "Gasolina" en la columna Categoria.
        val fila = mutableListOf<Celda>(Celda.Texto(categoria.nombre))
        meses.forEach { ym ->
            fila += Celda.Formula(
                sumifsMes(criterioGrupo = "\$A$numeroFila", tipo = tipo, ym = ym),
                cache = centavosADouble(totalCategoriaMes(categoria.id, tipo, ym)),
                estilo = Estilo.DINERO
            )
        }
        val ultimaColumnaMes = Ooxml.letraColumna(meses.size + 1)
        fila += Celda.Formula(
            "SUM(B$numeroFila:$ultimaColumnaMes$numeroFila)",
            cache = centavosADouble(meses.sumOf { totalCategoriaMes(categoria.id, tipo, it) }),
            estilo = Estilo.DINERO_TOTAL
        )
        return fila
    }

    private fun bloquePorDescripcion(
        filas: MutableList<List<Celda>>,
        tipo: TipoMovimiento,
        titulo: String
    ) {
        filas += fila(Celda.Texto(titulo, Estilo.SUBTITULO))
        filas += encabezadoMeses("Descripcion")

        val descripciones = datos.movimientos
            .filter { it.tipo == tipo }
            .groupBy { it.descripcion }
            .mapValues { (_, lista) -> lista.sumOf { it.importeCentavos } }
            .entries.sortedBy { abs(it.value) * -1 }

        descripciones.forEach { (descripcion, _) ->
            val fila = mutableListOf<Celda>(Celda.Texto(descripcion))
            meses.forEach { ym ->
                val valor = datos.movimientos
                    .filter { it.tipo == tipo && it.descripcion == descripcion && YearMonth.from(it.fecha) == ym }
                    .sumOf { it.importeCentavos }
                fila += Celda.Numero(centavosADouble(valor), Estilo.DINERO)
            }
            fila += Celda.Numero(
                centavosADouble(
                    datos.movimientos.filter { it.tipo == tipo && it.descripcion == descripcion }
                        .sumOf { it.importeCentavos }
                ),
                Estilo.DINERO_TOTAL
            )
            filas += fila
        }
    }

    private fun bloqueFlujoNeto(filas: MutableList<List<Celda>>) {
        filas += fila(Celda.Texto("Flujo del mes", Estilo.SUBTITULO))
        filas += encabezadoMeses("Concepto")

        val ingresos = meses.map { totalTipoMes(TipoMovimiento.ENTRADA, it) }
        val consumo = meses.map { gastoConsumoMes(it) }
        val patrimonio = meses.map { compraPatrimonioMes(it) }

        filas += renglonSerie("Ingresos", ingresos)
        filas += renglonSerie("Gasto de consumo", consumo)
        filas += renglonSerie("Compra de patrimonio", patrimonio)
        filas += renglonSerie("Neto (ingresos - consumo)", ingresos.indices.map { ingresos[it] + consumo[it] })

        val tasas = ingresos.indices.map {
            if (ingresos[it] <= 0L) 0.0
            else (ingresos[it] + consumo[it]).toDouble() / ingresos[it]
        }
        val filaTasa = mutableListOf<Celda>(Celda.Texto("Tasa de ahorro", Estilo.NEGRITA))
        tasas.forEach { filaTasa += Celda.Numero(it, Estilo.PORCENTAJE) }
        val tasaGlobal = ingresos.sum().let {
            if (it <= 0L) 0.0 else (it + consumo.sum()).toDouble() / it
        }
        filaTasa += Celda.Numero(tasaGlobal, Estilo.PORCENTAJE_TOTAL)
        filas += filaTasa
    }

    private fun renglonSerie(etiqueta: String, valores: List<Long>): List<Celda> {
        val fila = mutableListOf<Celda>(Celda.Texto(etiqueta, Estilo.NEGRITA))
        valores.forEach { fila += Celda.Numero(centavosADouble(it), Estilo.DINERO) }
        fila += Celda.Numero(centavosADouble(valores.sum()), Estilo.DINERO_TOTAL)
        return fila
    }

    // --------------------------------------------------------- Presupuesto

    private fun hojaPresupuesto(): Hoja {
        val filas = mutableListOf<List<Celda>>()
        filas += fila(Celda.Texto("Presupuesto contra realidad", Estilo.TITULO))
        filas += fila(Celda.Texto("Meta positiva. Real en valor absoluto. Desviacion negativa = te pasaste.", Estilo.TENUE))
        filas += fila()

        if (datos.presupuestos.isEmpty()) {
            filas += fila(Celda.Texto("Todavia no hay metas capturadas.", Estilo.TENUE))
            filas += fila(Celda.Texto("Definelas en Ollin, pestaña Presupuesto, y vuelve a exportar.", Estilo.TENUE))
            return Hoja("Presupuesto", filas, listOf(AnchoColumna(1, 40.0)))
        }

        val porPeriodo = datos.presupuestos.groupBy { YearMonth.of(it.anio, it.mes) }.toSortedMap()

        porPeriodo.forEach { (ym, lista) ->
            filas += fila(Celda.Texto(ym.toString(), Estilo.SUBTITULO))
            filas += listOf(
                Celda.Texto("Categoria", Estilo.ENCABEZADO),
                Celda.Texto("Meta", Estilo.ENCABEZADO),
                Celda.Texto("Real", Estilo.ENCABEZADO),
                Celda.Texto("Desviacion", Estilo.ENCABEZADO),
                Celda.Texto("Avance", Estilo.ENCABEZADO)
            )
            lista.sortedByDescending { it.montoCentavos }.forEach { p ->
                val categoria = datos.categoriaPorId[p.categoriaId] ?: return@forEach
                val tipoMov = if (categoria.tipo == TipoCategoria.INGRESO)
                    TipoMovimiento.ENTRADA else TipoMovimiento.SALIDA
                val real = abs(totalCategoriaMes(p.categoriaId, tipoMov, ym))
                val n = filas.size + 1
                filas += listOf(
                    Celda.Texto(categoria.nombre),
                    Celda.Numero(centavosADouble(p.montoCentavos), Estilo.DINERO),
                    Celda.Formula(
                        "ABS(${sumifsMes("\$A$n", tipoMov, ym)})",
                        cache = centavosADouble(real),
                        estilo = Estilo.DINERO
                    ),
                    Celda.Formula(
                        "B$n-C$n",
                        cache = centavosADouble(p.montoCentavos - real),
                        estilo = if (p.montoCentavos - real < 0) Estilo.DINERO_NEGATIVO else Estilo.DINERO
                    ),
                    Celda.Formula(
                        "IF(B$n=0,0,C$n/B$n)",
                        cache = if (p.montoCentavos == 0L) 0.0 else real.toDouble() / p.montoCentavos,
                        estilo = Estilo.PORCENTAJE
                    )
                )
            }
            filas += fila()
        }

        return Hoja(
            nombre = "Presupuesto",
            filas = filas,
            anchos = listOf(
                AnchoColumna(1, 32.0), AnchoColumna(2, 14.0),
                AnchoColumna(3, 14.0), AnchoColumna(4, 14.0), AnchoColumna(5, 10.0)
            )
        )
    }

    // ------------------------------------------------------- Transferencias

    private fun hojaTransferencias(): Hoja {
        val filas = mutableListOf<List<Celda>>()
        filas += fila(Celda.Texto("Movimientos internos por cuenta", Estilo.TITULO))
        filas += fila(Celda.Texto("El neto global debe dar cero. Si no, falta una pata de alguna transferencia.", Estilo.TENUE))
        filas += fila()
        filas += listOf(
            Celda.Texto("Cuenta", Estilo.ENCABEZADO),
            Celda.Texto("Entradas", Estilo.ENCABEZADO),
            Celda.Texto("Salidas", Estilo.ENCABEZADO),
            Celda.Texto("Neto", Estilo.ENCABEZADO)
        )

        val primera = filas.size + 1
        datos.cuentas.forEach { cuenta ->
            val f = filas.size + 1
            val entradas = totalTransferencia(cuenta.nombre, TipoMovimiento.TRANSFERENCIA_ENTRADA)
            val salidas = totalTransferencia(cuenta.nombre, TipoMovimiento.TRANSFERENCIA_SALIDA)
            filas += listOf(
                Celda.Texto(cuenta.nombre),
                Celda.Formula(
                    "SUMIFS(${rango(colCantidad)},${rango(colCuenta)},\$A$f,${rango(colTipo)},\"${TipoMovimiento.TRANSFERENCIA_ENTRADA.etiqueta}\")",
                    cache = centavosADouble(entradas), estilo = Estilo.DINERO
                ),
                Celda.Formula(
                    "SUMIFS(${rango(colCantidad)},${rango(colCuenta)},\$A$f,${rango(colTipo)},\"${TipoMovimiento.TRANSFERENCIA_SALIDA.etiqueta}\")",
                    cache = centavosADouble(salidas), estilo = Estilo.DINERO
                ),
                Celda.Formula("B$f+C$f", cache = centavosADouble(entradas + salidas), estilo = Estilo.DINERO)
            )
        }
        val ultima = filas.size

        filas += listOf(
            Celda.Texto("Total", Estilo.NEGRITA),
            Celda.Formula("SUM(B$primera:B$ultima)", cache = centavosADouble(totalTransferenciasGlobal(TipoMovimiento.TRANSFERENCIA_ENTRADA)), estilo = Estilo.DINERO_TOTAL),
            Celda.Formula("SUM(C$primera:C$ultima)", cache = centavosADouble(totalTransferenciasGlobal(TipoMovimiento.TRANSFERENCIA_SALIDA)), estilo = Estilo.DINERO_TOTAL),
            Celda.Formula("SUM(D$primera:D$ultima)", cache = 0.0, estilo = Estilo.DINERO_TOTAL)
        )

        return Hoja(
            nombre = "Transferencias",
            filas = filas,
            anchos = listOf(AnchoColumna(1, 28.0), AnchoColumna(2, 16.0), AnchoColumna(3, 16.0), AnchoColumna(4, 16.0))
        )
    }

    // --------------------------------------------------------- Compromisos

    private fun hojaCompromisos(): Hoja {
        val filas = mutableListOf<List<Celda>>()
        filas += fila(Celda.Texto("Compromisos por venir", Estilo.TITULO))
        filas += fila(Celda.Texto("Lo que ya esta comprometido y todavia no se ha pagado.", Estilo.TENUE))
        filas += fila()

        if (datos.compromisos.isEmpty()) {
            filas += fila(Celda.Texto("Sin compromisos registrados.", Estilo.TENUE))
            return Hoja("Compromisos", filas, listOf(AnchoColumna(1, 40.0)))
        }

        filas += listOf(
            Celda.Texto("Compromiso", Estilo.ENCABEZADO),
            Celda.Texto("Cuenta", Estilo.ENCABEZADO),
            Celda.Texto("Monto", Estilo.ENCABEZADO),
            Celda.Texto("Periodicidad", Estilo.ENCABEZADO),
            Celda.Texto("Proximo pago", Estilo.ENCABEZADO),
            Celda.Texto("Pagos restantes", Estilo.ENCABEZADO),
            Celda.Texto("Saldo pendiente", Estilo.ENCABEZADO)
        )

        val primera = filas.size + 1
        datos.compromisos.sortedBy { it.fechaPrimerPago }.forEach { c ->
            val restantes = c.totalPagos?.let { (it - c.pagosRealizados).coerceAtLeast(0) }
            val proximo = c.fechaPrimerPago.plusMonths(
                (c.pagosRealizados.toLong()) * c.periodicidad.meses
            )
            filas += listOf(
                Celda.Texto(c.nombre),
                Celda.Texto(c.cuentaId?.let { datos.nombreCuenta(it) }.orEmpty()),
                Celda.Numero(centavosADouble(c.montoCentavos), Estilo.DINERO),
                Celda.Texto(c.periodicidad.etiqueta),
                Celda.Fecha(proximo),
                if (restantes != null) Celda.Numero(restantes.toDouble(), Estilo.ENTERO)
                else Celda.Texto("indefinido", Estilo.TENUE),
                if (restantes != null)
                    Celda.Numero(centavosADouble(c.montoCentavos * restantes), Estilo.DINERO)
                else Celda.Texto("-", Estilo.TENUE)
            )
        }
        val ultima = filas.size
        filas += listOf(
            Celda.Texto("Total comprometido", Estilo.NEGRITA),
            Celda.Vacia, Celda.Vacia, Celda.Vacia, Celda.Vacia, Celda.Vacia,
            Celda.Formula(
                "SUM(G$primera:G$ultima)",
                cache = centavosADouble(
                    datos.compromisos.sumOf { c ->
                        val r = c.totalPagos?.let { (it - c.pagosRealizados).coerceAtLeast(0) } ?: 0
                        c.montoCentavos * r
                    }
                ),
                estilo = Estilo.DINERO_TOTAL
            )
        )

        return Hoja(
            nombre = "Compromisos",
            filas = filas,
            anchos = listOf(
                AnchoColumna(1, 28.0), AnchoColumna(2, 22.0), AnchoColumna(3, 14.0),
                AnchoColumna(4, 14.0), AnchoColumna(5, 14.0), AnchoColumna(6, 14.0),
                AnchoColumna(7, 16.0)
            )
        )
    }


    // ------------------------------------------------------------- utiles

    private fun fila(vararg celdas: Celda): List<Celda> = celdas.toList()

    private fun renglonValor(etiqueta: String, centavos: Long): List<Celda> = listOf(
        Celda.Texto(etiqueta),
        Celda.Numero(centavosADouble(centavos), if (centavos < 0) Estilo.DINERO_NEGATIVO else Estilo.DINERO)
    )

    private fun rango(columna: String): String =
        Ooxml.refHoja("Registros", "\$$columna\$2:\$$columna\$$ultimaFila")

    /** SUMIFS por grupo + tipo + mes, con criterios de fecha independientes del idioma. */
    private fun sumifsMes(criterioGrupo: String, tipo: TipoMovimiento, ym: YearMonth): String =
        "SUMIFS(${rango(colCantidad)}," +
            "${rango(colGrupo)},$criterioGrupo," +
            "${rango(colTipo)},\"${tipo.etiqueta}\"," +
            "${rango(colFecha)},\">=\"&DATE(${ym.year},${ym.monthValue},1)," +
            "${rango(colFecha)},\"<=\"&DATE(${ym.year},${ym.monthValue},${ym.lengthOfMonth()}))"

    private fun centavosADouble(centavos: Long): Double = Dinero.aDoubleParaHoja(centavos)

    private fun saldoDe(nombreCuenta: String): Long =
        datos.movimientos.filter { datos.nombreCuenta(it.cuentaId) == nombreCuenta }
            .sumOf { it.importeCentavos }

    private fun totalCategoriaMes(categoriaId: Long, tipo: TipoMovimiento, ym: YearMonth): Long =
        datos.movimientos
            .filter { it.categoriaId == categoriaId && it.tipo == tipo && YearMonth.from(it.fecha) == ym }
            .sumOf { it.importeCentavos }

    private fun totalTipoMes(tipo: TipoMovimiento, ym: YearMonth): Long =
        datos.movimientos.filter { it.tipo == tipo && YearMonth.from(it.fecha) == ym }
            .sumOf { it.importeCentavos }

    private fun gastoConsumoMes(ym: YearMonth): Long =
        datos.movimientos.filter {
            it.tipo == TipoMovimiento.SALIDA &&
                YearMonth.from(it.fecha) == ym &&
                datos.categoriaPorId[it.categoriaId]?.tipo != TipoCategoria.PATRIMONIO
        }.sumOf { it.importeCentavos }

    private fun compraPatrimonioMes(ym: YearMonth): Long =
        datos.movimientos.filter {
            it.tipo == TipoMovimiento.SALIDA &&
                YearMonth.from(it.fecha) == ym &&
                datos.categoriaPorId[it.categoriaId]?.tipo == TipoCategoria.PATRIMONIO
        }.sumOf { it.importeCentavos }

    private fun gastoConsumoMensualPromedio(): Long {
        if (meses.isEmpty()) return 0L
        // El ultimo mes suele estar incompleto; se descarta si hay historial suficiente.
        val considerados = if (meses.size > 1) meses.dropLast(1) else meses
        val total = considerados.sumOf { abs(gastoConsumoMes(it)) }
        return total / considerados.size
    }

    private fun totalTransferencia(nombreCuenta: String, tipo: TipoMovimiento): Long =
        datos.movimientos
            .filter { datos.nombreCuenta(it.cuentaId) == nombreCuenta && it.tipo == tipo }
            .sumOf { it.importeCentavos }

    private fun totalTransferenciasGlobal(tipo: TipoMovimiento): Long =
        datos.movimientos.filter { it.tipo == tipo }.sumOf { it.importeCentavos }
}
