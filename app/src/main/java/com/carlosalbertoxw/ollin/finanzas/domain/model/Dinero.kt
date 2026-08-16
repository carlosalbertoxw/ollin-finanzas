package com.carlosalbertoxw.ollin.finanzas.domain.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Todo importe vive como centavos en un Long.
 *
 * Guardar dinero en punto flotante deja saldos como 999.999999999996 o
 * -6.25e-13 donde deberia haber un cero exacto. Con centavos enteros el saldo
 * cero es cero y las conciliaciones cuadran.
 */
object Dinero {

    private val SIMBOLOS = DecimalFormatSymbols(Locale("es", "MX"))
    private val FORMATO_MONEDA = DecimalFormat("$#,##0.00", SIMBOLOS)
    private val FORMATO_CORTO = DecimalFormat("$#,##0", SIMBOLOS)
    private val FORMATO_LLANO = DecimalFormat("0.00", DecimalFormatSymbols(Locale.US))

    /** Convierte un valor de hoja de calculo a centavos, redondeando al centavo mas cercano. */
    fun aCentavos(valor: Double): Long =
        BigDecimal.valueOf(valor).setScale(2, RoundingMode.HALF_UP).movePointRight(2).toLong()

    fun aCentavos(valor: BigDecimal): Long =
        valor.setScale(2, RoundingMode.HALF_UP).movePointRight(2).toLong()

    /** Devuelve el valor decimal para escribirlo en la hoja. Nunca acumula error. */
    fun aDecimal(centavos: Long): BigDecimal =
        BigDecimal.valueOf(centavos).movePointLeft(2)

    fun aDoubleParaHoja(centavos: Long): Double = aDecimal(centavos).toDouble()

    /** "1234.56" tal como debe ir dentro del XML de la celda, sin separadores ni moneda. */
    fun aTextoHoja(centavos: Long): String = FORMATO_LLANO.format(aDecimal(centavos))

    fun formatea(centavos: Long): String = FORMATO_MONEDA.format(aDecimal(centavos))

    fun formateaCorto(centavos: Long): String = FORMATO_CORTO.format(aDecimal(centavos))

    /** Compacta para tarjetas de tablero: $184.4k, $1.2M. */
    fun formateaCompacto(centavos: Long): String {
        val abs = kotlin.math.abs(centavos)
        val signo = if (centavos < 0) "-" else ""
        return when {
            abs >= 100_000_000L -> "$signo$%.1fM".format(Locale.US, abs / 100_000_000.0)
            abs >= 100_000L -> "$signo$%.1fk".format(Locale.US, abs / 100_000.0)
            else -> signo + FORMATO_CORTO.format(BigDecimal.valueOf(abs).movePointLeft(2))
        }
    }

    /**
     * Interpreta lo que el usuario teclea: acepta "1,234.56", "1234,56",
     * "$1 234.56" y el signo al frente. Devuelve null si no hay numero.
     */
    fun parsea(entrada: String): Long? {
        val limpio = entrada.trim()
            .replace("$", "")
            .replace(" ", "")
            .replace(" ", "")
        if (limpio.isEmpty()) return null

        val negativo = limpio.startsWith("-") || (limpio.startsWith("(") && limpio.endsWith(")"))
        var nucleo = limpio.removePrefix("-").removePrefix("(").removeSuffix(")")

        val ultimaComa = nucleo.lastIndexOf(',')
        val ultimoPunto = nucleo.lastIndexOf('.')
        nucleo = when {
            // Ambos presentes: el ultimo es el separador decimal.
            ultimaComa >= 0 && ultimoPunto >= 0 ->
                if (ultimaComa > ultimoPunto) nucleo.replace(".", "").replace(',', '.')
                else nucleo.replace(",", "")
            // Solo comas: decimal si deja 1 o 2 digitos a la derecha, si no es de millares.
            ultimaComa >= 0 ->
                if (nucleo.length - ultimaComa - 1 in 1..2) nucleo.replace(',', '.')
                else nucleo.replace(",", "")
            else -> nucleo
        }

        val numero = nucleo.toBigDecimalOrNull() ?: return null
        val centavos = aCentavos(numero)
        return if (negativo) -centavos else centavos
    }
}
