package com.carlosalbertoxw.ollin.finanzas.data.actualizaciones

/**
 * Un numero de version semantico, para poder compararlos.
 *
 * Existe porque comparar versiones como texto miente: `"1.10.0" < "1.9.0"` es
 * cierto en orden alfabetico y falso en la realidad, y eso deja a la gente sin
 * enterarse de la actualizacion justo cuando el proyecto empieza a tener
 * historia.
 */
data class Version(
    val mayor: Int,
    val menor: Int,
    val parche: Int
) : Comparable<Version> {

    override fun compareTo(other: Version): Int = compareValuesBy(
        this, other,
        Version::mayor, Version::menor, Version::parche
    )

    override fun toString(): String = "$mayor.$menor.$parche"

    companion object {

        /**
         * Lee "1.2.3", "v1.2.3" y "1.2.3-debug" como la misma version.
         *
         * La `v` es como se escriben los tags y el `-debug` es el sufijo que le
         * pone la variante de depuracion al `versionName`. Ninguno de los dos
         * cambia de que version se habla, y tratarlos como texto distinto
         * dejaria la compilacion de depuracion creyendo siempre que esta
         * desactualizada.
         *
         * Falta de partes es tolerable —"1.2" es 1.2.0—; texto que no es un
         * numero, no: se devuelve nulo antes que inventar una version.
         */
        fun de(texto: String?): Version? {
            val limpio = texto?.trim()?.removePrefix("v")?.substringBefore('-')
            if (limpio.isNullOrEmpty()) return null

            val partes = limpio.split('.')
            if (partes.isEmpty() || partes.size > 3) return null

            val numeros = partes.map { it.toIntOrNull() ?: return null }
            if (numeros.any { it < 0 }) return null

            return Version(
                mayor = numeros[0],
                menor = numeros.getOrElse(1) { 0 },
                parche = numeros.getOrElse(2) { 0 }
            )
        }
    }
}
