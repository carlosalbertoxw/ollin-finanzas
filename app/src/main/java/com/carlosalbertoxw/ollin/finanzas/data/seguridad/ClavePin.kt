package com.carlosalbertoxw.ollin.finanzas.data.seguridad

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Deriva la huella del PIN propio de Ollin Finanzas.
 *
 * El PIN nunca se guarda: se guarda PBKDF2 sobre el, con una sal distinta por
 * telefono. Un PIN de cuatro digitos tiene diez mil combinaciones, asi que sin
 * un derivado lento bastaria un segundo para probarlas todas contra el archivo
 * de preferencias; las iteraciones son justamente lo que hace que eso no sea
 * gratis.
 *
 * Pero diez mil combinaciones siguen siendo pocas: con el archivo en la mano,
 * ni seiscientas mil iteraciones aguantan mas que unos minutos. Por eso la
 * huella se **sella** ademas con un [Sello] que vive en el Keystore
 * ([LlaveDelPin]): sin el telefono no se puede calcular, y en el telefono cada
 * intento pasa por el freno de [ControlBloqueo].
 */
object ClavePin {

    private const val ITERACIONES = 120_000
    private const val BITS = 256
    private const val ALGORITMO = "PBKDF2WithHmacSHA256"

    const val LARGO_MINIMO = 4

    /**
     * Marca de las huellas selladas. Las que no la llevan son las de la 1.2.0 y
     * anteriores: PBKDF2 a secas, que se siguen aceptando y se sellan en cuanto
     * su dueno acierta, sin pedirle nada.
     */
    internal const val PREFIJO_SELLADA = "ks1:"

    /**
     * Lo que ata la huella al telefono. La de verdad es [LlaveDelPin], un HMAC
     * con una llave del Keystore que no se puede extraer; las pruebas pasan uno
     * hecho a mano porque la JVM no tiene Keystore.
     */
    fun interface Sello {
        fun sella(huella: ByteArray): ByteArray
    }

    enum class Verificacion {
        INCORRECTO,
        CORRECTO,

        /** Acerto, pero contra una huella vieja: hay que guardarla sellada. */
        CORRECTO_SIN_SELLAR
    }

    fun nuevaSal(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes)
    }

    /**
     * PBKDF2 a secas, en Base64. Es la huella de antes del sello: solo sirve
     * para reconocer las que ya estaban guardadas.
     *
     * Pesa cientos de milisegundos a proposito: nunca en el hilo principal.
     */
    suspend fun deriva(pin: String, sal: String): String =
        Base64.getEncoder().encodeToString(pbkdf2(pin, sal))

    /** La huella que se guarda hoy: PBKDF2 sellado con [sello]. */
    suspend fun huella(pin: String, sal: String, sello: Sello): String {
        val sellada = sellaFueraDelHilo(sello, pbkdf2(pin, sal))
        return PREFIJO_SELLADA + Base64.getEncoder().encodeToString(sellada)
    }

    /** El Keystore responde por IPC: tampoco en el hilo principal. */
    private suspend fun sellaFueraDelHilo(sello: Sello, huella: ByteArray): ByteArray =
        withContext(Dispatchers.Default) { sello.sella(huella) }

    private suspend fun pbkdf2(pin: String, sal: String): ByteArray =
        withContext(Dispatchers.Default) {
            val spec = PBEKeySpec(
                pin.toCharArray(),
                Base64.getDecoder().decode(sal),
                ITERACIONES,
                BITS
            )
            try {
                SecretKeyFactory.getInstance(ALGORITMO).generateSecret(spec).encoded
            } finally {
                spec.clearPassword()
            }
        }

    /**
     * Si [pin] corresponde a [hash], sellado o no.
     *
     * Comparacion en tiempo constante. Un `==` normal corta en el primer byte
     * distinto, y ese tiempo de mas revela cuanto del PIN se acerto.
     *
     * Cualquier cosa ilegible --Base64 roto, un sello que falla-- es un PIN
     * incorrecto y no una excepcion: la pantalla de bloqueo no puede caerse.
     */
    suspend fun verifica(pin: String, hash: String?, sal: String?, sello: Sello?): Verificacion {
        if (hash.isNullOrBlank() || sal.isNullOrBlank()) return Verificacion.INCORRECTO
        val derivada = runCatching { pbkdf2(pin, sal) }.getOrNull()
            ?: return Verificacion.INCORRECTO

        val sellada = hash.startsWith(PREFIJO_SELLADA)
        val calculada = if (sellada) {
            sello ?: return Verificacion.INCORRECTO
            runCatching { sellaFueraDelHilo(sello, derivada) }.getOrNull()
                ?: return Verificacion.INCORRECTO
        } else {
            derivada
        }
        val guardada = runCatching {
            Base64.getDecoder().decode(hash.removePrefix(PREFIJO_SELLADA))
        }.getOrNull() ?: return Verificacion.INCORRECTO

        return when {
            !MessageDigest.isEqual(calculada, guardada) -> Verificacion.INCORRECTO
            sellada -> Verificacion.CORRECTO
            else -> Verificacion.CORRECTO_SIN_SELLAR
        }
    }

    /** [verifica] reducido a si o no, para quien no tiene nada que migrar. */
    suspend fun coincide(pin: String, hash: String?, sal: String?, sello: Sello? = null): Boolean =
        verifica(pin, hash, sal, sello) != Verificacion.INCORRECTO
}
