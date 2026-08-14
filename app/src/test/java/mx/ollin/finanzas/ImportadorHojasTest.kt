package mx.ollin.finanzas

import kotlinx.coroutines.test.runTest
import mx.ollin.finanzas.data.db.Categoria
import mx.ollin.finanzas.data.db.Compromiso
import mx.ollin.finanzas.data.db.Cuenta
import mx.ollin.finanzas.data.db.Movimiento
import mx.ollin.finanzas.data.db.Presupuesto
import mx.ollin.finanzas.data.excel.Celda
import mx.ollin.finanzas.data.excel.DatosExportacion
import mx.ollin.finanzas.data.excel.EsquemaExportacion
import mx.ollin.finanzas.data.excel.ExportadorExcel
import mx.ollin.finanzas.data.excel.Hoja
import mx.ollin.finanzas.data.excel.HojaExportable
import mx.ollin.finanzas.data.excel.ImportadorExcel
import mx.ollin.finanzas.data.excel.OpcionesImportacion
import mx.ollin.finanzas.data.excel.ResultadoImportacion
import mx.ollin.finanzas.data.excel.Severidad
import mx.ollin.finanzas.data.excel.XlsxEscritor
import mx.ollin.finanzas.domain.model.Contraparte
import mx.ollin.finanzas.domain.model.Medio
import mx.ollin.finanzas.domain.model.Periodicidad
import mx.ollin.finanzas.domain.model.TipoCategoria
import mx.ollin.finanzas.domain.model.TipoCuenta
import mx.ollin.finanzas.domain.model.TipoMovimiento
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.LocalDate

/**
 * Importacion de las pestañas que no son Registros.
 *
 * Hasta ahora el libro completo salia de la app pero solo volvia a entrar por
 * la hoja de movimientos: el catalogo de cuentas, las metas del mes y los pagos
 * por venir se quedaban en el archivo. Aqui se prueba el viaje de regreso.
 */
class ImportadorHojasTest : BaseEnMemoria() {

    private fun importador() =
        ImportadorExcel(cuentaDao, categoriaDao, movimientoDao, mapeoDao, presupuestoDao, compromisoDao)

    private fun libro(vararg hojas: Hoja): InputStream {
        val salida = ByteArrayOutputStream()
        XlsxEscritor(hojas.toList()).escribeEn(salida)
        return salida.toByteArray().inputStream()
    }

    private fun hoja(nombre: String, vararg filas: List<Celda>) = Hoja(nombre, filas.toList())

    private fun texto(vararg valores: String?): List<Celda> =
        valores.map { if (it.isNullOrBlank()) Celda.Vacia else Celda.Texto(it) }

    private fun hojaRegistros(vararg filas: List<Celda>) = Hoja(
        nombre = "Registros",
        filas = listOf(
            texto("Fecha", "Cantidad", "Cuenta", "Categoria", "Descripcion", "Tipo")
        ) + filas.toList()
    )

    private fun registro(
        fecha: LocalDate = LocalDate.of(2026, 1, 15),
        cantidad: Double,
        cuenta: String = "Banorte",
        categoria: String? = null,
        descripcion: String = "Movimiento",
        tipo: String? = null
    ): List<Celda> = listOf(
        Celda.Fecha(fecha),
        Celda.Numero(cantidad),
        Celda.Texto(cuenta),
        if (categoria == null) Celda.Vacia else Celda.Texto(categoria),
        Celda.Texto(descripcion),
        if (tipo == null) Celda.Vacia else Celda.Texto(tipo)
    )

    private fun ResultadoImportacion.errores() =
        diagnosticos.filter { it.severidad == Severidad.ERROR }

    // ------------------------------------------------------- round trip real

    /**
     * El libro completo que genera la app, de ida y de vuelta. Cubre de una sola
     * vez el formato exacto de Diccionarios, Presupuesto y Compromisos: si
     * alguien mueve una columna en el exportador, la prueba se entera.
     */
    @Test
    fun `el libro completo devuelve catalogo, metas y compromisos`() = runTest {
        val datos = DatosExportacion(
            cuentas = listOf(
                Cuenta(id = 1, nombre = "Banorte", tipo = TipoCuenta.DEBITO),
                Cuenta(id = 2, nombre = "Terreno Ejido", tipo = TipoCuenta.ACTIVO)
            ),
            categorias = listOf(
                Categoria(id = 10, nombre = "Casa", tipo = TipoCategoria.GASTO),
                Categoria(id = 11, nombre = "Luz", padreId = 10, tipo = TipoCategoria.GASTO),
                Categoria(id = 12, nombre = "Muebles", padreId = 10, tipo = TipoCategoria.GASTO),
                Categoria(id = 20, nombre = "Trabajo", tipo = TipoCategoria.INGRESO),
                Categoria(id = 21, nombre = "Salario", padreId = 20, tipo = TipoCategoria.INGRESO)
            ),
            movimientos = listOf(
                mov(1, "2026-01-10", 850871, 1, 21, "Salario", TipoMovimiento.ENTRADA),
                mov(2, "2026-01-16", -59980, 1, 11, "Luz", TipoMovimiento.SALIDA)
            ),
            presupuestos = listOf(
                Presupuesto(id = 1, categoriaId = 11, anio = 2026, mes = 1, montoCentavos = 80000),
                Presupuesto(id = 2, categoriaId = 21, anio = 2026, mes = 1, montoCentavos = 900000)
            ),
            compromisos = listOf(
                Compromiso(
                    id = 1,
                    nombre = "Refrigerador MSI",
                    cuentaId = 1,
                    categoriaId = 12,
                    montoCentavos = 125000,
                    periodicidad = Periodicidad.MENSUAL,
                    fechaPrimerPago = LocalDate.of(2026, 1, 5),
                    totalPagos = 12,
                    pagosRealizados = 2
                ),
                Compromiso(
                    id = 2,
                    nombre = "Streaming",
                    cuentaId = 1,
                    categoriaId = null,
                    montoCentavos = 29900,
                    periodicidad = Periodicidad.MENSUAL,
                    fechaPrimerPago = LocalDate.of(2026, 1, 8),
                    totalPagos = null
                )
            )
        )
        val bytes = ByteArrayOutputStream()
        ExportadorExcel(datos, EsquemaExportacion.EXTENDIDO, HojaExportable.PREDETERMINADAS)
            .escribeEn(bytes)

        val resultado = importador().importa(bytes.toByteArray().inputStream())

        assertEquals(emptyList<Any>(), resultado.errores())
        assertEquals(2, resultado.importados)

        // Diccionarios: entra tambien la cuenta que no tiene ni un movimiento.
        val cuentas = cuentaDao.todas().associateBy { it.nombre }
        assertEquals(setOf("Banorte", "Terreno Ejido"), cuentas.keys)
        assertEquals(TipoCuenta.ACTIVO, cuentas.getValue("Terreno Ejido").tipo)

        // La jerarquia de categorias vuelve: la hoja lista la hija con su grupo.
        val categorias = categoriaDao.todas().associateBy { it.nombre }
        val padre = categorias.getValue("Casa")
        assertNull(padre.padreId)
        assertEquals(padre.id, categorias.getValue("Luz").padreId)
        assertEquals(TipoCategoria.INGRESO, categorias.getValue("Salario").tipo)

        // Presupuesto.
        assertEquals(2, resultado.presupuestosImportados)
        val metas = presupuestoDao.todos().associateBy { categorias.entries.first { c -> c.value.id == it.categoriaId }.key }
        assertEquals(80000L, metas.getValue("Luz").montoCentavos)
        assertEquals(2026, metas.getValue("Luz").anio)
        assertEquals(1, metas.getValue("Luz").mes)

        // Compromisos: la hoja muestra el proximo pago y lo que falta, asi que
        // el compromiso se reconstruye desde ahi.
        assertEquals(2, resultado.compromisosImportados)
        val compromisos = compromisoDao.todos().associateBy { it.nombre }
        val msi = compromisos.getValue("Refrigerador MSI")
        assertEquals(125000L, msi.montoCentavos)
        assertEquals(LocalDate.of(2026, 3, 5), msi.fechaPrimerPago)  // 1er pago + 2 pagados
        assertEquals(10, msi.totalPagos)
        assertEquals(cuentas.getValue("Banorte").id, msi.cuentaId)
        // La hoja lleva la categoria del compromiso, asi que vuelve clasificado.
        assertEquals(categorias.getValue("Muebles").id, msi.categoriaId)
        assertTrue(msi.activo)
        // Una suscripcion no tiene numero de pagos y asi debe volver.
        assertNull(compromisos.getValue("Streaming").totalPagos)
        // El que no traia categoria sigue sin ella, no se le inventa una.
        assertNull(compromisos.getValue("Streaming").categoriaId)
    }

    private fun mov(
        id: Long,
        fecha: String,
        centavos: Long,
        cuentaId: Long,
        categoriaId: Long?,
        descripcion: String,
        tipo: TipoMovimiento
    ) = Movimiento(
        id = id,
        fecha = LocalDate.parse(fecha),
        importeCentavos = centavos,
        cuentaId = cuentaId,
        categoriaId = categoriaId,
        descripcion = descripcion,
        medio = Medio.ELECTRONICO,
        tipo = tipo,
        contraparte = if (tipo.esTransferencia || tipo.esInterno) Contraparte.PROPIA else Contraparte.TERCERO
    )

    // -------------------------------------------------------- Diccionarios

    @Test
    fun `la naturaleza declarada gana sobre la inferida del nombre`() = runTest {
        // "Nomina" sin diccionario seria cuenta de banco; la hoja dice inversion.
        val diccionarios = hoja(
            "Diccionarios",
            texto("Cuentas", "Naturaleza", "Categorias", "Grupo"),
            texto("Nomina", "Inversion", "Gasolina", "Transporte"),
            texto("Guardadito", "Efectivo", null, null)
        )

        val resultado = importador().importa(
            libro(diccionarios, hojaRegistros(registro(cantidad = -500.0, cuenta = "Nomina")))
        )

        assertEquals(emptyList<Any>(), resultado.errores())
        val cuentas = cuentaDao.todas().associateBy { it.nombre }
        assertEquals(TipoCuenta.INVERSION, cuentas.getValue("Nomina").tipo)
        // Una cuenta sin movimientos tambien entra: el catalogo es el catalogo.
        assertEquals(TipoCuenta.EFECTIVO, cuentas.getValue("Guardadito").tipo)

        val categorias = categoriaDao.todas().associateBy { it.nombre }
        assertEquals(categorias.getValue("Transporte").id, categorias.getValue("Gasolina").padreId)
    }

    @Test
    fun `el tipo de la categoria se deduce de como se usa en Registros`() = runTest {
        val diccionarios = hoja(
            "Diccionarios",
            texto("Cuentas", "Naturaleza", "Categorias", "Grupo"),
            texto(null, null, "Salario", "Trabajo"),
            texto(null, null, "Gasolina", "Transporte")
        )

        importador().importa(
            libro(
                diccionarios,
                hojaRegistros(
                    registro(cantidad = 9000.0, categoria = "Salario", descripcion = "Quincena"),
                    registro(cantidad = -500.0, categoria = "Gasolina", descripcion = "Carga")
                )
            )
        )

        val categorias = categoriaDao.todas().associateBy { it.nombre }
        assertEquals(TipoCategoria.INGRESO, categorias.getValue("Salario").tipo)
        assertEquals(TipoCategoria.INGRESO, categorias.getValue("Trabajo").tipo)
        assertEquals(TipoCategoria.GASTO, categorias.getValue("Gasolina").tipo)
    }

    /** El catalogo que ya existe manda: importar no repisa lo que sembro la app. */
    @Test
    fun `no toca las categorias que ya existen`() = runTest {
        val id = nuevaCategoria("Terreno", TipoCategoria.PATRIMONIO)
        val diccionarios = hoja(
            "Diccionarios",
            texto("Cuentas", "Naturaleza", "Categorias", "Grupo"),
            texto(null, null, "Terreno", "Patrimonio")
        )

        importador().importa(
            libro(diccionarios, hojaRegistros(registro(cantidad = -500.0, descripcion = "Pago")))
        )

        val categoria = categoriaDao.porId(id)
        assertNotNull(categoria)
        assertEquals(TipoCategoria.PATRIMONIO, categoria!!.tipo)
        assertNull(categoria.padreId)
    }

    // ---------------------------------------------------------- Presupuesto

    @Test
    fun `lee las metas del formato de la app, con su subtitulo de periodo`() = runTest {
        val gasolina = nuevaCategoria("Gasolina")
        val presupuesto = hoja(
            "Presupuesto",
            texto("Presupuesto contra realidad"),
            texto("Meta positiva. Real en valor absoluto."),
            texto("2026-02"),
            texto("Categoria", "Meta", "Real", "Desviacion", "Avance"),
            listOf(Celda.Texto("Gasolina"), Celda.Numero(1500.0), Celda.Numero(900.0)),
            texto("2026-03"),
            texto("Categoria", "Meta", "Real", "Desviacion", "Avance"),
            listOf(Celda.Texto("Gasolina"), Celda.Numero(1800.0), Celda.Numero(0.0))
        )

        val resultado = importador().importa(
            libro(presupuesto, hojaRegistros(registro(cantidad = -500.0, descripcion = "Carga")))
        )

        assertEquals(2, resultado.presupuestosImportados)
        val metas = presupuestoDao.todos().sortedBy { it.mes }
        assertEquals(listOf(2 to 150000L, 3 to 180000L), metas.map { it.mes to it.montoCentavos })
        assertTrue(metas.all { it.categoriaId == gasolina && it.anio == 2026 })
    }

    @Test
    fun `lee una tabla de presupuesto capturada a mano, con columnas de anio y mes`() = runTest {
        nuevaCategoria("Despensa")
        val presupuesto = hoja(
            "Presupuesto",
            texto("Categoria", "Monto", "Anio", "Mes"),
            listOf(Celda.Texto("Despensa"), Celda.Numero(4200.0), Celda.Numero(2026.0), Celda.Numero(5.0))
        )

        val resultado = importador().importa(
            libro(presupuesto, hojaRegistros(registro(cantidad = -500.0, descripcion = "Super")))
        )

        assertEquals(1, resultado.presupuestosImportados)
        val meta = presupuestoDao.todos().single()
        assertEquals(2026, meta.anio)
        assertEquals(5, meta.mes)
        assertEquals(420000L, meta.montoCentavos)
    }

    @Test
    fun `una meta de una categoria que no existe se avisa en vez de inventarla`() = runTest {
        val presupuesto = hoja(
            "Presupuesto",
            texto("Categoria", "Meta", "Mes"),
            listOf(Celda.Texto("Vacaciones"), Celda.Numero(5000.0), Celda.Texto("2026-07"))
        )

        val resultado = importador().importa(
            libro(presupuesto, hojaRegistros(registro(cantidad = -500.0, descripcion = "Pago")))
        )

        assertEquals(0, resultado.presupuestosImportados)
        assertEquals(0, presupuestoDao.todos().size)
        assertEquals(0, categoriaDao.todas().count { it.nombre == "Vacaciones" })
        assertTrue(resultado.diagnosticos.any { it.severidad == Severidad.AVISO })
    }

    // ---------------------------------------------------------- Compromisos

    @Test
    fun `salta el renglon de total y el que dice indefinido`() = runTest {
        val compromisos = hoja(
            "Compromisos",
            texto("Compromisos por venir"),
            texto("Compromiso", "Cuenta", "Monto", "Periodicidad", "Proximo pago", "Pagos restantes"),
            listOf(
                Celda.Texto("Seguro auto"), Celda.Texto("Banorte"), Celda.Numero(3200.0),
                Celda.Texto("Anual"), Celda.Fecha(LocalDate.of(2026, 6, 1)), Celda.Numero(3.0)
            ),
            listOf(
                Celda.Texto("Gimnasio"), Celda.Vacia, Celda.Numero(450.0),
                Celda.Texto("Mensual"), Celda.Fecha(LocalDate.of(2026, 2, 3)), Celda.Texto("indefinido")
            ),
            listOf(
                Celda.Texto("Total comprometido"), Celda.Vacia, Celda.Vacia,
                Celda.Vacia, Celda.Vacia, Celda.Vacia
            )
        )

        val resultado = importador().importa(
            libro(compromisos, hojaRegistros(registro(cantidad = -500.0, descripcion = "Pago")))
        )

        assertEquals(2, resultado.compromisosImportados)
        val porNombre = compromisoDao.todos().associateBy { it.nombre }
        val seguro = porNombre.getValue("Seguro auto")
        assertEquals(Periodicidad.ANUAL, seguro.periodicidad)
        assertEquals(320000L, seguro.montoCentavos)
        assertEquals(LocalDate.of(2026, 6, 1), seguro.fechaPrimerPago)
        assertEquals(3, seguro.totalPagos)
        assertNotNull(seguro.cuentaId)
        // Sin numero de pagos es una suscripcion: indefinida y sin cuenta atada.
        assertNull(porNombre.getValue("Gimnasio").totalPagos)
        assertNull(porNombre.getValue("Gimnasio").cuentaId)
    }

    /** La categoria del compromiso se resuelve contra el catalogo; no se inventa. */
    @Test
    fun `un compromiso que nombra una categoria inexistente entra sin ella y se avisa`() = runTest {
        val resultado = importador().importa(
            libro(
                hoja(
                    "Compromisos",
                    texto("Compromiso", "Cuenta", "Categoria", "Monto", "Proximo pago", "Pagos restantes"),
                    listOf(
                        Celda.Texto("Seguro auto"), Celda.Vacia, Celda.Texto("Seguros"),
                        Celda.Numero(3200.0), Celda.Fecha(LocalDate.of(2026, 6, 1)), Celda.Numero(3.0)
                    )
                ),
                hojaRegistros(registro(cantidad = -500.0, descripcion = "Pago"))
            )
        )

        assertEquals(1, resultado.compromisosImportados)
        assertNull(compromisoDao.todos().single().categoriaId)
        assertEquals(0, categoriaDao.todas().count { it.nombre == "Seguros" })
        assertTrue(
            resultado.diagnosticos.any {
                it.severidad == Severidad.AVISO && it.mensaje.contains("categoria que no existe")
            }
        )
    }

    @Test
    fun `importar dos veces sin reemplazar no duplica los compromisos`() = runTest {
        val compromisos = {
            hoja(
                "Compromisos",
                texto("Compromiso", "Monto", "Proximo pago", "Pagos restantes"),
                listOf(
                    Celda.Texto("Seguro auto"), Celda.Numero(3200.0),
                    Celda.Fecha(LocalDate.of(2026, 6, 1)), Celda.Numero(3.0)
                )
            )
        }
        val opciones = OpcionesImportacion(reemplazarTodo = false)
        val registros = { hojaRegistros(registro(cantidad = -500.0, descripcion = "Pago")) }

        importador().importa(libro(compromisos(), registros()), opciones)
        val segunda = importador().importa(libro(compromisos(), registros()), opciones)

        assertEquals(0, segunda.compromisosImportados)
        assertEquals(1, compromisoDao.todos().size)
        assertTrue(segunda.diagnosticos.any { it.severidad == Severidad.AVISO })
    }

    /**
     * "Reemplazar todo" alcanza a metas y compromisos, pero solo si el libro
     * trae esas pestañas: borrar lo que el archivo no puede reponer seria
     * perder datos a cambio de nada.
     */
    @Test
    fun `reemplazar solo vacia lo que el libro puede reponer`() = runTest {
        val gasolina = nuevaCategoria("Gasolina")
        presupuestoDao.guarda(
            Presupuesto(categoriaId = gasolina, anio = 2025, mes = 12, montoCentavos = 100000)
        )
        compromisoDao.inserta(
            Compromiso(
                nombre = "Viejo",
                cuentaId = null,
                categoriaId = null,
                montoCentavos = 50000,
                fechaPrimerPago = LocalDate.of(2025, 1, 1)
            )
        )

        val resultado = importador().importa(
            libro(
                hoja(
                    "Compromisos",
                    texto("Compromiso", "Monto", "Proximo pago", "Pagos restantes"),
                    listOf(
                        Celda.Texto("Nuevo"), Celda.Numero(700.0),
                        Celda.Fecha(LocalDate.of(2026, 4, 1)), Celda.Numero(6.0)
                    )
                ),
                hojaRegistros(registro(cantidad = -500.0, descripcion = "Carga"))
            )
        )

        assertEquals(1, resultado.compromisosImportados)
        // El libro traia Compromisos: los viejos se van.
        assertEquals(listOf("Nuevo"), compromisoDao.todos().map { it.nombre })
        // No traia Presupuesto: la meta de diciembre sigue ahi.
        assertEquals(1, presupuestoDao.todos().size)
    }

    // ------------------------------------------------- libro sin movimientos

    @Test
    fun `un libro de puros catalogos entra sin borrar los movimientos`() = runTest {
        val banorte = nuevaCuenta("Banorte")
        nuevoMovimiento(banorte, -1000, TipoMovimiento.SALIDA, "Ya estaba")

        val resultado = importador().importa(
            libro(
                hoja(
                    "Diccionarios",
                    texto("Cuentas", "Naturaleza"),
                    texto("Cartera", "Efectivo")
                ),
                hoja(
                    "Compromisos",
                    texto("Compromiso", "Monto", "Proximo pago", "Pagos restantes"),
                    listOf(
                        Celda.Texto("Predial"), Celda.Numero(1200.0),
                        Celda.Fecha(LocalDate.of(2026, 12, 1)), Celda.Numero(1.0)
                    )
                )
            )
        )

        // Sin hoja de movimientos no es un error: es un libro de catalogos.
        assertEquals(emptyList<Any>(), resultado.errores())
        assertEquals(1, movimientoDao.cuenta())
        assertEquals(listOf("Cartera"), resultado.cuentasCreadas)
        assertEquals(1, resultado.compromisosImportados)
        assertTrue(resultado.diagnosticos.any { it.severidad == Severidad.AVISO })
    }

    // --------------------------------------------- limpieza al reemplazar

    /**
     * El catalogo de ejemplo que siembra la app en el primer arranque no
     * sobrevive a un "reemplazar todo": sin movimientos detras no describe nada
     * y solo estorba en los desplegables.
     */
    @Test
    fun `reemplazar se lleva el catalogo que queda sin uso`() = runTest {
        val cartera = nuevaCuenta("Cartera", TipoCuenta.EFECTIVO)
        nuevaCuenta("Tarjeta de ejemplo", TipoCuenta.CREDITO)
        val despensa = nuevaCategoria("Despensa")
        nuevaCategoria("Mascotas")
        nuevoMovimiento(cartera, -1200, TipoMovimiento.SALIDA, "Super", despensa)
        nuevoMapeo("super", despensa)

        val resultado = importador().importa(
            libro(
                hojaRegistros(
                    registro(cantidad = -800.0, cuenta = "Banorte", categoria = "Gasolina", descripcion = "Carga")
                )
            )
        )

        // Queda exactamente lo que el archivo describe.
        assertEquals(listOf("Banorte"), cuentaDao.todas().map { it.nombre })
        assertEquals(listOf("Gasolina"), categoriaDao.todas().map { it.nombre })
        assertEquals(listOf("Cartera", "Tarjeta de ejemplo"), resultado.cuentasEliminadas.sorted())
        assertEquals(2, resultado.categoriasEliminadas)
        // El mapeo a una categoria borrada se va con ella: si no, la proxima
        // captura de "Super" apuntaria a una categoria que ya no existe.
        assertEquals(0, mapeoDao.cuenta())
    }

    @Test
    fun `no borra el catalogo que todavia sostiene algo ni el que el libro nombra`() = runTest {
        val diccionarios = hoja(
            "Diccionarios",
            texto("Cuentas", "Naturaleza", "Categorias", "Grupo"),
            texto("Guardadito", "Efectivo", null, null)
        )
        val gasolina = nuevaCategoria("Gasolina")
        presupuestoDao.guarda(
            Presupuesto(categoriaId = gasolina, anio = 2026, mes = 1, montoCentavos = 150000)
        )

        val resultado = importador().importa(
            libro(
                diccionarios,
                hojaRegistros(
                    registro(cantidad = -800.0, cuenta = "Banorte", categoria = "Despensa", descripcion = "Super")
                )
            )
        )

        // "Guardadito" no tiene un solo movimiento, pero viene en Diccionarios:
        // el catalogo es el catalogo.
        assertEquals(setOf("Banorte", "Guardadito"), cuentaDao.todas().map { it.nombre }.toSet())
        // "Gasolina" no aparece en el libro, pero tiene una meta colgando.
        assertEquals(setOf("Despensa", "Gasolina"), categoriaDao.todas().map { it.nombre }.toSet())
        assertEquals(emptyList<String>(), resultado.cuentasEliminadas)
        assertEquals(0, resultado.categoriasEliminadas)
    }

    /** Un padre que todavia agrupa a una categoria viva se queda, aunque el no se use. */
    @Test
    fun `conserva el grupo de una categoria que sobrevive`() = runTest {
        val resultado = importador().importa(
            libro(
                hoja(
                    "Diccionarios",
                    texto("Cuentas", "Naturaleza", "Categorias", "Grupo"),
                    texto(null, null, "Gasolina", "Transporte")
                ),
                hojaRegistros(
                    registro(cantidad = -800.0, cuenta = "Banorte", categoria = "Gasolina", descripcion = "Carga")
                )
            )
        )

        val categorias = categoriaDao.todas().associateBy { it.nombre }
        assertEquals(setOf("Gasolina", "Transporte"), categorias.keys)
        assertEquals(categorias.getValue("Transporte").id, categorias.getValue("Gasolina").padreId)
        assertEquals(0, resultado.categoriasEliminadas)
    }

    /**
     * El modo compacto no exporta categorias. Vaciar el catalogo entero por eso
     * dejaria la captura sin donde clasificar, y la siembra del arranque las
     * repondria igual.
     */
    @Test
    fun `un libro sin categorias no desmantela el catalogo`() = runTest {
        nuevaCategoria("Despensa")
        nuevaCategoria("Mascotas")

        val resultado = importador().importa(
            libro(hojaRegistros(registro(cantidad = -800.0, cuenta = "Banorte", descripcion = "Compra")))
        )

        assertEquals(2, categoriaDao.todas().size)
        assertEquals(0, resultado.categoriasEliminadas)
        assertTrue(resultado.diagnosticos.any { it.severidad == Severidad.INFO })
    }

    @Test
    fun `sin reemplazar el catalogo se queda completo`() = runTest {
        nuevaCuenta("Cartera", TipoCuenta.EFECTIVO)
        nuevaCategoria("Mascotas")

        val resultado = importador().importa(
            libro(
                hojaRegistros(
                    registro(cantidad = -800.0, cuenta = "Banorte", categoria = "Gasolina", descripcion = "Carga")
                )
            ),
            OpcionesImportacion(reemplazarTodo = false)
        )

        assertEquals(setOf("Banorte", "Cartera"), cuentaDao.todas().map { it.nombre }.toSet())
        assertEquals(setOf("Gasolina", "Mascotas"), categoriaDao.todas().map { it.nombre }.toSet())
        assertEquals(emptyList<String>(), resultado.cuentasEliminadas)
    }

    // ------------------------------------------------- avisos de la importacion

    /**
     * Los avisos se cuentan una vez por mensaje, con los renglones que los
     * provocaron: un libro con veinte filas rotas devolvia veinte lineas
     * identicas y ninguna se alcanzaba a leer.
     */
    @Test
    fun `los avisos repetidos se agrupan con sus renglones`() = runTest {
        val resultado = importador().importa(
            libro(
                hojaRegistros(
                    registro(cantidad = -800.0, cuenta = "Banorte", descripcion = "Carga"),
                    texto(null, null, null, null, "Sin fecha ni importe"),
                    texto(null, null, null, null, "Otra rota")
                )
            )
        )

        val incompletos = resultado.diagnosticosAgrupados()
            .single { it.mensaje.startsWith("Renglon incompleto") }
        assertEquals(Severidad.AVISO, incompletos.severidad)
        assertEquals(2, incompletos.veces)
        assertEquals(listOf(3, 4), incompletos.filas)
    }

    @Test
    fun `apagar la lectura de otras hojas deja fuera metas y compromisos`() = runTest {
        nuevaCategoria("Gasolina")
        val resultado = importador().importa(
            libro(
                hoja(
                    "Presupuesto",
                    texto("Categoria", "Meta", "Mes"),
                    listOf(Celda.Texto("Gasolina"), Celda.Numero(1500.0), Celda.Texto("2026-02"))
                ),
                hojaRegistros(registro(cantidad = -500.0, descripcion = "Carga"))
            ),
            OpcionesImportacion(importarOtrasHojas = false)
        )

        assertEquals(1, resultado.importados)
        assertEquals(0, resultado.presupuestosImportados)
        assertEquals(0, presupuestoDao.todos().size)
    }
}
