package com.carlosalbertoxw.ollin.finanzas.ui.seguridad

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.carlosalbertoxw.ollin.finanzas.data.seguridad.LlaveDeDesbloqueo

/** Hay patron, PIN o contrasena que pedir prestada. */
fun telefonoAsegurado(contexto: Context): Boolean =
    contexto.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

/**
 * Devuelve la accion que pide la credencial del telefono.
 *
 * La usan la pantalla de bloqueo para dejarte entrar y la de ajustes para
 * confirmar que eres tu antes de quitar el candado. Antes de Android 11 el
 * dialogo unificado no admite credencial del dispositivo, asi que ahi se abre
 * la pantalla de desbloqueo del sistema.
 *
 * Desde Android 11 el exito no se cree por el callback: se exige que el
 * cifrador de [LlaveDeDesbloqueo], que solo el Keystore habilita tras una
 * autenticacion real, logre cifrar. Por eso la huella tiene que ser de clase
 * fuerte; las debiles no pueden habilitar una llave, y quien solo tenga una
 * entra con el patron o el PIN del telefono.
 *
 * Si el Keystore no deja preparar la llave en algun telefono, se cae a la
 * pantalla de desbloqueo del sistema en vez de dejar a nadie fuera de su libro.
 */
@Composable
fun pedirCredencialDelSistema(
    actividad: FragmentActivity,
    titulo: String,
    alLograr: () -> Unit,
    alFallar: (String) -> Unit
): () -> Unit {
    val lanzador = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { resultado ->
        if (resultado.resultCode == Activity.RESULT_OK) alLograr()
        else alFallar("No se pudo verificar. Intenta de nuevo.")
    }

    val pantallaDelSistema: () -> Unit = {
        val guardia = actividad.getSystemService(KeyguardManager::class.java)
        @Suppress("DEPRECATION")
        val intencion = guardia?.createConfirmDeviceCredentialIntent(
            titulo,
            "Usa tu patron, PIN o contrasena"
        )
        if (intencion != null) lanzador.launch(intencion)
        else alFallar("Tu telefono ya no tiene patron ni PIN configurado.")
    }

    return {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val preparado = runCatching {
                val cifrador = LlaveDeDesbloqueo.cifradorNuevo()
                BiometricPrompt(
                    actividad,
                    ContextCompat.getMainExecutor(actividad),
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(
                            resultado: BiometricPrompt.AuthenticationResult
                        ) {
                            if (LlaveDeDesbloqueo.demuestraAutenticacion(resultado.cryptoObject?.cipher)) {
                                alLograr()
                            } else {
                                alFallar("No se pudo verificar. Intenta de nuevo.")
                            }
                        }

                        override fun onAuthenticationError(codigo: Int, descripcion: CharSequence) {
                            alFallar(descripcion.toString())
                        }
                    }
                ).authenticate(
                    BiometricPrompt.PromptInfo.Builder()
                        .setTitle(titulo)
                        .setSubtitle("Usa tu huella, patron o PIN")
                        .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
                        .build(),
                    BiometricPrompt.CryptoObject(cifrador)
                )
            }
            if (preparado.isFailure) pantallaDelSistema()
        } else {
            pantallaDelSistema()
        }
    }
}
