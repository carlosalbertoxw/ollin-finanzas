package com.carlosalbertoxw.ollin.finanzas.domain.model

import java.time.LocalDate
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

    fun larga(dia: LocalDate, hoy: LocalDate = LocalDate.now()): String =
        (if (dia.year == hoy.year) LARGA else LARGA_ANIO).format(dia)
            .replaceFirstChar { it.titlecase(ES) }
}
