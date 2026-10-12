package com.carlosalbertoxw.ollin.finanzas

import com.carlosalbertoxw.ollin.finanzas.data.db.Movimiento
import com.carlosalbertoxw.ollin.finanzas.data.excel.DatosExportacion
import com.carlosalbertoxw.ollin.finanzas.data.excel.EsquemaExportacion
import com.carlosalbertoxw.ollin.finanzas.data.excel.ExportadorExcel
import com.carlosalbertoxw.ollin.finanzas.data.excel.HojaExportable
import com.carlosalbertoxw.ollin.finanzas.data.excel.ImportadorExcel
import com.carlosalbertoxw.ollin.finanzas.data.excel.OpcionesImportacion
import com.carlosalbertoxw.ollin.finanzas.domain.model.Contraparte
import com.carlosalbertoxw.ollin.finanzas.domain.model.Medio
import com.carlosalbertoxw.ollin.finanzas.domain.model.TipoMovimiento
import com.carlosalbertoxw.ollin.finanzas.domain.usecase.RevisaCalidad
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import kotlin.time.measureTimedValue

/**
 * Cuanto tarda Ollin con un libro grande, medido y no supuesto.
 *
 * Los caminos que crecen con el libro son tres: la revision de calidad, que
 * compara todas las descripciones distintas entre si (cuadratica en el numero
 * de descripciones), y exportar e importar el `.xlsx`, que cargan el libro
 * entero en memoria. El tamano es el que el resto del codigo da por bueno
 * --"un libro de Ollin con cincuenta mil movimientos", ver [XlsxLector]-- y
 * las descripciones distintas son muchas mas de las que junta una persona.
 *
 * No falla por tiempo: los runners de CI varian demasiado y una prueba que se
 * pone roja por ruido ensena a ignorarla. Comprueba que el resultado sea
 * correcto y deja los tiempos en `app/build/pruebas/rendimiento.txt` y en la
 * salida de la prueba, para compararlos entre versiones y decidir con una cifra
 * si hace falta optimizar. Son tiempos de la JVM de escritorio, no de un
 * telefono: sirven para ver tendencias, no para prometer segundos.
 *
 * Solo corre si se pide, porque tarda cerca de un minuto y no tiene por que
 * pagarlo cada push:
 *
 * ```
 * ./gradlew :app:testDebugUnitTest -Pollin.rendimiento=true --tests '*RendimientoTest'
 * ```
 */
class RendimientoTest : BaseEnMemoria() {

    @Before
    fun soloSiSePide() {
        assumeTrue(
            "Se mide con -Pollin.rendimiento=true",
            System.getProperty("ollin.rendimiento") == "true"
        )
    }

    @Test
    fun `un libro grande se revisa, se exporta y se importa`() = runBlocking {
        val cuenta = nuevaCuenta("Banco")
        val movimientos = libroGrande(cuenta)
        movimientoDao.insertaTodos(movimientos)
        val tiempos = linkedMapOf<String, Long>()

        val (hallazgos, revisar) = measureTimedValue { RevisaCalidad(repositorio).ejecuta() }
        tiempos["Revisar calidad"] = revisar.inWholeMilliseconds

        val datos = DatosExportacion(
            cuentas = cuentaDao.todas(),
            categorias = categoriaDao.todas(),
            movimientos = movimientoDao.todos(),
            presupuestos = emptyList(),
            compromisos = emptyList()
        )
        val libro = ByteArrayOutputStream()
        val (_, exportar) = measureTimedValue {
            ExportadorExcel(datos, EsquemaExportacion.EXTENDIDO, HojaExportable.PREDETERMINADAS)
                .escribeEn(libro)
        }
        tiempos["Exportar (libro completo)"] = exportar.inWholeMilliseconds

        val (resultado, importar) = measureTimedValue {
            ImportadorExcel(db).importa(
                ByteArrayInputStream(libro.toByteArray()),
                OpcionesImportacion(reemplazarTodo = true)
            )
        }
        tiempos["Importar (reemplazando)"] = importar.inWholeMilliseconds

        assertEquals(MOVIMIENTOS, resultado.importados)
        assertEquals(MOVIMIENTOS, movimientoDao.todos().size)

        informa(tiempos, libro.size(), hallazgos.size)
    }

    /**
     * Movimientos repartidos en cinco anios, con [DESCRIPCIONES] descripciones
     * distintas de largos variados: la revision de calidad solo compara pares
     * de largo parecido, y con todas del mismo largo mediria un caso que no
     * existe.
     */
    private fun libroGrande(cuentaId: Long): List<Movimiento> {
        val inicio = LocalDate.of(2021, 1, 1)
        return List(MOVIMIENTOS) { i ->
            val gasto = i % 5 != 0
            Movimiento(
                fecha = inicio.plusDays((i % 1825).toLong()),
                importeCentavos = if (gasto) -(1_000L + i % 90_000) else 2_500_000L,
                cuentaId = cuentaId,
                categoriaId = null,
                descripcion = descripcion(i % DESCRIPCIONES),
                medio = Medio.ELECTRONICO,
                tipo = if (gasto) TipoMovimiento.SALIDA else TipoMovimiento.ENTRADA,
                contraparte = Contraparte.TERCERO
            )
        }
    }

    private fun descripcion(n: Int): String {
        val base = RAICES[n % RAICES.size]
        val relleno = "x".repeat(n % 17)
        return "$base $n$relleno"
    }

    private fun informa(tiempos: Map<String, Long>, bytes: Int, hallazgos: Int) {
        val texto = buildString {
            appendLine("Rendimiento con $MOVIMIENTOS movimientos y $DESCRIPCIONES descripciones")
            appendLine("(JVM de escritorio con Robolectric; no son tiempos de telefono)")
            tiempos.forEach { (paso, ms) -> appendLine("  ${paso.padEnd(28)} $ms ms") }
            appendLine("  ${"Tamano del .xlsx".padEnd(28)} ${bytes / 1024} KB")
            appendLine("  ${"Hallazgos de calidad".padEnd(28)} $hallazgos")
        }
        println(texto)
        File("build/pruebas").apply { mkdirs() }.resolve("rendimiento.txt").writeText(texto)
    }

    private companion object {
        const val MOVIMIENTOS = 50_000
        const val DESCRIPCIONES = 5_000

        val RAICES = listOf(
            "Supermercado",
            "Gasolina",
            "Renta del departamento",
            "Farmacia",
            "Suscripcion de musica",
            "Comida",
            "Transporte publico",
            "Colegiatura"
        )
    }
}
