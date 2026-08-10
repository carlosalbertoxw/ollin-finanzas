package mx.ollin.finanzas

import mx.ollin.finanzas.data.db.Categoria
import mx.ollin.finanzas.data.db.Compromiso
import mx.ollin.finanzas.data.db.Cuenta
import mx.ollin.finanzas.data.db.Movimiento
import mx.ollin.finanzas.data.db.Presupuesto
import mx.ollin.finanzas.data.excel.DatosExportacion
import mx.ollin.finanzas.data.excel.EsquemaExportacion
import mx.ollin.finanzas.data.excel.ExportadorExcel
import mx.ollin.finanzas.data.excel.HojaExportable
import mx.ollin.finanzas.data.excel.XlsxLector
import mx.ollin.finanzas.domain.model.Contraparte
import mx.ollin.finanzas.domain.model.Medio
import mx.ollin.finanzas.domain.model.Periodicidad
import mx.ollin.finanzas.domain.model.TipoCategoria
import mx.ollin.finanzas.domain.model.TipoCuenta
import mx.ollin.finanzas.domain.model.TipoMovimiento
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.LocalDate

/**
 * Casos que el round trip no toca: compromisos con datos, catalogos incompletos
 * y volumenes parecidos a los de un uso real de varios años.
 */
class ExportadorBordesTest {

    private fun exporta(datos: DatosExportacion, hojas: Set<HojaExportable> = HojaExportable.PREDETERMINADAS): ByteArray {
        val bytes = ByteArrayOutputStream()
        ExportadorExcel(datos, EsquemaExportacion.EXTENDIDO, hojas).escribeEn(bytes)
        return bytes.toByteArray()
    }

    private fun mov(
        id: Long,
        fecha: LocalDate,
        centavos: Long,
        cuentaId: Long,
        categoriaId: Long?,
        tipo: TipoMovimiento
    ) = Movimiento(
        id = id,
        fecha = fecha,
        importeCentavos = centavos,
        cuentaId = cuentaId,
        categoriaId = categoriaId,
        descripcion = "Movimiento $id",
        medio = Medio.ELECTRONICO,
        tipo = tipo,
        contraparte = Contraparte.TERCERO
    )

    @Test
    fun `un libro con compromisos reales se escribe`() {
        val datos = DatosExportacion(
            cuentas = listOf(Cuenta(id = 1, nombre = "Banorte", tipo = TipoCuenta.DEBITO)),
            categorias = listOf(Categoria(id = 10, nombre = "Casa", tipo = TipoCategoria.GASTO)),
            movimientos = listOf(mov(1, LocalDate.of(2026, 1, 5), -1000, 1, null, TipoMovimiento.SALIDA)),
            presupuestos = emptyList(),
            compromisos = listOf(
                Compromiso(
                    id = 1, nombre = "Refri a 12 MSI", cuentaId = 1, categoriaId = 10, montoCentavos = 120000,
                    periodicidad = Periodicidad.MENSUAL,
                    fechaPrimerPago = LocalDate.of(2026, 1, 5),
                    totalPagos = 12, pagosRealizados = 3
                ),
                Compromiso(
                    id = 2, nombre = "Streaming", cuentaId = null, categoriaId = null, montoCentavos = 29900,
                    periodicidad = Periodicidad.MENSUAL,
                    fechaPrimerPago = LocalDate.of(2025, 3, 1),
                    totalPagos = null, pagosRealizados = 30
                ),
                Compromiso(
                    id = 3, nombre = "Seguro auto", cuentaId = 99, categoriaId = 10, montoCentavos = 1500000,
                    periodicidad = Periodicidad.ANUAL,
                    fechaPrimerPago = LocalDate.of(2024, 6, 1),
                    totalPagos = 5, pagosRealizados = 7
                )
            )
        )
        val libro = XlsxLector.lee(exporta(datos).inputStream())
        assertTrue(libro.hojas.any { it.nombre == "Compromisos" })
    }

    @Test
    fun `catalogos incompletos no rompen la exportacion`() {
        val datos = DatosExportacion(
            // Nombre con apostrofo y con caracteres que obligan a entrecomillar.
            cuentas = listOf(Cuenta(id = 1, nombre = "Ahorro d'Ana (MXN)", tipo = TipoCuenta.INVERSION)),
            // Categoria hija cuyo padre no esta en la lista.
            categorias = listOf(Categoria(id = 11, nombre = "Gasolina", padreId = 99, tipo = TipoCategoria.GASTO)),
            movimientos = listOf(
                // Categoria inexistente y cuenta inexistente.
                mov(1, LocalDate.of(2026, 1, 5), -1000, 77, 88, TipoMovimiento.SALIDA),
                mov(2, LocalDate.of(2026, 1, 6), 5000, 1, null, TipoMovimiento.ENTRADA)
            ),
            presupuestos = listOf(Presupuesto(1, 88, 2026, 1, 0)),
            compromisos = emptyList()
        )
        val libro = XlsxLector.lee(exporta(datos).inputStream())
        assertTrue(libro.hojas.isNotEmpty())
    }

    @Test
    fun `un libro sin cuentas ni categorias se escribe`() {
        val datos = DatosExportacion(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        assertTrue(exporta(datos).size > 1000)
    }

    @Test
    fun `tres años de movimientos diarios se exportan`() {
        val cuentas = (1L..6L).map {
            Cuenta(id = it, nombre = "Cuenta $it", tipo = TipoCuenta.entries[it.toInt() % TipoCuenta.entries.size])
        }
        val padres = (1L..8L).map {
            Categoria(id = it, nombre = "Rubro $it", tipo = TipoCategoria.GASTO)
        }
        val hijas = (1L..40L).map {
            Categoria(id = 100 + it, nombre = "Categoria $it", padreId = (it % 8) + 1, tipo = TipoCategoria.GASTO)
        }
        val inicio = LocalDate.of(2023, 1, 1)
        val movimientos = (0 until 3 * 365 * 3).map { i ->
            mov(
                id = i.toLong() + 1,
                fecha = inicio.plusDays((i / 3).toLong()),
                centavos = -(100L + i),
                cuentaId = (i % 6) + 1L,
                categoriaId = 100 + (i % 40) + 1L,
                tipo = TipoMovimiento.SALIDA
            )
        }
        val datos = DatosExportacion(cuentas, padres + hijas, movimientos, emptyList(), emptyList())
        val bytes = exporta(datos)
        assertTrue("El libro debe pesar algo", bytes.size > 10_000)
    }
}
