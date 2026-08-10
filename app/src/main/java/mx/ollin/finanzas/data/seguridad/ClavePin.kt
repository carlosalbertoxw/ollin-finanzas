package mx.ollin.finanzas.data.seguridad

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Deriva la huella del PIN propio de Ollin.
 *
 * El PIN nunca se guarda: se guarda PBKDF2 sobre el, con una sal distinta por
 * telefono. Un PIN de cuatro digitos tiene diez mil combinaciones, asi que sin
 * un derivado lento bastaria un segundo para probarlas todas contra el archivo
 * de preferencias; las iteraciones son justamente lo que hace que eso no sea
 * gratis.
 */
object ClavePin {

    private const val ITERACIONES = 120_000
    private const val BITS = 256
    private const val ALGORITMO = "PBKDF2WithHmacSHA256"

    const val LARGO_MINIMO = 4

    fun nuevaSal(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes)
    }

    /** Pesa cientos de milisegundos a proposito: nunca en el hilo principal. */
    suspend fun deriva(pin: String, sal: String): String = withContext(Dispatchers.Default) {
        val spec = PBEKeySpec(
            pin.toCharArray(),
            Base64.getDecoder().decode(sal),
            ITERACIONES,
            BITS
        )
        val huella = SecretKeyFactory.getInstance(ALGORITMO).generateSecret(spec).encoded
        spec.clearPassword()
        Base64.getEncoder().encodeToString(huella)
    }

    /**
     * Comparacion en tiempo constante. Un `==` normal corta en el primer byte
     * distinto, y ese tiempo de mas revela cuanto del PIN se acerto.
     */
    suspend fun coincide(pin: String, hash: String?, sal: String?): Boolean {
        if (hash.isNullOrBlank() || sal.isNullOrBlank()) return false
        val calculado = runCatching { deriva(pin, sal) }.getOrNull() ?: return false
        return MessageDigest.isEqual(
            Base64.getDecoder().decode(calculado),
            runCatching { Base64.getDecoder().decode(hash) }.getOrNull() ?: return false
        )
    }
}
