package mx.ollin.finanzas.domain.model

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

/** Tipo de compromiso futuro que Ollin Finanzas vigila. */

/** Periodicidad de un compromiso recurrente. */
enum class Periodicidad(val etiqueta: String, val meses: Int) {
    MENSUAL("Mensual", 1),
    BIMESTRAL("Bimestral", 2),
    TRIMESTRAL("Trimestral", 3),
    SEMESTRAL("Semestral", 6),
    ANUAL("Anual", 12);

    companion object {
        fun desdeEtiqueta(valor: String?): Periodicidad? {
            val n = valor?.normalizaClave() ?: return null
            return entries.firstOrNull { it.etiqueta.normalizaClave() == n || it.name.normalizaClave() == n }
        }
    }
}

/**
 * Normaliza para comparar texto que viene de una hoja de calculo:
 * quita acentos, colapsa espacios y pasa a minusculas. Sin esto,
 * "Descripcion" y "Descripción" son claves distintas al importar.
 */
fun String.normalizaClave(): String {
    val sinAcentos = java.text.Normalizer.normalize(this, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
    return sinAcentos.trim().lowercase().replace(Regex("\\s+"), " ")
}
