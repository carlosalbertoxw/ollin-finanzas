package mx.ollin.finanzas.ui.screens

import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mx.ollin.finanzas.data.prefs.Ajustes
import mx.ollin.finanzas.data.prefs.ModoBloqueo
import mx.ollin.finanzas.data.seguridad.ClavePin
import mx.ollin.finanzas.di.Contenedor
import mx.ollin.finanzas.ui.recuerdaVm
import mx.ollin.finanzas.ui.seguridad.pedirCredencialDelSistema
import mx.ollin.finanzas.ui.seguridad.telefonoAsegurado
import mx.ollin.finanzas.ui.theme.LocalColoresOllin

class AjustesVm(contenedor: Contenedor) : ViewModel() {

    private val prefs = contenedor.ajustes
    private val repo = contenedor.repositorio

    val ajustes: StateFlow<Ajustes> = prefs.ajustes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Ajustes())

    val movimientos: StateFlow<Int> = repo.observaConteoMovimientos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun cambiaTema(oscuro: Boolean?) {
        viewModelScope.launch { prefs.guardaTema(oscuro) }
    }

    fun cambiaColorDinamico(valor: Boolean) {
        viewModelScope.launch { prefs.guardaColorDinamico(valor) }
    }

    fun cambiaMuestraSaldoInicial(valor: Boolean) {
        viewModelScope.launch { prefs.guardaMuestraSaldoInicial(valor) }
    }

    fun quitaBloqueo() {
        viewModelScope.launch { prefs.quitaBloqueo() }
    }

    fun usaBloqueoDelSistema() {
        viewModelScope.launch { prefs.activaBloqueoSistema() }
    }

    fun usaBloqueoConPin(pin: String) {
        viewModelScope.launch {
            val sal = ClavePin.nuevaSal()
            prefs.activaBloqueoPin(hash = ClavePin.deriva(pin, sal), sal = sal)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AjustesPantalla(
    contenedor: Contenedor,
    alAbrirCuentas: () -> Unit,
    alAbrirCategorias: () -> Unit,
    alAbrirCompromisos: () -> Unit,
    alCerrar: () -> Unit
) {
    val vm = recuerdaVm("ajustes") { AjustesVm(contenedor) }
    val ajustes by vm.ajustes.collectAsStateWithLifecycle()
    val movimientos by vm.movimientos.collectAsStateWithLifecycle()
    val colores = LocalColoresOllin.current

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Ajustes") },
            navigationIcon = {
                IconButton(onClick = alCerrar) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                }
            }
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Apariencia", style = MaterialTheme.typography.titleMedium)

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val opciones = listOf<Pair<String, Boolean?>>(
                    "Sistema" to null,
                    "Claro" to false,
                    "Oscuro" to true
                )
                opciones.forEachIndexed { i, (etiqueta, valor) ->
                    SegmentedButton(
                        selected = ajustes.temaOscuro == valor,
                        onClick = { vm.cambiaTema(valor) },
                        shape = SegmentedButtonDefaults.itemShape(i, opciones.size)
                    ) { Text(etiqueta) }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Color del sistema", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Usa la paleta de tu fondo de pantalla en vez de la de Ollin.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colores.textoTenue
                        )
                    }
                    Switch(
                        checked = ajustes.colorDinamico,
                        onCheckedChange = vm::cambiaColorDinamico
                    )
                }
            }

            HorizontalDivider()

            Text("Captura", style = MaterialTheme.typography.titleMedium)

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Mostrar \"Saldo inicial\"", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "El saldo inicial solo se ocupa al dar de alta una cuenta. Apagalo " +
                            "cuando ya hayas capturado los tuyos y la captura queda mas corta. " +
                            "Los que ya guardaste se siguen pudiendo abrir y editar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colores.textoTenue
                    )
                }
                Switch(
                    checked = ajustes.muestraSaldoInicial,
                    onCheckedChange = vm::cambiaMuestraSaldoInicial
                )
            }

            HorizontalDivider()

            SeccionBloqueo(
                ajustes = ajustes,
                alQuitar = vm::quitaBloqueo,
                alUsarSistema = vm::usaBloqueoDelSistema,
                alUsarPin = vm::usaBloqueoConPin
            )

            HorizontalDivider()

            Text("Catalogo", style = MaterialTheme.typography.titleMedium)
            Text(
                "Las cuentas y las categorias con las que capturas. Renombralas a lo tuyo: " +
                    "las que trae Ollin de fabrica son solo un punto de partida.",
                style = MaterialTheme.typography.bodySmall,
                color = colores.textoTenue
            )
            TextButton(onClick = alAbrirCuentas, modifier = Modifier.fillMaxWidth()) {
                Text("Administrar cuentas")
            }
            TextButton(onClick = alAbrirCategorias, modifier = Modifier.fillMaxWidth()) {
                Text("Administrar categorias")
            }
            // Entrada fija a compromisos. La del tablero solo aparece cuando ya
            // hay alguno por vencer, asi que sin esta no habria forma de dar de
            // alta el primero.
            TextButton(onClick = alAbrirCompromisos, modifier = Modifier.fillMaxWidth()) {
                Text("Administrar compromisos")
            }

            HorizontalDivider()

            Text("Tus datos", style = MaterialTheme.typography.titleMedium)
            Text(
                "$movimientos movimientos guardados en este telefono. Ollin no manda nada a " +
                    "ningun servidor y el respaldo automatico del sistema esta desactivado " +
                    "para la base: tu respaldo es la exportacion a .xlsx, que decides tu donde guardar.",
                style = MaterialTheme.typography.bodyMedium,
                color = colores.textoTenue
            )

            HorizontalDivider()

            Text("Sobre Ollin", style = MaterialTheme.typography.titleMedium)
            Text(
                "Ollin es \"movimiento\" en nahuatl, y es el nombre del glifo del calendario " +
                    "mexica que representa el cambio. Un libro de finanzas no es mas que el " +
                    "registro de tus movimientos.",
                style = MaterialTheme.typography.bodyMedium,
                color = colores.textoTenue
            )
            Text(
                "Los importes se guardan en centavos enteros, nunca en decimales flotantes: " +
                    "por eso un saldo en cero es exactamente cero.",
                style = MaterialTheme.typography.bodySmall,
                color = colores.textoTenue
            )

            Spacer(Modifier.height(24.dp))
            TextButton(onClick = alCerrar, modifier = Modifier.fillMaxWidth()) { Text("Volver") }
        }
    }
}

@Composable
private fun SeccionBloqueo(
    ajustes: Ajustes,
    alQuitar: () -> Unit,
    alUsarSistema: () -> Unit,
    alUsarPin: (String) -> Unit
) {
    val contexto = LocalContext.current
    val actividad = LocalActivity.current as? FragmentActivity
    val colores = LocalColoresOllin.current
    val modoActual = ajustes.modoBloqueo

    var pidiendoPinNuevo by remember { mutableStateOf(false) }
    var pidiendoPinActual by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf<String?>(null) }
    // Lo que se hara en cuanto confirmes que eres tu.
    var pendiente by remember { mutableStateOf<(() -> Unit)?>(null) }

    val telefonoAsegurado = remember(contexto) { telefonoAsegurado(contexto) }

    val confirmaConSistema = pedirCredencialDelSistema(
        actividad = actividad ?: return,
        titulo = "Confirma que eres tu",
        alLograr = { pendiente?.invoke(); pendiente = null },
        alFallar = { pendiente = null; aviso = it }
    )

    /**
     * Cambiar o quitar el candado exige la llave que hay puesta ahora. Sin esto,
     * quien encuentre la app abierta la desprotege en dos toques y el candado
     * solo estorba a su dueno.
     */
    fun conConfirmacion(accion: () -> Unit) {
        aviso = null
        when (modoActual) {
            ModoBloqueo.NINGUNO -> accion()
            ModoBloqueo.SISTEMA -> { pendiente = accion; confirmaConSistema() }
            ModoBloqueo.PIN -> { pendiente = accion; pidiendoPinActual = true }
        }
    }

    Text("Bloqueo", style = MaterialTheme.typography.titleMedium)
    Text(
        "Ollin pide la llave al abrirse y al volver despues de un minuto fuera. " +
            "Ese minuto es lo que evita que elegir un archivo de Excel te expulse.",
        style = MaterialTheme.typography.bodySmall,
        color = colores.textoTenue
    )

    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        ModoBloqueo.entries.forEachIndexed { i, modo ->
            SegmentedButton(
                selected = modoActual == modo,
                onClick = {
                    aviso = null
                    when (modo) {
                        ModoBloqueo.NINGUNO -> conConfirmacion(alQuitar)
                        ModoBloqueo.SISTEMA ->
                            if (telefonoAsegurado) conConfirmacion(alUsarSistema)
                            else aviso = "Tu telefono no tiene patron, PIN ni contrasena. " +
                                "Configuralo en los ajustes de Android y vuelve aqui."
                        ModoBloqueo.PIN -> conConfirmacion { pidiendoPinNuevo = true }
                    }
                },
                shape = SegmentedButtonDefaults.itemShape(i, ModoBloqueo.entries.size),
                icon = {}
            ) {
                Text(
                    modo.etiqueta,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    aviso?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = colores.alerta)
    }

    Text(
        when (modoActual) {
            ModoBloqueo.NINGUNO -> "Cualquiera que tome tu telefono desbloqueado puede abrir Ollin."
            ModoBloqueo.SISTEMA -> "Se usa el patron, PIN o huella con que desbloqueas el telefono. " +
                "Ollin no guarda ningun secreto."
            ModoBloqueo.PIN -> "Se usa un PIN solo de Ollin. Si lo olvidas no hay forma de " +
                "recuperarlo: tendrias que reinstalar la app y perderias los datos."
        },
        style = MaterialTheme.typography.bodySmall,
        color = colores.textoTenue
    )

    Text(
        "La base de datos esta cifrada con AES-256 y su llave vive en el Keystore del " +
            "telefono. El archivo no dice nada aunque alguien lo saque por USB.",
        style = MaterialTheme.typography.bodySmall,
        color = colores.textoTenue
    )

    if (modoActual == ModoBloqueo.PIN) {
        TextButton(
            onClick = { conConfirmacion { pidiendoPinNuevo = true } },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Cambiar el PIN") }
    }

    if (pidiendoPinActual) {
        DialogoPinActual(
            ajustes = ajustes,
            alConfirmar = {
                pidiendoPinActual = false
                pendiente?.invoke()
                pendiente = null
            },
            alCancelar = { pidiendoPinActual = false; pendiente = null }
        )
    }

    if (pidiendoPinNuevo) {
        DialogoNuevoPin(
            alGuardar = { pin -> alUsarPin(pin); pidiendoPinNuevo = false },
            alCancelar = { pidiendoPinNuevo = false }
        )
    }
}

@Composable
private fun DialogoPinActual(
    ajustes: Ajustes,
    alConfirmar: () -> Unit,
    alCancelar: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var verificando by remember { mutableStateOf(false) }
    val ambito = rememberCoroutineScope()

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
                error?.let {
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
                enabled = pin.length >= ClavePin.LARGO_MINIMO && !verificando,
                onClick = {
                    verificando = true
                    error = null
                    ambito.launch {
                        val correcto = ClavePin.coincide(pin, ajustes.pinHash, ajustes.pinSal)
                        verificando = false
                        if (correcto) alConfirmar() else { error = "PIN incorrecto"; pin = "" }
                    }
                }
            ) { Text(if (verificando) "Comprobando..." else "Confirmar") }
        },
        dismissButton = { TextButton(onClick = alCancelar) { Text("Cancelar") } }
    )
}

@Composable
private fun DialogoNuevoPin(alGuardar: (String) -> Unit, alCancelar: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirmacion by remember { mutableStateOf("") }

    val corto = pin.length < ClavePin.LARGO_MINIMO
    val distintos = confirmacion.isNotEmpty() && pin != confirmacion

    AlertDialog(
        onDismissRequest = alCancelar,
        title = { Text("PIN de Ollin") },
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
