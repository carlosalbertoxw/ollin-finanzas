package mx.ollin.finanzas

import kotlinx.coroutines.test.runTest
import mx.ollin.finanzas.data.db.Categoria
import mx.ollin.finanzas.data.db.Cuenta
import mx.ollin.finanzas.data.db.Movimiento
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
 * Hasta ahora solo estaba probado el escritor de xlsx. Aqui se prueba el lado
 * contrario, que es el que decide cosas por ti: corrige tipos, empareja
 * transferencias, inventa cuentas y recalcula contrapartes. Cada una de esas
 * decisiones se toma sobre datos que el usuario ya no revisa.
 */
class ImportadorExcelTest : BaseEnMemoria() {

    private fun importador() =
        ImportadorExcel(cuentaDao, categoriaDao, movimientoDao, mapeoDao, presupuestoDao, compromisoDao)

    private val encabezadosCompletos = listOf(
        "Fecha", "Cantidad", "Cuenta", "Categoria", "Descripcion", "Medio", "Contraparte", "Tipo"
    )

    private fun celda(valor: Any?): Celda = when (valor) {
        null -> Celda.Vacia
        is LocalDate -> Celda.Fecha(valor)
        is Double -> Celda.Numero(valor)
        is Int -> Celda.Numero(valor.toDouble())
        is String -> if (valor.isBlank()) Celda.Vacia else Celda.Texto(valor)
        else -> Celda.Texto(valor.toString())
    }

    private fun fila(
        fecha: Any? = LocalDate.of(2026, 1, 15),
        cantidad: Any? = null,
        cuenta: Any? = "Banorte",
        categoria: Any? = null,
        descripcion: Any? = null,
        medio: Any? = null,
        contraparte: Any? = null,
        tipo: Any? = null
    ): List<Celda> = listOf(
        celda(fecha), celda(cantidad), celda(cuenta), celda(categoria),
        celda(descripcion), celda(medio), celda(contraparte), celda(tipo)
    )

    /** Arma un .xlsx real en memoria con el mismo escritor que usa la app. */
    private fun libro(
        filas: List<List<Celda>>,
        encabezados: List<String> = encabezadosCompletos,
        nombreHoja: String = "Registros"
    ): InputStream {
        val hoja = Hoja(
            nombre = nombreHoja,
            filas = listOf(encabezados.map { Celda.Texto(it) }) + filas
        )
        val salida = ByteArrayOutputStream()
        XlsxEscritor(listOf(hoja)).escribeEn(salida)
        return salida.toByteArray().inputStream()
    }

    private suspend fun importa(
        filas: List<List<Celda>>,
        encabezados: List<String> = encabezadosCompletos,
        opciones: OpcionesImportacion = OpcionesImportacion()
    ): ResultadoImportacion = importador().importa(libro(filas, encabezados), opciones)

    private fun ResultadoImportacion.errores() =
        diagnosticos.filter { it.severidad == Severidad.ERROR }

    // ------------------------------------------------------------ round trip

    /**
     * La prueba que mas cubre de una sola vez: lo que exporta la app tiene que
     * poder volver a entrar. Si alguien cambia un encabezado en el exportador,
     * es aqui donde se entera.
     */
    @Test
    fun `lo que exporta la app se vuelve a importar sin perdida`() = runTest {
        val datos = DatosExportacion(
            cuentas = listOf(
                Cuenta(id = 1, nombre = "Banorte", tipo = TipoCuenta.DEBITO),
                Cuenta(id = 2, nombre = "Cartera", tipo = TipoCuenta.EFECTIVO, medioPorDefecto = Medio.EFECTIVO)
            ),
            categorias = listOf(
                Categoria(id = 11, nombre = "Gasolina", tipo = TipoCategoria.GASTO),
                Categoria(id = 21, nombre = "Salario", tipo = TipoCategoria.INGRESO)
            ),
            movimientos = listOf(
                movExport(1, "2026-01-01", 409194, 1, null, "Balance Inicial", TipoMovimiento.BALANCE_INICIAL),
                movExport(2, "2026-01-15", 850871, 1, 21, "Salario", TipoMovimiento.ENTRADA),
                movExport(3, "2026-01-16", -59980, 1, 11, "Gasolina", TipoMovimiento.SALIDA),
                movExport(4, "2026-01-20", -350000, 1, null, "Traspaso a cartera", TipoMovimiento.TRANSFERENCIA_SALIDA, "g1"),
                movExport(5, "2026-01-20", 350000, 2, null, "Traspaso a cartera", TipoMovimiento.TRANSFERENCIA_ENTRADA, "g1")
            ),
            presupuestos = emptyList(),
            compromisos = emptyList()
        )
        val bytes = ByteArrayOutputStream()
        ExportadorExcel(datos, EsquemaExportacion.EXTENDIDO, HojaExportable.PREDETERMINADAS)
            .escribeEn(bytes)

        val resultado = importador().importa(bytes.toByteArray().inputStream())

        assertEquals(emptyList<Any>(), resultado.errores())
        assertEquals(5, resultado.importados)
        assertEquals(5, movimientoDao.cuenta())
        assertTrue(resultado.cuentasCreadas.containsAll(listOf("Banorte", "Cartera")))
        // El par de transferencia se reconoce solo, sin el uuid original.
        assertEquals(1, resultado.transferenciasEmparejadas)
    }

    private fun movExport(
        id: Long,
        fecha: String,
        centavos: Long,
        cuentaId: Long,
        categoriaId: Long?,
        descripcion: String,
        tipo: TipoMovimiento,
        grupo: String? = null
    ) = Movimiento(
        id = id,
        fecha = LocalDate.parse(fecha),
        importeCentavos = centavos,
        cuentaId = cuentaId,
        categoriaId = categoriaId,
        descripcion = descripcion,
        medio = Medio.ELECTRONICO,
        tipo = tipo,
        contraparte = if (tipo.esTransferencia || tipo.esInterno) Contraparte.PROPIA else Contraparte.TERCERO,
        grupoTransferencia = grupo
    )

    // ------------------------------------------------------ lectura de hoja

    @Test
    fun `un libro sin fecha, cantidad y cuenta se rechaza con un error`() = runTest {
        val resultado = importa(
            filas = listOf(listOf(Celda.Texto("Ana"), Celda.Texto("555"))),
            encabezados = listOf("Nombre", "Telefono")
        )

        assertEquals(0, resultado.importados)
        assertEquals(1, resultado.errores().size)
        assertTrue(resultado.huboProblemas)
    }

    @Test
    fun `reconoce los sinonimos de encabezado y los acentos`() = runTest {
        val resultado = importa(
            filas = listOf(
                listOf(
                    celda(LocalDate.of(2026, 1, 15)), celda(-500.0), celda("Banorte"),
                    celda("Gasolina"), celda("Carga de gasolina")
                )
            ),
            encabezados = listOf("Fecha", "Importe", "Cuenta", "Categoría", "Concepto")
        )

        assertEquals(1, resultado.importados)
        val movimiento = movimientoDao.todos().single()
        assertEquals(-50000L, movimiento.importeCentavos)
        assertEquals("Carga de gasolina", movimiento.descripcion)
        assertNotNull(movimiento.categoriaId)
    }

    @Test
    fun `omite el renglon incompleto y lo reporta con su numero de fila`() = runTest {
        val resultado = importa(
            listOf(
                fila(cantidad = -500.0, descripcion = "Gasolina"),
                // Sin cuenta: no hay a que colgarlo.
                fila(cantidad = -300.0, cuenta = null, descripcion = "Huerfano")
            )
        )

        assertEquals(1, resultado.importados)
        assertEquals(1, resultado.omitidos)
        // Se busca por numero de fila: el resumen de "sin categoria" tambien
        // llega como AVISO, pero ese no senala ningun renglon.
        val aviso = resultado.diagnosticos.single { it.fila != null }
        assertEquals(3, aviso.fila)
        assertEquals(Severidad.AVISO, aviso.severidad)
    }

    // -------------------------------------------------------- saneamiento

    @Test
    fun `corrige el tipo contra el signo y deja el importe intacto`() = runTest {
        val resultado = importa(
            listOf(fila(cantidad = -500.0, descripcion = "Reembolso", tipo = "Entrada"))
        )

        assertEquals(1, resultado.tiposCorregidos)
        val movimiento = movimientoDao.todos().single()
        assertEquals(TipoMovimiento.SALIDA, movimiento.tipo)
        assertEquals(-50000L, movimiento.importeCentavos)
    }

    @Test
    fun `empareja las dos patas de una transferencia`() = runTest {
        val resultado = importa(
            listOf(
                fila(cantidad = -500.0, cuenta = "Banorte", descripcion = "Traspaso", tipo = "Transferencia Salida"),
                fila(cantidad = 500.0, cuenta = "Cartera", descripcion = "Traspaso", tipo = "Transferencia Entrada")
            )
        )

        assertEquals(1, resultado.transferenciasEmparejadas)
        assertEquals(0, resultado.transferenciasHuerfanas)
        val grupos = movimientoDao.todos().map { it.grupoTransferencia }
        assertEquals(1, grupos.filterNotNull().distinct().size)
        assertEquals(2, grupos.filterNotNull().size)
    }

    @Test
    fun `una pata sola se reporta como huerfana`() = runTest {
        val resultado = importa(
            listOf(fila(cantidad = -500.0, descripcion = "Traspaso", tipo = "Transferencia Salida"))
        )

        assertEquals(0, resultado.transferenciasEmparejadas)
        assertEquals(1, resultado.transferenciasHuerfanas)
        assertNull(movimientoDao.todos().single().grupoTransferencia)
    }

    /**
     * La contraparte se deriva del tipo y no se cree lo que traiga el archivo:
     * es un campo que se captura a mano y se deja de mantener enseguida.
     */
    @Test
    fun `recalcula la contraparte contra lo que decia el archivo y lo informa`() = runTest {
        val resultado = importa(
            listOf(
                fila(
                    cantidad = -500.0, descripcion = "Traspaso",
                    contraparte = "2", tipo = "Transferencia Salida"
                )
            )
        )

        assertEquals(1, resultado.contrapartesRecalculadas)
        assertEquals(Contraparte.PROPIA, movimientoDao.todos().single().contraparte)
        assertTrue(resultado.diagnosticos.any { it.severidad == Severidad.INFO })
    }

    // --------------------------------------------------------- inferencias

    @Test
    fun `crea las cuentas que faltan infiriendo el tipo por el nombre`() = runTest {
        importa(
            listOf(
                fila(cantidad = -100.0, cuenta = "Tarjeta Oro", descripcion = "Compra"),
                fila(cantidad = -200.0, cuenta = "Prestamo a Juan", descripcion = "Prestamo"),
                fila(cantidad = -300.0, cuenta = "Cartera", descripcion = "Tortilla"),
                fila(cantidad = -400.0, cuenta = "Terreno", descripcion = "Escrituras"),
                fila(cantidad = -500.0, cuenta = "Nomina MSI", descripcion = "Mensualidad")
            )
        )

        val porNombre = cuentaDao.todas().associateBy { it.nombre }
        assertEquals(TipoCuenta.CREDITO, porNombre.getValue("Tarjeta Oro").tipo)
        // "Prestamo a Juan" es dinero que va a volver: por cobrar, no deuda.
        assertEquals(TipoCuenta.ACTIVO, porNombre.getValue("Prestamo a Juan").tipo)
        assertEquals(TipoCuenta.EFECTIVO, porNombre.getValue("Cartera").tipo)
        assertEquals(TipoCuenta.ACTIVO, porNombre.getValue("Terreno").tipo)
        assertEquals(TipoCuenta.CREDITO_MSI, porNombre.getValue("Nomina MSI").tipo)
    }

    @Test
    fun `solo lo que parece tarjeta queda atado al medio electronico`() = runTest {
        importa(
            listOf(
                fila(cantidad = -100.0, cuenta = "Tarjeta Oro", descripcion = "Compra"),
                fila(cantidad = -200.0, cuenta = "Prestamo Afirme", descripcion = "Pago")
            )
        )

        val porNombre = cuentaDao.todas().associateBy { it.nombre }
        assertTrue(porNombre.getValue("Tarjeta Oro").soloElectronico)
        // Un prestamo comparte tipo con la tarjeta, pero si recibe dinero en mano.
        assertEquals(TipoCuenta.CREDITO, porNombre.getValue("Prestamo Afirme").tipo)
        assertTrue(!porNombre.getValue("Prestamo Afirme").soloElectronico)
    }

    @Test
    fun `sin columna de tipo lo deduce de la descripcion y del signo`() = runTest {
        importa(
            filas = listOf(
                listOf(celda(LocalDate.of(2026, 1, 1)), celda(1000.0), celda("Banorte"), celda("Balance Inicial")),
                listOf(celda(LocalDate.of(2026, 1, 2)), celda(500.0), celda("Banorte"), celda("Revaluacion terreno")),
                listOf(celda(LocalDate.of(2026, 1, 3)), celda(-500.0), celda("Banorte"), celda("Traspaso")),
                listOf(celda(LocalDate.of(2026, 1, 4)), celda(-200.0), celda("Banorte"), celda("Pago servicios")),
                listOf(celda(LocalDate.of(2026, 1, 5)), celda(900.0), celda("Banorte"), celda("Sueldo"))
            ),
            encabezados = listOf("Fecha", "Cantidad", "Cuenta", "Descripcion")
        )

        val porDescripcion = movimientoDao.todos().associate { it.descripcion to it.tipo }
        assertEquals(TipoMovimiento.BALANCE_INICIAL, porDescripcion["Balance Inicial"])
        assertEquals(TipoMovimiento.AJUSTE_VALOR, porDescripcion["Revaluacion terreno"])
        assertEquals(TipoMovimiento.TRANSFERENCIA_SALIDA, porDescripcion["Traspaso"])
        assertEquals(TipoMovimiento.SALIDA, porDescripcion["Pago servicios"])
        assertEquals(TipoMovimiento.ENTRADA, porDescripcion["Sueldo"])
    }

    @Test
    fun `acepta el serial de Excel y los formatos de fecha declarados`() = runTest {
        val esperada = LocalDate.of(2026, 1, 15)
        listOf<Any>(esperada, "2026-01-15", "15/01/2026", "15-01-2026").forEach { valor ->
            movimientoDao.eliminaTodos()
            val resultado = importa(listOf(fila(fecha = valor, cantidad = -500.0, descripcion = "Gasolina")))
            assertEquals("Fallo con $valor", 1, resultado.importados)
            assertEquals("Fallo con $valor", esperada, movimientoDao.todos().single().fecha)
        }
    }

    // ------------------------------------------------------------ opciones

    @Test
    fun `reemplazar todo en falso agrega en vez de vaciar`() = runTest {
        val banorte = nuevaCuenta("Banorte")
        nuevoMovimiento(banorte, -1000, TipoMovimiento.SALIDA, "Ya estaba")

        importa(
            filas = listOf(
                fila(cantidad = -500.0, descripcion = "Nuevo uno"),
                fila(cantidad = -300.0, descripcion = "Nuevo dos")
            ),
            opciones = OpcionesImportacion(reemplazarTodo = false)
        )

        assertEquals(3, movimientoDao.cuenta())
    }

    @Test
    fun `el balance inicial nunca recibe categoria aunque la fila traiga una`() = runTest {
        importa(
            listOf(
                fila(
                    cantidad = 1000.0, categoria = "Ahorros",
                    descripcion = "Balance Inicial", tipo = "Balance Inicial"
                )
            )
        )

        assertNull(movimientoDao.todos().single().categoriaId)
    }
}
