package mx.ollin.finanzas

import kotlinx.coroutines.test.runTest
import mx.ollin.finanzas.domain.model.Medio
import mx.ollin.finanzas.domain.model.TipoCategoria
import mx.ollin.finanzas.domain.model.TipoCuenta
import mx.ollin.finanzas.domain.model.TipoMovimiento
import mx.ollin.finanzas.domain.usecase.GravedadHallazgo
import mx.ollin.finanzas.domain.usecase.Hallazgo
import mx.ollin.finanzas.domain.usecase.RevisaCalidad
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Los nueve detectores de calidad, uno por prueba.
 *
 * Cada caso siembra el libro mas chico que dispara su hallazgo y nada mas: un
 * libro grande enciende varios detectores a la vez y entonces la prueba deja de
 * decir cual fue. Por eso tambien se afirma sobre la clave concreta y no sobre
 * el tamano de la lista.
 */
class RevisaCalidadTest : BaseEnMemoria() {

    private suspend fun revisa(): List<Hallazgo> = RevisaCalidad(repositorio).ejecuta()

    private fun List<Hallazgo>.de(clave: String): Hallazgo? = firstOrNull { it.clave == clave }

    /** Cuenta con saldo de sobra, para que nada quede en rojo por accidente. */
    private suspend fun banorteConSaldo(): Long {
        val id = nuevaCuenta("Banorte", TipoCuenta.DEBITO)
        nuevoMovimiento(id, 1_000_000, TipoMovimiento.BALANCE_INICIAL, "Balance Inicial")
        return id
    }

    // --------------------------------------------------------- los extremos

    @Test
    fun `un libro sin movimientos no reporta nada`() = runTest {
        nuevaCuenta("Banorte")
        assertTrue(revisa().isEmpty())
    }

    /**
     * El caso que mas cuesta mantener: si algun detector se vuelve demasiado
     * entusiasta, es aqui donde se nota antes de que el usuario vea una pantalla
     * de Salud llena de avisos falsos.
     */
    @Test
    fun `un libro sano no produce ningun hallazgo`() = runTest {
        val banorte = nuevaCuenta("Banorte", TipoCuenta.DEBITO)
        val cartera = nuevaCuenta("Cartera", TipoCuenta.EFECTIVO)
        val sueldo = nuevaCategoria("Sueldo", TipoCategoria.INGRESO)
        val gasolina = nuevaCategoria("Gasolina", TipoCategoria.GASTO)

        nuevoMovimiento(banorte, 1_000_000, TipoMovimiento.BALANCE_INICIAL, "Balance Inicial")
        nuevoMovimiento(
            cartera, 500_000, TipoMovimiento.BALANCE_INICIAL, "Balance Inicial",
            medio = Medio.EFECTIVO
        )
        nuevoMovimiento(banorte, 800_000, TipoMovimiento.ENTRADA, "Sueldo", sueldo)
        nuevoMovimiento(banorte, -300_000, TipoMovimiento.SALIDA, "Gasolina", gasolina)
        nuevoMovimiento(
            banorte, -200_000, TipoMovimiento.TRANSFERENCIA_SALIDA, "Traspaso a cartera",
            grupoTransferencia = "g1"
        )
        nuevoMovimiento(
            cartera, 200_000, TipoMovimiento.TRANSFERENCIA_ENTRADA, "Traspaso a cartera",
            medio = Medio.EFECTIVO, grupoTransferencia = "g1"
        )

        assertEquals(emptyList<Hallazgo>(), revisa())
    }

    // ------------------------------------------------------ los detectores

    @Test
    fun `detecta el tipo que contradice al signo y perdona a los internos`() = runTest {
        val banorte = banorteConSaldo()
        val gasolina = nuevaCategoria("Gasolina")

        nuevoMovimiento(banorte, -50_000, TipoMovimiento.ENTRADA, "Reembolso", gasolina)
        nuevoMovimiento(banorte, -30_000, TipoMovimiento.SALIDA, "Gasolina", gasolina)
        // Un ajuste de valor negativo es una depreciacion, no un error.
        nuevoMovimiento(banorte, -500, TipoMovimiento.AJUSTE_VALOR, "Depreciacion")

        val hallazgo = revisa().de("tipo_vs_signo")
        assertNotNull(hallazgo)
        assertEquals(1, hallazgo!!.afectados)
        assertEquals(GravedadHallazgo.ALTA, hallazgo.gravedad)
        assertTrue(hallazgo.reparable)
    }

    @Test
    fun `detecta la pata sin grupo y el grupo que no tiene dos patas`() = runTest {
        val banorte = banorteConSaldo()

        nuevoMovimiento(banorte, -20_000, TipoMovimiento.TRANSFERENCIA_SALIDA, "Traspaso suelto")
        nuevoMovimiento(
            banorte, -30_000, TipoMovimiento.TRANSFERENCIA_SALIDA, "Traspaso incompleto",
            grupoTransferencia = "g1"
        )

        val hallazgo = revisa().de("transferencia_huerfana")
        assertNotNull(hallazgo)
        assertEquals(2, hallazgo!!.afectados)
        assertEquals(GravedadHallazgo.ALTA, hallazgo.gravedad)
    }

    /** Sumar o borrar son correcciones distintas, asi que no se repara solo. */
    @Test
    fun `detecta dos saldos iniciales en la misma cuenta y no lo repara`() = runTest {
        val banorte = nuevaCuenta("Banorte")
        nuevoMovimiento(banorte, 600_000, TipoMovimiento.BALANCE_INICIAL, "Balance Inicial")
        nuevoMovimiento(banorte, 400_000, TipoMovimiento.BALANCE_INICIAL, "Saldo de apertura")

        val hallazgo = revisa().de("saldo_inicial_duplicado")
        assertNotNull(hallazgo)
        assertEquals(2, hallazgo!!.afectados)
        assertEquals(GravedadHallazgo.ALTA, hallazgo.gravedad)
        assertFalse(hallazgo.reparable)
        assertTrue("Debe citar el nombre de la cuenta", hallazgo.detalle.contains("Banorte"))
    }

    @Test
    fun `cuenta los movimientos sin categoria y excluye internos y transferencias`() = runTest {
        val banorte = banorteConSaldo()

        nuevoMovimiento(banorte, -30_000, TipoMovimiento.SALIDA, "Compra sin clasificar")
        nuevoMovimiento(banorte, -500, TipoMovimiento.AJUSTE_VALOR, "Depreciacion")
        nuevoMovimiento(banorte, -20_000, TipoMovimiento.TRANSFERENCIA_SALIDA, "Traspaso a cartera")

        val hallazgo = revisa().de("sin_categoria")
        assertNotNull(hallazgo)
        assertEquals(1, hallazgo!!.afectados)
        assertEquals(GravedadHallazgo.MEDIA, hallazgo.gravedad)
        assertTrue(hallazgo.reparable)
    }

    @Test
    fun `detecta el medio que no cuadra con la cuenta en los dos sentidos`() = runTest {
        val cartera = nuevaCuenta("Cartera", TipoCuenta.EFECTIVO)
        val tarjeta = nuevaCuenta("Tarjeta Oro", TipoCuenta.CREDITO)
        val gasto = nuevaCategoria("Gasolina")

        nuevoMovimiento(
            cartera, 500_000, TipoMovimiento.BALANCE_INICIAL, "Saldo de cartera",
            medio = Medio.EFECTIVO
        )
        // Una salida de la cartera no puede ser electronica.
        nuevoMovimiento(
            cartera, -10_000, TipoMovimiento.SALIDA, "Tortilleria", gasto,
            medio = Medio.ELECTRONICO
        )
        // Ni una tarjeta puede cobrarse en mano.
        nuevoMovimiento(
            tarjeta, -20_000, TipoMovimiento.SALIDA, "Restaurante", gasto,
            medio = Medio.EFECTIVO
        )

        val hallazgo = revisa().de("medio_incoherente")
        assertNotNull(hallazgo)
        assertEquals(2, hallazgo!!.afectados)
        assertEquals(GravedadHallazgo.BAJA, hallazgo.gravedad)
        assertTrue(hallazgo.reparable)
    }

    @Test
    fun `sin cuenta de activo, la compra de patrimonio pide crear una`() = runTest {
        val banorte = banorteConSaldo()
        val terrenos = nuevaCategoria("Terrenos", TipoCategoria.PATRIMONIO)
        nuevoMovimiento(banorte, -500_000, TipoMovimiento.SALIDA, "Construccion", terrenos)

        val hallazgo = revisa().de("patrimonio_sin_espejo")
        assertNotNull(hallazgo)
        assertEquals(1, hallazgo!!.afectados)
        assertEquals(GravedadHallazgo.MEDIA, hallazgo.gravedad)
        assertTrue(hallazgo.detalle.contains("Crea una cuenta de tipo Activo"))
    }

    @Test
    fun `con cuenta de activo, la compra de patrimonio pide registrar el traspaso`() = runTest {
        val banorte = banorteConSaldo()
        nuevaCuenta("Terreno", TipoCuenta.ACTIVO)
        val terrenos = nuevaCategoria("Terrenos", TipoCategoria.PATRIMONIO)
        nuevoMovimiento(banorte, -500_000, TipoMovimiento.SALIDA, "Construccion", terrenos)

        val hallazgo = revisa().de("patrimonio_sin_espejo")
        assertNotNull(hallazgo)
        assertTrue(hallazgo!!.detalle.contains("Registralas como transferencia"))
    }

    /** Una tarjeta en rojo es lo normal; una cuenta de banco en rojo, no. */
    @Test
    fun `marca la cuenta de debito en rojo pero no la de credito`() = runTest {
        val banorte = nuevaCuenta("Banorte", TipoCuenta.DEBITO)
        val tarjeta = nuevaCuenta("Tarjeta Oro", TipoCuenta.CREDITO)
        val gasto = nuevaCategoria("Gasolina")

        nuevoMovimiento(banorte, -10_000, TipoMovimiento.SALIDA, "Gasolina", gasto)
        nuevoMovimiento(tarjeta, -20_000, TipoMovimiento.SALIDA, "Restaurante", gasto)

        val hallazgo = revisa().de("saldo_negativo")
        assertNotNull(hallazgo)
        assertEquals(1, hallazgo!!.afectados)
        assertEquals(GravedadHallazgo.ALTA, hallazgo.gravedad)
        assertTrue(hallazgo.detalle.contains("Banorte"))
        assertFalse(hallazgo.detalle.contains("Tarjeta Oro"))
    }

    @Test
    fun `reporta los meses de en medio sin registros y no los extremos`() = runTest {
        val banorte = banorteConSaldo()
        val gasto = nuevaCategoria("Gasolina")
        nuevoMovimiento(
            banorte, -10_000, TipoMovimiento.SALIDA, "Gasolina", gasto,
            fecha = LocalDate.of(2026, 4, 10)
        )

        val hallazgo = revisa().de("meses_vacios")
        assertNotNull(hallazgo)
        assertEquals(2, hallazgo!!.afectados)
        assertTrue(hallazgo.detalle.contains("2026-02"))
        assertTrue(hallazgo.detalle.contains("2026-03"))
    }

    @Test
    fun `empareja erratas de captura y deja en paz lo que si es distinto`() = runTest {
        val banorte = banorteConSaldo()
        val gasto = nuevaCategoria("Gasolina")

        nuevoMovimiento(banorte, -10_000, TipoMovimiento.SALIDA, "Gasolina Pemex", gasto)
        nuevoMovimiento(banorte, -12_000, TipoMovimiento.SALIDA, "Gasolina Pemx", gasto)
        nuevoMovimiento(banorte, -13_000, TipoMovimiento.SALIDA, "Supermercado", gasto)

        val hallazgo = revisa().de("descripciones_parecidas")
        assertNotNull(hallazgo)
        assertEquals(1, hallazgo!!.afectados)
        assertEquals(GravedadHallazgo.BAJA, hallazgo.gravedad)
    }

    @Test
    fun `los hallazgos salen ordenados por gravedad`() = runTest {
        val cartera = nuevaCuenta("Cartera", TipoCuenta.EFECTIVO)
        val gasto = nuevaCategoria("Gasolina")

        nuevoMovimiento(
            cartera, 500_000, TipoMovimiento.BALANCE_INICIAL, "Saldo de cartera",
            medio = Medio.EFECTIVO
        )
        // BAJA
        nuevoMovimiento(
            cartera, -10_000, TipoMovimiento.SALIDA, "Tortilleria", gasto,
            medio = Medio.ELECTRONICO
        )
        // MEDIA
        nuevoMovimiento(
            cartera, -20_000, TipoMovimiento.SALIDA, "Sin clasificar",
            medio = Medio.EFECTIVO
        )
        // ALTA
        nuevoMovimiento(
            cartera, -30_000, TipoMovimiento.ENTRADA, "Reembolso", gasto,
            medio = Medio.EFECTIVO
        )

        val hallazgos = revisa()
        assertEquals(hallazgos, hallazgos.sortedBy { it.gravedad.ordinal })
        assertEquals(GravedadHallazgo.ALTA, hallazgos.first().gravedad)
        assertNull(hallazgos.de("saldo_negativo"))
    }
}
