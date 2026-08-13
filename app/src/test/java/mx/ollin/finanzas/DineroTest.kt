package mx.ollin.finanzas

import mx.ollin.finanzas.domain.model.Dinero
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Dinero es la unica puerta por la que entra lo que el usuario teclea y la
 * unica que decide cuantos centavos se guardan. Un error aqui no revienta:
 * escribe un importe equivocado en la base y ahi se queda.
 */
class DineroTest {

    // ------------------------------------------------------------- parsea

    @Test
    fun `lee un decimal simple`() {
        assertEquals(123456L, Dinero.parsea("1234.56"))
    }

    /**
     * Con punto y coma presentes gana el que va al final: el otro es de
     * millares. Es la unica regla que desambigua sin preguntar el locale.
     */
    @Test
    fun `la coma es de millares cuando tambien hay punto decimal`() {
        assertEquals(123456L, Dinero.parsea("1,234.56"))
    }

    @Test
    fun `la coma sola con dos digitos a la derecha es separador decimal`() {
        assertEquals(123456L, Dinero.parsea("1234,56"))
        assertEquals(123450L, Dinero.parsea("1234,5"))
    }

    @Test
    fun `el formato europeo con punto de millares y coma decimal`() {
        assertEquals(123456L, Dinero.parsea("1.234,56"))
    }

    /**
     * Tres digitos tras la coma no pueden ser centavos, asi que es de millares.
     * Sin esta rama "1,234" entraria como doce pesos con treinta y cuatro.
     */
    @Test
    fun `la coma sola con tres digitos a la derecha es de millares`() {
        assertEquals(123400L, Dinero.parsea("1,234"))
    }

    @Test
    fun `ignora el simbolo de moneda y los espacios, incluido el no separable`() {
        // El U+00A0 es el que llega al copiar una celda de Excel.
        val noSeparable = ' '
        assertEquals(123456L, Dinero.parsea("\$1 234.56"))
        assertEquals(123456L, Dinero.parsea("\$1${noSeparable}234.56"))
        assertEquals(123456L, Dinero.parsea("  1234.56  "))
    }

    @Test
    fun `los parentesis marcan negativo, como en contabilidad`() {
        assertEquals(-50000L, Dinero.parsea("(500)"))
    }

    @Test
    fun `el signo menos al frente da negativo`() {
        assertEquals(-50000L, Dinero.parsea("-500"))
        assertEquals(-123456L, Dinero.parsea("-1,234.56"))
    }

    @Test
    fun `devuelve null cuando no queda nada que leer`() {
        assertNull(Dinero.parsea(""))
        assertNull(Dinero.parsea("   "))
        assertNull(Dinero.parsea("\$"))
    }

    @Test
    fun `devuelve null cuando no hay numero`() {
        assertNull(Dinero.parsea("abc"))
        assertNull(Dinero.parsea("-"))
    }

    // -------------------------------------------------------- conversiones

    @Test
    fun `redondea al centavo mas cercano con HALF_UP`() {
        assertEquals(1001L, Dinero.aCentavos(10.005))
        assertEquals(1000L, Dinero.aCentavos(10.004))
        assertEquals(0L, Dinero.aCentavos(0.0))
    }

    /** HALF_UP se aleja del cero: -10.005 no puede redondear a -10.00. */
    @Test
    fun `en negativos el redondeo se aleja del cero`() {
        assertEquals(-1001L, Dinero.aCentavos(-10.005))
    }

    @Test
    fun `ida y vuelta por decimal no pierde centavos`() {
        listOf(0L, 1L, -1L, 99L, 123456L, -987654321L).forEach { centavos ->
            assertEquals(centavos, Dinero.aCentavos(Dinero.aDecimal(centavos)))
        }
    }

    // ------------------------------------------------------------ formatos

    /** La celda del xlsx lleva el numero pelon: nada de moneda ni millares. */
    @Test
    fun `el texto de celda va sin moneda ni separador de millares`() {
        assertEquals("1234.56", Dinero.aTextoHoja(123456))
        assertEquals("-5.00", Dinero.aTextoHoja(-500))
        assertEquals("0.00", Dinero.aTextoHoja(0))
    }

    @Test
    fun `formatea con moneda y millares`() {
        assertEquals("\$1,234.56", Dinero.formatea(123456))
        assertEquals("-\$5.00", Dinero.formatea(-500))
    }

    @Test
    fun `el formato compacto usa el sufijo M a partir del millon`() {
        assertEquals("\$1.2M", Dinero.formateaCompacto(120_000_000))
    }

    @Test
    fun `el formato compacto usa el sufijo k a partir del millar`() {
        assertEquals("\$184.4k", Dinero.formateaCompacto(18_440_000))
    }

    @Test
    fun `por debajo del umbral el formato compacto no lleva sufijo`() {
        assertEquals("\$999", Dinero.formateaCompacto(99_900))
    }

    /**
     * El signo va antes del simbolo de moneda, y tiene que salir igual en las
     * tres ramas: es facil que una lo anteponga y otra no.
     */
    @Test
    fun `el signo negativo se antepone en las tres ramas del compacto`() {
        assertEquals("-\$1.2M", Dinero.formateaCompacto(-120_000_000))
        assertEquals("-\$184.4k", Dinero.formateaCompacto(-18_440_000))
        assertEquals("-\$999", Dinero.formateaCompacto(-99_900))
    }
}
