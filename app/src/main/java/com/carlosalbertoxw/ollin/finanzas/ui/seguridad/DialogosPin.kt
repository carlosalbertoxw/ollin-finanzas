package com.carlosalbertoxw.ollin.finanzas.ui.seguridad

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.carlosalbertoxw.ollin.finanzas.data.prefs.Ajustes
import com.carlosalbertoxw.ollin.finanzas.data.seguridad.ClavePin
import com.carlosalbertoxw.ollin.finanzas.data.seguridad.ControlBloqueo
import com.carlosalbertoxw.ollin.finanzas.ui.theme.LocalColoresOllin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Pide el PIN puesto antes de cambiarlo o quitarlo.
 *
 * Pasa por [ControlBloqueo.intentaPin], igual que la pantalla de bloqueo, y no
 * por [ClavePin] directamente: este dialogo se abre con la app ya desbloqueada,
 * y si no frenara, quien la encontrara abierta podria probar los diez mil PIN
 * aqui sin espera y llevarse uno que su dueno quiza usa en otros lados.
 */
@Composable
fun DialogoPinActual(
    ajustes: Ajustes,
    bloqueo: ControlBloqueo,
    alConfirmar: () -> Unit,
    alCancelar: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var verificando by remember { mutableStateOf(false) }
    var espera by remember { mutableIntStateOf(bloqueo.segundosDeEspera()) }
    val esperaHasta by bloqueo.esperaHasta.collectAsState()
    val ambito = rememberCoroutineScope()

    // La misma cuenta regresiva que la pantalla de bloqueo, leida del control.
    LaunchedEffect(esperaHasta) {
        espera = bloqueo.segundosDeEspera()
        while (espera > 0) {
            delay(1_000)
            espera = bloqueo.segundosDeEspera()
        }
    }

    AlertDialog(
        onDismissRequest = alCancelar,
        title = { Text("Confirma tu PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Escribe el PIN que tienes puesto para poder cambiarlo o quitarlo.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(12) },
                    label = { Text("PIN actual") },
                    singleLine = true,
                    isError = error != null,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                val mensaje = if (espera > 0) "Demasiados intentos. Espera $espera s." else error
                mensaje?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalColoresOllin.current.salida
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = pin.length >= ClavePin.LARGO_MINIMO && !verificando && espera == 0,
                onClick = {
                    verificando = true
                    error = null
                    ambito.launch {
                        val intento = bloqueo.intentaPin(pin, ajustes.pinHash, ajustes.pinSal)
                        verificando = false
                        if (intento == ControlBloqueo.IntentoDePin.Correcto) {
                            alConfirmar()
                        } else {
                            espera = bloqueo.segundosDeEspera()
                            error = "PIN incorrecto"
                            pin = ""
                        }
                    }
                }
            ) { Text(if (verificando) "Comprobando..." else "Confirmar") }
        },
        dismissButton = { TextButton(onClick = alCancelar) { Text("Cancelar") } }
    )
}

@Composable
fun DialogoNuevoPin(alGuardar: (String) -> Unit, alCancelar: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirmacion by remember { mutableStateOf("") }

    val corto = pin.length < ClavePin.LARGO_MINIMO
    val distintos = confirmacion.isNotEmpty() && pin != confirmacion

    AlertDialog(
        onDismissRequest = alCancelar,
        title = { Text("PIN de Ollin Finanzas") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Minimo ${ClavePin.LARGO_MINIMO} digitos. No se guarda tal cual: " +
                        "de el solo queda una huella de la que no se puede volver atras.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(12) },
                    label = { Text("PIN nuevo") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                OutlinedTextField(
                    value = confirmacion,
                    onValueChange = { confirmacion = it.filter(Char::isDigit).take(12) },
                    label = { Text("Repitelo") },
                    singleLine = true,
                    isError = distintos,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                if (distintos) {
                    Text(
                        "Los dos PIN no coinciden.",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalColoresOllin.current.salida
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { alGuardar(pin) },
                enabled = !corto && pin == confirmacion
            ) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = alCancelar) { Text("Cancelar") }
        }
    )
}
