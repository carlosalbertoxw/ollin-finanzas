package com.carlosalbertoxw.ollin.finanzas

import com.carlosalbertoxw.ollin.finanzas.data.db.Categoria
import com.carlosalbertoxw.ollin.finanzas.data.db.Cuenta
import com.carlosalbertoxw.ollin.finanzas.data.db.Movimiento
import com.carlosalbertoxw.ollin.finanzas.data.db.Presupuesto
import com.carlosalbertoxw.ollin.finanzas.data.excel.DatosExportacion
import com.carlosalbertoxw.ollin.finanzas.data.excel.EsquemaExportacion
import com.carlosalbertoxw.ollin.finanzas.data.excel.ExportadorExcel
import com.carlosalbertoxw.ollin.finanzas.data.excel.HojaExportable
import com.carlosalbertoxw.ollin.finanzas.data.excel.Ooxml
import com.carlosalbertoxw.ollin.finanzas.data.excel.XlsxLector
import com.carlosalbertoxw.ollin.finanzas.domain.model.Contraparte
import com.carlosalbertoxw.ollin.finanzas.domain.model.Dinero
import com.carlosalbertoxw.ollin.finanzas.domain.model.Medio
import com.carlosalbertoxw.ollin.finanzas.domain.model.TipoCategoria
import com.carlosalbertoxw.ollin.finanzas.domain.model.TipoCuenta
import com.carlosalbertoxw.ollin.finanzas.domain.model.TipoMovimiento
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * El escritor de xlsx es codigo propio, asi que necesita prueba propia.
 * Los archivos quedan en build/pruebas para poder abrirlos a mano.
 */
class ExcelRoundTripTest {

    private val salida = File("build/pruebas").apply { mkdirs() }

    private fun datosDeMuestra(): DatosExportacion {
        val cuentas = listOf(
            Cuenta(id = 1, nombre = "Banorte", tipo = TipoCuenta.DEBITO),
            Cuenta(id = 2, nombre = "Cartera", tipo = TipoCuenta.EFECTIVO, medioPorDefecto = Medio.EFECTIVO),
            Cuenta(id = 3, nombre = "Tarjeta Credito Plata", tipo = TipoCuenta.CREDITO),
            Cuenta(id = 4, nombre = "Terreno", tipo = TipoCuenta.ACTIVO)
        )
        val categorias = listOf(
            Categoria(id = 10, nombre = "Auto", tipo = TipoCategoria.GASTO),
            Categoria(id = 11, nombre = "Gasolina", padreId = 10, tipo = TipoCategoria.GASTO),
            Categoria(id = 20, nombre = "Sueldo", tipo = TipoCategoria.INGRESO),
            Categoria(id = 21, nombre = "Salario", padreId = 20, tipo = TipoCategoria.INGRESO),
            Categoria(id = 30, nombre = "Inmuebles", tipo = TipoCategoria.PATRIMONIO),
            Categoria(id = 31, nombre = "Construccion Terreno", padreId = 30, tipo = TipoCategoria.PATRIMONIO)
        )
        val movimientos = listOf(
            mov(1, "2026-01-01", 409194, 1, null, "Balance Inicial", TipoMovimiento.BALANCE_INICIAL),
            mov(2, "2026-01-15", 850871, 1, 21, "Salario", TipoMovimiento.ENTRADA),
            mov(3, "2026-01-16", -59980, 3, 11, "Gasolina", TipoMovimiento.SALIDA),
            mov(4, "2026-02-15", 853357, 1, 21, "Salario", TipoMovimiento.ENTRADA),
            mov(5, "2026-02-18", -117600, 3, 11, "Gasolina", TipoMovimiento.SALIDA),
            // Un caso con acentos y comillas, para probar el escapado del XML.
            mov(6, "2026-02-20", -25000, 2, 11, "Gasolina \"Pemex\" & más <ruta>", TipoMovimiento.SALIDA),
            mov(7, "2026-03-10", -1000000, 1, 31, "Construccion Terreno", TipoMovimiento.SALIDA),
            mov(8, "2026-03-11", -350000, 1, null, "Transferencia entre cuentas", TipoMovimiento.TRANSFERENCIA_SALIDA, "g1"),
            mov(9, "2026-03-11", 350000, 2, null, "Transferencia entre cuentas", TipoMovimiento.TRANSFERENCIA_ENTRADA, "g1")
        )
        return DatosExportacion(
            cuentas = cuentas,
            categorias = categorias,
            movimientos = movimientos,
            presupuestos = listOf(Presupuesto(1, 11, 2026, 2, 150000)),
            compromisos = emptyList()
        )
    }

    private fun mov(
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
        contraparte = if (tipo.esTransferencia || tipo == TipoMovimiento.BALANCE_INICIAL)
            Contraparte.PROPIA else Contraparte.TERCERO,
        grupoTransferencia = grupo
    )

    @Test
    fun `el serial de fecha coincide con el que espera Excel`() {
        // 2026-01-01 es el serial 46023. Si esto cambia, todas las fechas
        // exportadas se corren.
        assertEquals(46023L, Ooxml.aSerial(LocalDate.of(2026, 1, 1)))
        assertEquals(LocalDate.of(2026, 1, 1), Ooxml.desdeSerial(46023.0))
    }

    @Test
    fun `las letras de columna van y vienen`() {
        assertEquals("A", Ooxml.letraColumna(1))
        assertEquals("Z", Ooxml.letraColumna(26))
        assertEquals("AA", Ooxml.letraColumna(27))
        assertEquals("BC", Ooxml.letraColumna(55))
        listOf(1, 26, 27, 55, 702, 703).forEach {
            assertEquals(it, Ooxml.indiceColumna(Ooxml.letraColumna(it)))
        }
    }

    @Test
    fun `los importes en centavos no acumulan error`() {
        assertEquals(100000L, Dinero.parsea("1,000.00"))
        assertEquals(100000L, Dinero.parsea("$1 000"))
        assertEquals(-45533L, Dinero.parsea("-455.33"))
        assertEquals(-45533L, Dinero.parsea("(455.33)"))
        assertEquals(123456L, Dinero.parsea("1234,56"))
        // En punto flotante esta suma daria 999.999999999996 en vez de cero.
        val piezas = listOf(409194L, -547080L, 137886L)
        assertEquals(0L, piezas.sum())
    }

    @Test
    fun `el libro extendido se escribe y se vuelve a leer`() {
        val datos = datosDeMuestra()
        val archivo = File(salida, "ollin-extendido.xlsx")
        archivo.outputStream().use {
            ExportadorExcel(datos, EsquemaExportacion.EXTENDIDO, HojaExportable.PREDETERMINADAS)
                .escribeEn(it)
        }
        assertTrue("El archivo debe existir y pesar algo", archivo.length() > 2000)

        val libro = archivo.inputStream().use(XlsxLector::lee)
        assertEquals(
            listOf(
                "Balance", "Ingresos - Egresos", "Presupuesto",
                "Transferencias", "Compromisos", "Registros", "Diccionarios"
            ),
            libro.hojas.map { it.nombre }
        )

        val registros = libro.hoja("Registros")
        assertNotNull(registros)
        registros!!

        // Encabezado exacto del modo extendido.
        assertEquals(
            EsquemaExportacion.EXTENDIDO.columnas,
            registros.filas[0].map { it.comoTexto() }
        )
        // Una fila por movimiento.
        assertEquals(datos.movimientos.size + 1, registros.filas.size)

        // La fecha viaja como serial y regresa igual.
        val primera = registros.filas[1]
        assertEquals(
            LocalDate.of(2026, 1, 1),
            Ooxml.desdeSerial(primera[0].numero!!)
        )
        assertEquals(4091.94, primera[1].numero!!, 0.0001)
        assertEquals("Banorte", primera[2].comoTexto())

        // El texto con comillas, ampersand y signos sobrevive el escapado.
        val conAcentos = registros.filas.first { it.getOrNull(4)?.comoTexto()?.contains("Pemex") == true }
        assertEquals("Gasolina \"Pemex\" & más <ruta>", conAcentos[4].comoTexto())
    }

    @Test
    fun `el modo compacto emite exactamente ocho columnas`() {
        val datos = datosDeMuestra()
        val archivo = File(salida, "ollin-compacto.xlsx")
        archivo.outputStream().use {
            ExportadorExcel(datos, EsquemaExportacion.COMPACTO, HojaExportable.PREDETERMINADAS)
                .escribeEn(it)
        }

        val registros = archivo.inputStream().use(XlsxLector::lee).hoja("Registros")!!
        assertEquals(
            listOf("Fecha", "Cantidad", "Cuenta", "Descripcion", "Medio", "Contraparte", "Tipo", "Mes"),
            registros.filas[0].map { it.comoTexto() }
        )
        // La contraparte sale como su codigo numerico 1/2.
        val transferencia = registros.filas.first {
            it.getOrNull(6)?.comoTexto() == TipoMovimiento.TRANSFERENCIA_SALIDA.etiqueta
        }
        assertEquals(1.0, transferencia[5].numero!!, 0.0)
    }

    @Test
    fun `exportar solo algunas pestanas produce un libro valido`() {
        val archivo = File(salida, "ollin-minimo.xlsx")
        archivo.outputStream().use {
            ExportadorExcel(datosDeMuestra(), EsquemaExportacion.EXTENDIDO, HojaExportable.MINIMA)
                .escribeEn(it)
        }
        val libro = archivo.inputStream().use(XlsxLector::lee)
        assertEquals(listOf("Registros", "Diccionarios"), libro.hojas.map { it.nombre })
    }

    @Test
    fun `un libro sin movimientos no revienta`() {
        val vacio = DatosExportacion(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        val archivo = File(salida, "ollin-vacio.xlsx")
        archivo.outputStream().use {
            ExportadorExcel(vacio, EsquemaExportacion.EXTENDIDO, HojaExportable.PREDETERMINADAS)
                .escribeEn(it)
        }
        assertTrue(archivo.length() > 1000)
        val libro = archivo.inputStream().use(XlsxLector::lee)
        assertTrue(libro.hojas.isNotEmpty())
    }
}
