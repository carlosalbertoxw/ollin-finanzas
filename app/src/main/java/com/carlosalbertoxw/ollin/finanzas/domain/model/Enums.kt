package com.carlosalbertoxw.ollin.finanzas.domain.model

import java.time.LocalDate

/**
 * Tipo de movimiento. Son seis y solo seis: cualquier renglon del libro cae en
 * uno de ellos, y de ahi se derivan el signo esperado y la contraparte.
 */
enum class TipoMovimiento(val etiqueta: String) {
    BALANCE_INICIAL("Balance Inicial"),
    ENTRADA("Entrada"),
    SALIDA("Salida"),
    TRANSFERENCIA_ENTRADA("Transferencia Entrada"),
    TRANSFERENCIA_SALIDA("Transferencia Salida"),

    /**
     * El activo cambio de valor sin que se moviera un peso: el terreno se
     * revaluo, la camioneta se deprecio, la cripto subio. Mueve el saldo de la
     * cuenta pero no es ingreso ni gasto, porque nadie pago ni cobro nada.
     */
    AJUSTE_VALOR("Ajuste de valor");

    val esTransferencia: Boolean
        get() = this == TRANSFERENCIA_ENTRADA || this == TRANSFERENCIA_SALIDA

    /** No lleva categoria ni contraparte: no hubo intercambio con nadie. */
    val esInterno: Boolean
        get() = this == BALANCE_INICIAL || this == AJUSTE_VALOR

    /** Signo que debe tener el importe para que el tipo sea coherente. */
    val signoEsperado: Int
        get() = when (this) {
            ENTRADA, TRANSFERENCIA_ENTRADA -> 1
            SALIDA, TRANSFERENCIA_SALIDA -> -1
            BALANCE_INICIAL, AJUSTE_VALOR -> 0 // cualquiera: revaluar o depreciar
        }

    companion object {
        fun desdeEtiqueta(valor: String?): TipoMovimiento? {
            val n = valor?.normalizaClave() ?: return null
            return entries.firstOrNull { it.etiqueta.normalizaClave() == n }
        }
    }
}

/**
 * Naturaleza de la cuenta. Determina como entra al balance y si su saldo
 * cuenta como liquidez, deuda o patrimonio no liquido.
 */
enum class TipoCuenta(val etiqueta: String, val esDeuda: Boolean, val esLiquida: Boolean) {
    EFECTIVO("Efectivo", esDeuda = false, esLiquida = true),
    DEBITO("Cuenta de banco", esDeuda = false, esLiquida = true),
    INVERSION("Inversion", esDeuda = false, esLiquida = true),
    CREDITO("Creditos - prestamos", esDeuda = true, esLiquida = false),
    CREDITO_MSI("Creditos - MSI", esDeuda = true, esLiquida = false),
    ACTIVO("Activo / patrimonio", esDeuda = false, esLiquida = false);

    companion object {
        fun desdeEtiqueta(valor: String?): TipoCuenta? {
            val n = valor?.normalizaClave() ?: return null
            return entries.firstOrNull { it.etiqueta.normalizaClave() == n || it.name.normalizaClave() == n }
        }
    }
}

/** Como se movio el dinero: en mano o por medios electronicos. */
enum class Medio(val etiqueta: String) {
    EFECTIVO("Efectivo"),
    ELECTRONICO("Electronico");

    companion object {
        fun desdeEtiqueta(valor: String?): Medio? {
            val n = valor?.normalizaClave() ?: return null
            return entries.firstOrNull { it.etiqueta.normalizaClave() == n }
        }
    }
}

/**
 * Contra quien va el movimiento: 1 = ocurre entre cuentas tuyas, 2 = interviene
 * un tercero.
 *
 * Ollin Finanzas lo deriva del tipo en vez de pedirlo. Un campo que se captura a mano
 * es un campo que se deja de mantener, y entonces todo reporte que dependa de
 * el miente. Ver [Movimiento.contraparteDerivada].
 */
enum class Contraparte(val codigo: Int, val etiqueta: String) {
    PROPIA(1, "Entre mis cuentas"),
    TERCERO(2, "Con un tercero");

    companion object {
        fun desdeCodigo(valor: Int?): Contraparte? = entries.firstOrNull { it.codigo == valor }
    }
}

/** Naturaleza de una categoria. Separa consumo real de compra de patrimonio. */
enum class TipoCategoria(val etiqueta: String) {
    GASTO("Gasto"),
    INGRESO("Ingreso"),
    /**
     * Salidas de dinero que no son consumo: terreno, cripto, bienes duraderos.
     * Se excluyen del gasto en los tableros y se contabilizan como traslado
     * de patrimonio, no como perdida.
     */
    PATRIMONIO("Patrimonio"),
    TRASPASO("Traspaso interno");

    companion object {
        fun desdeEtiqueta(valor: String?): TipoCategoria? {
            val n = valor?.normalizaClave() ?: return null
            return entries.firstOrNull { it.etiqueta.normalizaClave() == n || it.name.normalizaClave() == n }
        }
    }
}

/**
 * Periodicidad de un compromiso recurrente.
 *
 * Dos cadencias distintas conviven aqui, y por eso hay dos campos: las cortas
 * se miden en dias y las largas en meses. No se pueden unificar sin mentir.
 * Sumar 30 dias no es sumar un mes -- el plan se recorreria un dia mas en cada
 * febrero -- y "medio mes" no es una cantidad de meses que exista.
 *
 * Nadie deberia leer [meses] ni [dias] por su cuenta: se avanza con [avanza],
 * se retrocede con [retrocede] y se lee con [cada].
 */
enum class Periodicidad(val etiqueta: String, val meses: Int = 0, val dias: Int = 0) {
    SEMANAL("Semanal", dias = 7),
    /** Cada quince dias, tal cual: no es "dos veces al mes" con dia fijo. */
    QUINCENAL("Quincenal", dias = 15),
    MENSUAL("Mensual", meses = 1),
    BIMESTRAL("Bimestral", meses = 2),
    TRIMESTRAL("Trimestral", meses = 3),
    SEMESTRAL("Semestral", meses = 6),
    ANUAL("Anual", meses = 12);

    /** Cuantos pagos caen en un ano. Es lo que permite comparar cadencias distintas. */
    val vecesPorAnio: Int
        get() = if (dias > 0) 365 / dias else 12 / meses

    /** Se repite al menos una vez al mes: es carga que se siente todos los meses. */
    val cabeEnUnMes: Boolean
        get() = vecesPorAnio >= 12

    /** Lo que pesa al mes un pago de este tamano a esta cadencia. */
    fun equivalenteMensual(montoCentavos: Long): Long = montoCentavos * vecesPorAnio / 12

    /** Como se lee la cadencia en pantalla. */
    val cada: String
        get() = when {
            dias == 7 -> "Cada semana"
            dias > 0 -> "Cada $dias dias"
            meses == 1 -> "Cada mes"
            else -> "Cada $meses meses"
        }

    /**
     * Avanza [pasos] periodos desde [ancla]. Siempre desde el ancla y nunca
     * encadenando sobre el resultado anterior: `plusMonths` recorta el dia al
     * ultimo valido del mes destino y no lo recuerda, asi que encadenar sumas
     * arrastra el recorte para siempre. Ver [com.carlosalbertoxw.ollin.finanzas.data.db.Compromiso.proximoPago].
     */
    fun avanza(ancla: LocalDate, pasos: Long): LocalDate =
        if (dias > 0) ancla.plusDays(pasos * dias) else ancla.plusMonths(pasos * meses)

    /** El inverso de [avanza]: de una fecha del plan al ancla que la produce. */
    fun retrocede(fecha: LocalDate, pasos: Long): LocalDate = avanza(fecha, -pasos)

    companion object {
        fun desdeEtiqueta(valor: String?): Periodicidad? {
            val n = valor?.normalizaClave() ?: return null
            return entries.firstOrNull { it.etiqueta.normalizaClave() == n || it.name.normalizaClave() == n }
        }
    }
}

/**
 * Compiladas una sola vez. Estaban dentro de [normalizaClave], que se llama
 * cientos de miles de veces al importar y al buscar descripciones parecidas:
 * construir la expresion en cada llamada costaba mas que el trabajo real.
 */
private val ACENTOS = Regex("\\p{InCombiningDiacriticalMarks}+")
private val ESPACIOS = Regex("\\s+")

/**
 * Normaliza para comparar texto que viene de una hoja de calculo:
 * quita acentos, colapsa espacios y pasa a minusculas. Sin esto,
 * "Descripcion" y "Descripción" son claves distintas al importar.
 */
fun String.normalizaClave(): String {
    val sinAcentos = java.text.Normalizer.normalize(this, java.text.Normalizer.Form.NFD)
        .replace(ACENTOS, "")
    return sinAcentos.trim().lowercase().replace(ESPACIOS, " ")
}
