package com.carlosalbertoxw.ollin.finanzas.domain.model

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Las fechas como las lee una persona. `LocalDate.toString()` da "2026-10-13",
 * que es un formato de intercambio, no de pantalla.
 *
 * Es la misma forma que usa Ollin Actividades al capturar: "Martes 13 de
 * octubre". El ano solo aparece cuando no es este, porque aqui si es comun
 * capturar o corregir un movimiento del ano pasado y sin el ano se leeria como
 * de hace unos dias.
 */
object Fechas {

    private val ES: Locale = Locale.forLanguageTag("es-MX")
    private val LARGA = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", ES)
    private val LARGA_ANIO = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", ES)

    /**
     * Las abreviaturas de los meses, fijas. Las de la plataforma cambian con la
     * version de los datos de idioma —"sep", "sept", "sept."— y una misma
     * fecha se veia distinta segun el telefono.
     */
    private val MESES_CORTOS =
        listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")
    private val MES_ANIO = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", ES)

    fun larga(dia: LocalDate, hoy: LocalDate = LocalDate.now()): String =
        (if (dia.year == hoy.year) LARGA else LARGA_ANIO).format(dia)
            .replaceFirstChar { it.titlecase(ES) }

    /**
     * "13 oct", para renglones donde la fecha acompana a otros datos. Como
     * [larga], el ano solo cuando no es este. Es la `fechaCortaConAnio` de
     * Ollin Actividades, con las mismas abreviaturas.
     */
    fun corta(dia: LocalDate, hoy: LocalDate = LocalDate.now()): String =
        "${dia.dayOfMonth} ${MESES_CORTOS[dia.monthValue - 1]}" +
            if (dia.year == hoy.year) "" else " ${dia.year}"

    /** "Hoy" y "Ayer" se leen mejor que la fecha cuando son lo mas frecuente. */
    fun relativa(dia: LocalDate, hoy: LocalDate = LocalDate.now()): String = when (dia) {
        hoy -> "Hoy"
        hoy.minusDays(1) -> "Ayer"
        hoy.plusDays(1) -> "Mañana"
        else -> larga(dia, hoy)
    }

    /** "Oct", o "Dic 2025" si no es de este ano: rotulos de meses en graficas y listas. */
    fun mesCorto(mes: YearMonth, hoy: LocalDate = LocalDate.now()): String {
        val nombre = MESES_CORTOS[mes.monthValue - 1].replaceFirstChar { it.titlecase(ES) }
        return if (mes.year == hoy.year) nombre else "$nombre ${mes.year}"
    }

    /**
     * El rotulo de una barra mensual: "Oct", sin ano aunque sea de otro, porque
     * en una grafica de ocho meses no cabe y el orden ya lo dice.
     */
    fun rotuloDeMes(periodo: String): String =
        periodo(periodo)?.let { mesCorto(it, hoy = it.atDay(1)) } ?: periodo.takeLast(2)

    /** "agosto de 2026", en minusculas para ir dentro de una frase. */
    fun mesEnFrase(mes: YearMonth): String = MES_ANIO.format(mes)

    /** Un periodo "2026-10" de las consultas, o null si no lo es. */
    fun periodo(texto: String): YearMonth? = runCatching { YearMonth.parse(texto) }.getOrNull()
}
