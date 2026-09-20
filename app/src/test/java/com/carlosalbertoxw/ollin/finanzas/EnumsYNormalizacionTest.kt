package com.carlosalbertoxw.ollin.finanzas

import com.carlosalbertoxw.ollin.finanzas.domain.model.Contraparte
import com.carlosalbertoxw.ollin.finanzas.domain.model.Medio
import com.carlosalbertoxw.ollin.finanzas.domain.model.Periodicidad
import com.carlosalbertoxw.ollin.finanzas.domain.model.TipoCategoria
import com.carlosalbertoxw.ollin.finanzas.domain.model.TipoCuenta
import com.carlosalbertoxw.ollin.finanzas.domain.model.TipoMovimiento
import com.carlosalbertoxw.ollin.finanzas.domain.model.normalizaClave
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * normalizaClave sostiene todo el emparejamiento por texto de la app: los
 * encabezados al importar, las categorias aprendidas por descripcion y la
 * deteccion de erratas. Si cambia, se rompen tres modulos a la vez y ninguno
 * avisa, porque el resultado sigue siendo un String valido.
 */
class EnumsYNormalizacionTest {

    // ------------------------------------------------------ normalizaClave

    @Test
    fun `quita acentos, que es lo que iguala Descripcion con y sin tilde`() {
        assertEquals("descripcion", "Descripción".normalizaClave())
        assertEquals("categoria", "Categoría".normalizaClave())
        assertEquals(
            "Descripción".normalizaClave(),
            "DESCRIPCION".normalizaClave()
        )
    }

    @Test
    fun `recorta los extremos y colapsa los espacios internos`() {
        assertEquals("pago tarjeta oro", "  Pago   Tarjeta \t Oro ".normalizaClave())
    }

    @Test
    fun `normalizar lo ya normalizado no cambia nada`() {
        listOf("Descripción", "  Pago   Tarjeta ", "SUPER", "").forEach { original ->
            val unaVez = original.normalizaClave()
            assertEquals(unaVez, unaVez.normalizaClave())
        }
    }

    // ------------------------------------------------------ TipoMovimiento

    @Test
    fun `desdeEtiqueta tolera acentos, mayusculas y espacios de mas`() {
        assertEquals(
            TipoMovimiento.TRANSFERENCIA_ENTRADA,
            TipoMovimiento.desdeEtiqueta("  TRANSFERENCIA  entrada ")
        )
        assertEquals(TipoMovimiento.SALIDA, TipoMovimiento.desdeEtiqueta("salida"))
    }

    @Test
    fun `desdeEtiqueta devuelve null con nulo o etiqueta desconocida`() {
        assertNull(TipoMovimiento.desdeEtiqueta(null))
        assertNull(TipoMovimiento.desdeEtiqueta("Nomina"))
        assertNull(TipoMovimiento.desdeEtiqueta(""))
    }

    /**
     * El signo esperado es de donde cuelga la reparacion automatica y media
     * revision de calidad, asi que se fijan los seis, no una muestra.
     */
    @Test
    fun `el signo esperado cubre los seis tipos`() {
        assertEquals(1, TipoMovimiento.ENTRADA.signoEsperado)
        assertEquals(1, TipoMovimiento.TRANSFERENCIA_ENTRADA.signoEsperado)
        assertEquals(-1, TipoMovimiento.SALIDA.signoEsperado)
        assertEquals(-1, TipoMovimiento.TRANSFERENCIA_SALIDA.signoEsperado)
        // Los internos aceptan cualquier signo: revaluar sube, depreciar baja.
        assertEquals(0, TipoMovimiento.BALANCE_INICIAL.signoEsperado)
        assertEquals(0, TipoMovimiento.AJUSTE_VALOR.signoEsperado)
    }

    @Test
    fun `ser transferencia y ser interno son excluyentes`() {
        TipoMovimiento.entries.forEach { tipo ->
            assertFalse(
                "$tipo no puede ser transferencia e interno a la vez",
                tipo.esTransferencia && tipo.esInterno
            )
        }
        assertEquals(
            setOf(TipoMovimiento.BALANCE_INICIAL, TipoMovimiento.AJUSTE_VALOR),
            TipoMovimiento.entries.filter { it.esInterno }.toSet()
        )
        assertEquals(
            setOf(TipoMovimiento.TRANSFERENCIA_ENTRADA, TipoMovimiento.TRANSFERENCIA_SALIDA),
            TipoMovimiento.entries.filter { it.esTransferencia }.toSet()
        )
    }

    // ---------------------------------------------------------- los demas

    @Test
    fun `TipoCuenta acepta tanto la etiqueta visible como el nombre del enum`() {
        assertEquals(TipoCuenta.CREDITO_MSI, TipoCuenta.desdeEtiqueta("Creditos - MSI"))
        assertEquals(TipoCuenta.CREDITO_MSI, TipoCuenta.desdeEtiqueta("CREDITO_MSI"))
        assertEquals(TipoCuenta.EFECTIVO, TipoCuenta.desdeEtiqueta("efectivo"))
        assertNull(TipoCuenta.desdeEtiqueta("Alcancia"))
    }

    @Test
    fun `las banderas de TipoCuenta separan deuda de liquidez`() {
        assertTrue(TipoCuenta.CREDITO.esDeuda)
        assertTrue(TipoCuenta.CREDITO_MSI.esDeuda)
        assertFalse(TipoCuenta.ACTIVO.esDeuda)
        // Un terreno es patrimonio pero no se puede gastar manana.
        assertFalse(TipoCuenta.ACTIVO.esLiquida)
        assertTrue(TipoCuenta.EFECTIVO.esLiquida)
    }

    @Test
    fun `Contraparte mapea 1 y 2, y nada mas`() {
        assertEquals(Contraparte.PROPIA, Contraparte.desdeCodigo(1))
        assertEquals(Contraparte.TERCERO, Contraparte.desdeCodigo(2))
        assertNull(Contraparte.desdeCodigo(0))
        assertNull(Contraparte.desdeCodigo(3))
        assertNull(Contraparte.desdeCodigo(null))
    }

    @Test
    fun `Medio y TipoCategoria resuelven desde su etiqueta`() {
        assertEquals(Medio.ELECTRONICO, Medio.desdeEtiqueta("Electronico"))
        assertEquals(Medio.EFECTIVO, Medio.desdeEtiqueta("EFECTIVO"))
        assertNull(Medio.desdeEtiqueta("Cheque"))

        assertEquals(TipoCategoria.PATRIMONIO, TipoCategoria.desdeEtiqueta("Patrimonio"))
        assertEquals(TipoCategoria.TRASPASO, TipoCategoria.desdeEtiqueta("TRASPASO"))
    }

    @Test
    fun `Periodicidad resuelve desde su etiqueta y sus pasos son coherentes`() {
        assertEquals(Periodicidad.TRIMESTRAL, Periodicidad.desdeEtiqueta("Trimestral"))
        assertEquals(3, Periodicidad.desdeEtiqueta("Trimestral")?.meses)
        assertEquals(12, Periodicidad.ANUAL.meses)
        assertEquals(Periodicidad.SEMANAL, Periodicidad.desdeEtiqueta("Semanal"))
        assertEquals(7, Periodicidad.SEMANAL.dias)
        assertEquals(Periodicidad.QUINCENAL, Periodicidad.desdeEtiqueta("QUINCENAL"))

        Periodicidad.entries.filter { !it.esUnico }.forEach { p ->
            // Una cadencia es de dias o de meses, nunca de las dos: si lo fuera,
            // [avanza] no sabria que paso dar. La unica sin paso es UNICO, que
            // no se repite y por eso se prueba aparte.
            assertTrue("$p no define un paso unico", (p.dias > 0) != (p.meses > 0))
            // Y las que van en meses tienen que dividir el ano, o la proyeccion
            // anual de un compromiso deja de cuadrar.
            if (p.meses > 0) assertEquals("$p no divide el ano", 0, 12 % p.meses)
            assertTrue("$p no cae nunca en un ano", p.vecesPorAnio > 0)
        }

        // Solo una puede no tener paso. Si manana alguien agrega otra sin dias
        // ni meses, [avanza] la dejaria clavada en su fecha sin que se note.
        assertEquals(
            listOf(Periodicidad.UNICO),
            Periodicidad.entries.filter { it.esUnico }
        )
    }

    @Test
    fun `Periodicidad UNICO no se repite ni pesa en la carga mensual`() {
        val unico = Periodicidad.UNICO
        assertEquals(unico, Periodicidad.desdeEtiqueta("Unico"))
        assertTrue(unico.esUnico)

        // Sin ritmo anual: la division por el paso reventaria, y no hay cifra
        // honesta que poner. Cero es lo que hace que no entre en la carga fija.
        assertEquals(0, unico.vecesPorAnio)
        assertFalse(unico.cabeEnUnMes)
        assertEquals(0L, unico.equivalenteMensual(50_000L))

        // Y no hay pago siguiente: el ancla es el unico pago, avance lo que
        // avance el contador. Es lo que deja al compromiso quieto en su fecha
        // hasta que alguien lo cumpla o lo descarte.
        val fecha = LocalDate.of(2026, 3, 10)
        assertEquals(fecha, unico.avanza(fecha, 1))
        assertEquals(fecha, unico.avanza(fecha, 7))
        assertEquals(fecha, unico.retrocede(fecha, 3))
    }

    @Test
    fun `Periodicidad avanza en dias o en meses segun su cadencia`() {
        val ancla = LocalDate.of(2026, 1, 31)

        // En meses se cuenta desde el ancla, asi que el 31 se recupera en cada
        // mes que lo tiene en vez de quedarse recortado en 28 para siempre.
        assertEquals(LocalDate.of(2026, 2, 28), Periodicidad.MENSUAL.avanza(ancla, 1))
        assertEquals(LocalDate.of(2026, 3, 31), Periodicidad.MENSUAL.avanza(ancla, 2))

        // En dias el paso es exacto y no hay nada que recortar.
        assertEquals(LocalDate.of(2026, 2, 7), Periodicidad.SEMANAL.avanza(ancla, 1))
        assertEquals(LocalDate.of(2026, 2, 15), Periodicidad.QUINCENAL.avanza(ancla, 1))

        // Retroceder deshace lo que avanzo, que es lo que usa el editor para
        // volver de la fecha elegida al ancla del plan. Se prueba en un dia que
        // existe en todos los meses: es la unica ida y vuelta que se puede
        // garantizar (ver abajo).
        val diaSeguro = LocalDate.of(2026, 1, 15)
        Periodicidad.entries.forEach { p ->
            assertEquals(
                "$p no deshace su propio avance",
                diaSeguro,
                p.retrocede(p.avanza(diaSeguro, 3), 3)
            )
        }

        // Limitacion conocida y aceptada de las cadencias en meses: un 31 que
        // pasa por un mes corto se recorta y ya no vuelve. Queda fijada aqui
        // para que se note si alguien cambia el modelo de ancla.
        assertEquals(
            LocalDate.of(2026, 1, 30),
            Periodicidad.MENSUAL.retrocede(Periodicidad.MENSUAL.avanza(ancla, 3), 3)
        )
        // En dias no pasa: restar dias es exacto en cualquier fecha.
        assertEquals(ancla, Periodicidad.QUINCENAL.retrocede(Periodicidad.QUINCENAL.avanza(ancla, 3), 3))
    }

    @Test
    fun `Periodicidad lleva cualquier cadencia a lo que pesa al mes`() {
        // Quincenal son dos pagos al mes; mensual es el mismo importe.
        assertEquals(20_000L, Periodicidad.QUINCENAL.equivalenteMensual(10_000L))
        assertEquals(10_000L, Periodicidad.MENSUAL.equivalenteMensual(10_000L))
        // Anual no se siente cada mes, y por eso no entra en la carga fija.
        assertFalse(Periodicidad.ANUAL.cabeEnUnMes)
        assertTrue(Periodicidad.SEMANAL.cabeEnUnMes)
        assertTrue(Periodicidad.MENSUAL.cabeEnUnMes)
    }
}
