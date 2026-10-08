package com.carlosalbertoxw.ollin.finanzas

import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.carlosalbertoxw.ollin.finanzas.data.db.Cuenta
import com.carlosalbertoxw.ollin.finanzas.data.db.Movimiento
import com.carlosalbertoxw.ollin.finanzas.data.db.OllinDatabase
import com.carlosalbertoxw.ollin.finanzas.data.excel.EsquemaExportacion
import com.carlosalbertoxw.ollin.finanzas.data.excel.HojaExportable
import com.carlosalbertoxw.ollin.finanzas.data.excel.OpcionesImportacion
import com.carlosalbertoxw.ollin.finanzas.data.repo.FinanzasRepositorio
import com.carlosalbertoxw.ollin.finanzas.domain.model.Contraparte
import com.carlosalbertoxw.ollin.finanzas.domain.model.Medio
import com.carlosalbertoxw.ollin.finanzas.domain.model.TipoCuenta
import com.carlosalbertoxw.ollin.finanzas.domain.model.TipoMovimiento
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/**
 * Exportar e importar por el `ContentResolver` del telefono.
 *
 * En la JVM el round trip va de bytes a bytes; aqui pasa por lo mismo que
 * cuando eliges un archivo en el selector: abrir el destino con "wt", escribir
 * el zip, volver a abrirlo y leerlo. Las bases son en memoria y no la de la
 * app, para no tocar el libro de quien tenga instalada la version de depuracion.
 *
 * El selector del sistema no se prueba: es UI de otra app y no lo alcanza
 * Compose. Se usa un archivo propio en `cacheDir` con su `Uri`.
 */
@RunWith(AndroidJUnit4::class)
class ExportarImportarTest {

    private val contexto = InstrumentationRegistry.getInstrumentation().targetContext
    private val archivo = File(contexto.cacheDir, "ollin-prueba.xlsx")
    private val uri: Uri = Uri.fromFile(archivo)

    private lateinit var origen: OllinDatabase
    private lateinit var destino: OllinDatabase

    @Before
    fun abreLasBases() {
        origen = Room.inMemoryDatabaseBuilder(contexto, OllinDatabase::class.java).build()
        destino = Room.inMemoryDatabaseBuilder(contexto, OllinDatabase::class.java).build()
        archivo.delete()
    }

    @After
    fun cierraLasBases() {
        origen.close()
        destino.close()
        archivo.delete()
    }

    private suspend fun siembra(movimientos: Int): Long {
        val cuenta = origen.cuentaDao()
            .inserta(Cuenta(nombre = "Banorte", tipo = TipoCuenta.DEBITO))
        val movimientoDao = origen.movimientoDao()
        movimientoDao.inserta(
            movimiento(cuenta, 1_000_000, TipoMovimiento.BALANCE_INICIAL, "Inicial", dia = 1)
        )
        repeat(movimientos - 1) { i ->
            movimientoDao.inserta(
                movimiento(cuenta, -(1_000L + i), TipoMovimiento.SALIDA, "Gasto $i", 2 + i % 27)
            )
        }
        return cuenta
    }

    private fun movimiento(
        cuenta: Long,
        centavos: Long,
        tipo: TipoMovimiento,
        descripcion: String,
        dia: Int
    ) = Movimiento(
        fecha = LocalDate.of(2026, 1, dia),
        importeCentavos = centavos,
        cuentaId = cuenta,
        descripcion = descripcion,
        medio = Medio.ELECTRONICO,
        tipo = tipo,
        contraparte = if (tipo == TipoMovimiento.BALANCE_INICIAL) {
            Contraparte.PROPIA
        } else {
            Contraparte.TERCERO
        }
    )

    private suspend fun exportaDesdeOrigen() =
        FinanzasRepositorio(origen, contexto.contentResolver)
            .exporta(uri, EsquemaExportacion.EXTENDIDO, HojaExportable.PREDETERMINADAS)

    @Test
    fun loExportadoAUnArchivoVuelveAEntrarEnOtraBase() = runBlocking {
        siembra(movimientos = 3)
        val esperado = origen.movimientoDao().todos().sumOf { it.importeCentavos }

        exportaDesdeOrigen()
        assertTrue("El archivo tiene que existir y no estar vacio", archivo.length() > 0)

        val resultado = FinanzasRepositorio(destino, contexto.contentResolver)
            .importa(uri, OpcionesImportacion())

        assertEquals(3, resultado.importados)
        assertEquals(3, destino.movimientoDao().cuenta())
        assertEquals(
            "Los centavos no pueden cambiar en el viaje",
            esperado,
            destino.movimientoDao().todos().sumOf { it.importeCentavos }
        )
    }

    /**
     * Sobrescribir un respaldo grande con uno chico. Sin truncar ("wt"), la cola
     * del archivo viejo quedaria pegada al final y el .xlsx saldria corrupto.
     */
    @Test
    fun sobrescribirUnRespaldoMasGrandeLoTrunca() = runBlocking {
        siembra(movimientos = 300)
        exportaDesdeOrigen()
        val grande = archivo.length()

        origen.clearAllTables()
        siembra(movimientos = 2)
        exportaDesdeOrigen()

        assertTrue("El segundo respaldo tiene que ser mas chico", archivo.length() < grande)
        val resultado = FinanzasRepositorio(destino, contexto.contentResolver)
            .importa(uri, OpcionesImportacion())
        assertEquals(2, resultado.importados)
    }
}
