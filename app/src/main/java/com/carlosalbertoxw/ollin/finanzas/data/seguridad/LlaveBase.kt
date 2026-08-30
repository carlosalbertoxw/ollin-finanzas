package com.carlosalbertoxw.ollin.finanzas.data.seguridad

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * La frase que abre la base cifrada.
 *
 * Se genera una sola vez al azar y no se guarda en claro: queda envuelta con
 * una llave AES que vive en el Keystore de Android, de donde no se puede
 * extraer ni con root. La app pide desenvolverla; nunca ve la llave maestra.
 *
 * Los 32 bytes al azar se representan en hexadecimal a proposito. La frase
 * viaja como texto —se guarda en SharedPreferences y se le entrega a SQLCipher
 * como cadena—, y en bytes crudos el resultado dependeria de por donde pase:
 * cualquier codificacion de por medio los alteraria y la base no volveria a
 * abrir.
 */
object LlaveBase {

    private const val ARCHIVO = "ollin_llave"
    private const val CAMPO = "frase_envuelta"
    private const val ALIAS = "ollin_base_maestra"
    private const val TRANSFORMACION = "AES/GCM/NoPadding"
    private const val BITS_ETIQUETA = 128
    private const val BYTES_IV = 12

    /**
     * Sincrono a proposito: Room construye el helper sin corrutinas, y una
     * lectura de SharedPreferences mas un desenvuelto de Keystore son
     * microsegundos.
     */
    fun frase(contexto: Context): String {
        val prefs = contexto.applicationContext
            .getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)

        prefs.getString(CAMPO, null)?.let { guardada ->
            return desenvuelve(guardada)
        }

        val nueva = ByteArray(32)
            .also { SecureRandom().nextBytes(it) }
            .joinToString("") { "%02x".format(it) }
        // commit y no apply: si el proceso muere antes de escribirla, la base
        // queda cifrada con una frase que ya nadie conoce.
        prefs.edit().putString(CAMPO, envuelve(nueva)).commit()
        return nueva
    }

    private fun llaveMaestra(): SecretKey {
        val almacen = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (almacen.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generador = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generador.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                // Sin exigir desbloqueo: la base se abre antes de que puedas
                // autenticarte, y exigirlo aqui dejaria la app sin arrancar.
                .setUserAuthenticationRequired(false)
                .build()
        )
        return generador.generateKey()
    }

    private fun envuelve(frase: String): String {
        val cifrador = Cipher.getInstance(TRANSFORMACION)
        cifrador.init(Cipher.ENCRYPT_MODE, llaveMaestra())
        val cuerpo = cifrador.doFinal(frase.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(cifrador.iv + cuerpo)
    }

    private fun desenvuelve(guardada: String): String {
        val todo = Base64.getDecoder().decode(guardada)
        val cifrador = Cipher.getInstance(TRANSFORMACION)
        cifrador.init(
            Cipher.DECRYPT_MODE,
            llaveMaestra(),
            GCMParameterSpec(BITS_ETIQUETA, todo, 0, BYTES_IV)
        )
        return String(cifrador.doFinal(todo, BYTES_IV, todo.size - BYTES_IV), Charsets.UTF_8)
    }
}
