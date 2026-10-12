package com.carlosalbertoxw.ollin.finanzas

import com.carlosalbertoxw.ollin.finanzas.domain.usecase.distanciaHasta
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

/**
 * La distancia acotada de la revision de calidad toma dos atajos --solo la
 * franja diagonal y cortar en cuanto la fila pasa del tope-- y ninguno puede
 * cambiar la respuesta. Aqui se compara contra la tabla completa de
 * Levenshtein, la de siempre, en miles de pares al azar con un alfabeto chico
 * para que haya muchas distancias cortas, que es donde se decide.
 */
class DistanciaTest {

    @Test
    fun `coincide con Levenshtein completo hasta el tope`() {
        val azar = Random(20261011)
        repeat(20_000) {
            val a = texto(azar)
            val b = texto(azar)
            val tope = azar.nextInt(0, 4)

            val esperada = minOf(levenshtein(a, b), tope + 1)

            assertEquals(
                "\"$a\" contra \"$b\" con tope $tope",
                esperada,
                distanciaHasta(a, b, tope)
            )
        }
    }

    @Test
    fun `casos a mano`() {
        assertEquals(0, distanciaHasta("gasolina", "gasolina", 2))
        assertEquals(1, distanciaHasta("gasolina", "gasolinas", 2))
        assertEquals(2, distanciaHasta("supermercado", "supremercado", 2))
        assertEquals(3, distanciaHasta("renta", "farmacia", 2))
        assertEquals(3, distanciaHasta("", "abc", 2))
        assertEquals(2, distanciaHasta("", "ab", 2))
    }

    private fun texto(azar: Random): String =
        String(CharArray(azar.nextInt(0, 9)) { "abc"[azar.nextInt(3)] })

    /** La de siempre: la tabla entera, sin atajos. */
    private fun levenshtein(a: String, b: String): Int {
        val tabla = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) tabla[i][0] = i
        for (j in 0..b.length) tabla[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val costo = if (a[i - 1] == b[j - 1]) 0 else 1
                tabla[i][j] = minOf(
                    tabla[i - 1][j] + 1,
                    tabla[i][j - 1] + 1,
                    tabla[i - 1][j - 1] + costo
                )
            }
        }
        return tabla[a.length][b.length]
    }
}
