package mx.ollin.finanzas.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.launch
import mx.ollin.finanzas.data.prefs.Ajustes
import mx.ollin.finanzas.data.prefs.ModoBloqueo
import mx.ollin.finanzas.data.seguridad.ClavePin
import mx.ollin.finanzas.ui.seguridad.pedirCredencialDelSistema
import mx.ollin.finanzas.ui.theme.LocalColoresOllin

/**
 * Lo unico que se ve mientras Ollin Finanzas esta cerrada con llave.
 *
 * Sustituye al arbol entero de la app, no lo tapa: si fuera una capa encima,
 * el contenido seguiria compuesto debajo y asomaria en la vista de apps
 * recientes.
 */
@Composable
fun BloqueoPantalla(
    actividad: FragmentActivity,
    ajustes: Ajustes,
    alDesbloquear: () -> Unit
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = LocalColoresOllin.current.textoTenue
            )
            Spacer(Modifier.height(16.dp))
            Text("Ollin Finanzas esta bloqueada", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(24.dp))

            when (ajustes.modoBloqueo) {
                ModoBloqueo.SISTEMA -> DesbloqueoSistema(actividad, alDesbloquear)
                ModoBloqueo.PIN -> DesbloqueoPin(ajustes, alDesbloquear)
                // Transitorio: aun no se leen las preferencias del disco.
                ModoBloqueo.NINGUNO -> Unit
            }
        }
    }
}

@Composable
private fun DesbloqueoSistema(actividad: FragmentActivity, alDesbloquear: () -> Unit) {
    var mensaje by remember { mutableStateOf<String?>(null) }
    val colores = LocalColoresOllin.current

    val pedir = pedirCredencialDelSistema(
        actividad = actividad,
        titulo = "Desbloquea Ollin Finanzas",
        alLograr = alDesbloquear,
        alFallar = { mensaje = it }
    )
    val pide: () -> Unit = { mensaje = null; pedir() }

    // Se pide sola al entrar: un boton de mas antes del candado no aporta nada.
    LaunchedEffect(Unit) { pide() }

    mensaje?.let {
        Text(
            it,
            style = MaterialTheme.typography.bodyMedium,
            color = colores.alerta,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
    }

    Button(onClick = pide) { Text("Desbloquear") }
}

@Composable
private fun DesbloqueoPin(ajustes: Ajustes, alDesbloquear: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var verificando by remember { mutableStateOf(false) }
    val ambito = rememberCoroutineScope()
    val colores = LocalColoresOllin.current

    val verifica: () -> Unit = {
        if (!verificando && pin.isNotEmpty()) {
            verificando = true
            error = null
            ambito.launch {
                val correcto = ClavePin.coincide(pin, ajustes.pinHash, ajustes.pinSal)
                verificando = false
                if (correcto) {
                    alDesbloquear()
                } else {
                    error = "PIN incorrecto"
                    pin = ""
                }
            }
        }
    }

    OutlinedTextField(
        value = pin,
        onValueChange = { nuevo -> pin = nuevo.filter(Char::isDigit).take(12) },
        label = { Text("PIN de Ollin Finanzas") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        isError = error != null,
        modifier = Modifier.fillMaxWidth()
    )

    error?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, style = MaterialTheme.typography.bodyMedium, color = colores.salida)
    }

    Spacer(Modifier.height(16.dp))
    Button(
        onClick = verifica,
        enabled = pin.length >= ClavePin.LARGO_MINIMO && !verificando,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(if (verificando) "Comprobando..." else "Entrar")
    }
}
