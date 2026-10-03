package com.carlosalbertoxw.ollin.finanzas.data.seguridad

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.annotation.RequiresApi
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * La prueba de que el telefono de verdad reconocio a su dueno.
 *
 * El dialogo de huella o PIN del sistema avisa con un callback, y un callback
 * se puede invocar a mano: en un telefono con root, alguien con Frida llama a
 * `onAuthenticationSucceeded` y el candado se abre sin que nadie pusiera el
 * dedo. CodeQL lo marca como autenticacion local insegura, y con razon.
 *
 * Por eso el desbloqueo no se fia del callback sino del Keystore. Esta llave
 * solo se puede usar inmediatamente despues de una autenticacion real --huella
 * fuerte o credencial del telefono--, y quien lo comprueba es el hardware, no
 * la app. El dialogo recibe un cifrador sin estrenar; si al volver ese cifrador
 * no logra cifrar, nadie se autentico, diga lo que diga el callback.
 *
 * No protege la base: esa va cifrada con [LlaveBase], que no exige
 * autenticacion porque la base se abre antes de llegar al candado. Esto solo
 * vuelve inutil el atajo de saltarse el dialogo.
 */
@RequiresApi(Build.VERSION_CODES.R)
object LlaveDeDesbloqueo {

    private const val ALIAS = "ollin_desbloqueo"
    private const val TRANSFORMACION = "AES/GCM/NoPadding"

    /** Lo que se cifra para comprobar la llave. El resultado se tira. */
    private val RETO = "ollin".toByteArray(Charsets.UTF_8)

    /**
     * Un cifrador listo para pasarle al dialogo.
     *
     * Si la llave quedo invalidada --se agrego una huella nueva, o se quito y
     * se volvio a poner el bloqueo del telefono--, se tira y se crea otra:
     * invalidarla es justo lo que tiene que pasar en esos casos, y la siguiente
     * autenticacion vuelve a demostrar quien eres.
     */
    fun cifradorNuevo(): Cipher = try {
        Cipher.getInstance(TRANSFORMACION).apply { init(Cipher.ENCRYPT_MODE, llave()) }
    } catch (e: KeyPermanentlyInvalidatedException) {
        almacen().deleteEntry(ALIAS)
        Cipher.getInstance(TRANSFORMACION).apply { init(Cipher.ENCRYPT_MODE, llave()) }
    }

    /**
     * Si el cifrador que devolvio el dialogo funciona. Solo el Keystore puede
     * dejarlo usable, y solo despues de una autenticacion real.
     */
    fun demuestraAutenticacion(cifrador: Cipher?): Boolean =
        cifrador != null && runCatching { cifrador.doFinal(RETO) }.isSuccess

    private fun almacen(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun llave(): SecretKey {
        (almacen().getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generador = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generador.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(true)
                // Cero segundos: cada uso exige su propia autenticacion, la del
                // dialogo que se acaba de mostrar, y no una de hace un rato.
                .setUserAuthenticationParameters(
                    0,
                    KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
                )
                .build()
        )
        return generador.generateKey()
    }
}
