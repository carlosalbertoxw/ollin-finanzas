package com.carlosalbertoxw.ollin.finanzas

import com.carlosalbertoxw.ollin.finanzas.data.db.FlujoMes
import com.carlosalbertoxw.ollin.finanzas.data.db.RenglonPresupuesto
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Las proyecciones son propiedades calculadas puras, pero son las que alimentan
 * las cifras grandes del tablero. Dos de ellas dividen, y ahi es donde una
 * cuenta recien creada -sin ingresos, sin meta- puede tumbar la pantalla.
 */
class ProyeccionesTest {

    private fun flujo(
        ingresos: Long,
        consumo: Long,
        patrimonio: Long
    ) = FlujoMes(
        periodo = "2026-01",
        ingresosCentavos = ingresos,
        gastoConsumoCentavos = consumo,
        compraPatrimonioCentavos = patrimonio
    )

    // ------------------------------------------------------------ FlujoMes

    @Test
    fun `sin ingresos la tasa de ahorro es cero y no divide entre cero`() {
        assertEquals(0.0, flujo(0, -50000, 0).tasaAhorro, 0.0001)
        assertEquals(0.0, flujo(-100, -50000, 0).tasaAhorro, 0.0001)
    }

    /**
     * Comprar un terreno no es gastar: si contara como consumo, el mes en que
     * inviertes pareceria el mes en que te arruinaste.
     */
    @Test
    fun `la tasa de ahorro mide sobre consumo real e ignora la compra de patrimonio`() {
        val mes = flujo(ingresos = 100000, consumo = -60000, patrimonio = -30000)
        assertEquals(0.4, mes.tasaAhorro, 0.0001)
    }

    @Test
    fun `el gasto total suma consumo y patrimonio, y el neto los resta del ingreso`() {
        val mes = flujo(ingresos = 100000, consumo = -60000, patrimonio = -30000)
        assertEquals(-90000L, mes.gastoTotalCentavos)
        assertEquals(10000L, mes.netoCentavos)
    }

    @Test
    fun `un mes sin nada deja todo en cero`() {
        val mes = flujo(0, 0, 0)
        assertEquals(0L, mes.gastoTotalCentavos)
        assertEquals(0L, mes.netoCentavos)
        assertEquals(0.0, mes.tasaAhorro, 0.0001)
    }

    // -------------------------------------------------- RenglonPresupuesto

    private fun renglon(meta: Long, real: Long) = RenglonPresupuesto(
        categoriaId = 11,
        nombreCategoria = "Gasolina",
        metaCentavos = meta,
        realCentavos = real
    )

    @Test
    fun `con meta en cero el avance es cero y no divide entre cero`() {
        assertEquals(0.0, renglon(meta = 0, real = -5000).avance, 0.0001)
        assertEquals(0.0, renglon(meta = -100, real = -5000).avance, 0.0001)
    }

    /** Los gastos llegan en negativo, asi que el avance usa el absoluto. */
    @Test
    fun `el avance y la desviacion usan el valor absoluto del real`() {
        val r = renglon(meta = 150000, real = -120000)
        assertEquals(0.8, r.avance, 0.0001)
        assertEquals(30000L, r.desviacionCentavos)
    }

    @Test
    fun `pasarse del presupuesto da avance mayor a uno y desviacion negativa`() {
        val r = renglon(meta = 100000, real = -130000)
        assertEquals(1.3, r.avance, 0.0001)
        assertEquals(-30000L, r.desviacionCentavos)
    }
}
