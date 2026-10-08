package com.carlosalbertoxw.ollin.finanzas.data.seguridad

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

/**
 * El sello que ata la huella del PIN a este telefono.
 *
 * Un PIN de cuatro digitos son diez mil combinaciones, y PBKDF2 solo las
 * encarece: con el archivo de preferencias en la mano --un telefono con root,
 * un respaldo de adb-- se recorren en minutos. Sellada con un HMAC cuya llave
 * vive en el Keystore, la huella ya no se puede calcular fuera del telefono, y
 * dentro cada intento pasa por la app y su freno.
 *
 * La llave no exige autenticacion: es justo lo que se usa para autenticarse.
 * Si alguna vez se pierde, la huella sellada deja de coincidir y el PIN no
 * abre, igual que la base: ambas viven y mueren con el Keystore de la app.
 */
object LlaveDelPin : ClavePin.Sello {

    private const val ALIAS = "ollin_pin"
    private const val ALGORITMO = "HmacSHA256"

    override fun sella(huella: ByteArray): ByteArray =
        Mac.getInstance(ALGORITMO).apply { init(llave()) }.doFinal(huella)

    private fun llave(): SecretKey {
        val almacen = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (almacen.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generador =
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore")
        generador.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build()
        )
        return generador.generateKey()
    }
}
