package com.carlosalbertoxw.ollin.finanzas.ui.screens

import android.os.Build
import android.text.format.DateFormat
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
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
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
import com.carlosalbertoxw.ollin.finanzas.data.notify.Recordatorios
import com.carlosalbertoxw.ollin.finanzas.data.prefs.Ajustes
import com.carlosalbertoxw.ollin.finanzas.data.prefs.ModoBloqueo
import com.carlosalbertoxw.ollin.finanzas.data.seguridad.ClavePin
import com.carlosalbertoxw.ollin.finanzas.data.prefs.AjustesRepositorio
import com.carlosalbertoxw.ollin.finanzas.data.repo.FinanzasRepositorio
import com.carlosalbertoxw.ollin.finanzas.ui.recuerdaVm
import com.carlosalbertoxw.ollin.finanzas.ui.seguridad.pedirCredencialDelSistema
import com.carlosalbertoxw.ollin.finanzas.ui.seguridad.telefonoAsegurado
import com.carlosalbertoxw.ollin.finanzas.ui.theme.LocalColoresOllin

class AjustesVm(
    private val prefs: AjustesRepositorio,
    private val repo: FinanzasRepositorio
) : ViewModel() {

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

    fun cambiaMuestraTutoriales(valor: Boolean) {
        viewModelScope.launch { prefs.guardaMuestraTutoriales(valor) }
    }

    fun cambiaBuscarActualizaciones(valor: Boolean) {
        viewModelScope.launch { prefs.guardaBuscarActualizaciones(valor) }
    }

    fun cambiaHoraDeAviso(hora: Int, minuto: Int) {
        viewModelScope.launch { prefs.guardaHoraDeAviso(hora, minuto) }
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
    ajustes: AjustesRepositorio,
    repo: FinanzasRepositorio,
    alAbrirCuentas: () -> Unit,
    alAbrirCategorias: () -> Unit,
    alAbrirCompromisos: () -> Unit,
    alAbrirTutoriales: () -> Unit,
    alAbrirAcercaDe: () -> Unit,
    alCerrar: () -> Unit
) {
    val vm = recuerdaVm("ajustes") { AjustesVm(ajustes, repo) }
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
                            "Usa la paleta de tu fondo de pantalla en vez de la de Ollin Finanzas.",
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

            SeccionAvisos(
                ajustes = ajustes,
                alCambiarHora = vm::cambiaHoraDeAviso
            )

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
                    "las que trae Ollin Finanzas de fabrica son solo un punto de partida.",
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
                "$movimientos movimientos guardados en este telefono. Ollin Finanzas no manda nada a " +
                    "ningun servidor y el respaldo automatico del sistema esta desactivado " +
                    "para la base: tu respaldo es la exportacion a .xlsx, que decides tu donde guardar.",
                style = MaterialTheme.typography.bodyMedium,
                color = colores.textoTenue
            )

            HorizontalDivider()

            Text("Ayuda", style = MaterialTheme.typography.titleMedium)

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Mostrar tutoriales", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Apagalo cuando ya te sepas la app y desaparecen sus atajos del " +
                            "tablero y de las pantallas. Los tutoriales se siguen abriendo " +
                            "desde aqui.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colores.textoTenue
                    )
                }
                Switch(
                    checked = ajustes.muestraTutoriales,
                    onCheckedChange = vm::cambiaMuestraTutoriales
                )
            }

            TextButton(onClick = alAbrirTutoriales, modifier = Modifier.fillMaxWidth()) {
                Text("Tutoriales de la app")
            }

            HorizontalDivider()

            Text("Actualizaciones", style = MaterialTheme.typography.titleMedium)

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Avisarme de versiones nuevas", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Ollin Finanzas no se instala desde Play, asi que nadie mas avisa de una " +
                            "version nueva. Una vez al dia le pregunta al sitio del proyecto si " +
                            "salio alguna, y lo enseña en Acerca de. Es una peticion a un archivo " +
                            "fijo que no manda ningun dato tuyo: la comparacion pasa en el " +
                            "telefono. Apagado, la app no toca internet en ningun momento.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colores.textoTenue
                    )
                }
                Switch(
                    checked = ajustes.buscarActualizaciones,
                    onCheckedChange = vm::cambiaBuscarActualizaciones
                )
            }
            TextButton(onClick = alAbrirAcercaDe, modifier = Modifier.fillMaxWidth()) {
                Text("Acerca de Ollin Finanzas")
            }

            Spacer(Modifier.height(24.dp))
            TextButton(onClick = alCerrar, modifier = Modifier.fillMaxWidth()) { Text("Volver") }
        }
    }
}

/**
 * La hora del aviso diario de compromisos.
 *
 * Estaba clavada en las nueve de la mañana, que a quien se levanta a las cinco
 * le llega tarde y a quien revisa sus cuentas de noche no le sirve. Moverla es
 * un ajuste, no una recompilacion.
 */
@Composable
private fun SeccionAvisos(
    ajustes: Ajustes,
    alCambiarHora: (Int, Int) -> Unit
) {
    val contexto = LocalContext.current
    val colores = LocalColoresOllin.current
    val de24Horas = remember(contexto) { DateFormat.is24HourFormat(contexto) }
    var eligiendoHora by remember { mutableStateOf(false) }

    Text("Avisos", style = MaterialTheme.typography.titleMedium)
    Text(
        "Una vez al dia Ollin Finanzas revisa tus compromisos y te avisa de los que " +
            "entraron en su ventana de aviso o ya se pasaron de fecha. Cada compromiso " +
            "decide con cuantos dias de anticipacion quiere sonar; aqui se elige a que " +
            "hora se hace esa revision.",
        style = MaterialTheme.typography.bodySmall,
        color = colores.textoTenue
    )

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Hora del aviso", style = MaterialTheme.typography.bodyLarge)
            Text(
                Recordatorios.formateaHora(ajustes.horaAviso, ajustes.minutoAviso, de24Horas),
                style = MaterialTheme.typography.bodyMedium,
                color = colores.textoTenue
            )
        }
        TextButton(onClick = { eligiendoHora = true }) { Text("Cambiar") }
    }

    Text(
        "El sistema puede correrlo unos minutos para ahorrar bateria: es una hora " +
            "aproximada, no una alarma de despertador.",
        style = MaterialTheme.typography.bodySmall,
        color = colores.textoTenue
    )

    if (eligiendoHora) {
        DialogoHoraDeAviso(
            hora = ajustes.horaAviso,
            minuto = ajustes.minutoAviso,
            de24Horas = de24Horas,
            alGuardar = { hora, minuto ->
                eligiendoHora = false
                alCambiarHora(hora, minuto)
                // La alarma en pie sigue apuntando a la hora vieja. Guardar la
                // preferencia no la mueve: hay que volver a ponerla aqui mismo,
                // o el cambio no se notaria hasta reiniciar el telefono.
                Recordatorios.reprogramaRevisionDiaria(contexto, hora, minuto)
            },
            alCancelar = { eligiendoHora = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogoHoraDeAviso(
    hora: Int,
    minuto: Int,
    de24Horas: Boolean,
    alGuardar: (Int, Int) -> Unit,
    alCancelar: () -> Unit
) {
    val estado = rememberTimePickerState(
        initialHour = hora,
        initialMinute = minuto,
        is24Hour = de24Horas
    )

    AlertDialog(
        onDismissRequest = alCancelar,
        title = { Text("Hora del aviso") },
        text = {
            // El reloj es alto y en pantallas cortas no cabe entero.
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TimePicker(state = estado)
            }
        },
        confirmButton = {
            TextButton(onClick = { alGuardar(estado.hour, estado.minute) }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = alCancelar) { Text("Cancelar") } }
    )
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
        "Ollin Finanzas pide la llave al abrirse y al volver despues de un minuto fuera. " +
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
            ModoBloqueo.NINGUNO ->
                "Cualquiera que tome tu telefono desbloqueado puede abrir Ollin Finanzas."
            ModoBloqueo.SISTEMA -> "Se usa el patron, PIN o huella con que desbloqueas el telefono. " +
                "Ollin Finanzas no guarda ningun secreto."
            ModoBloqueo.PIN -> "Se usa un PIN solo de Ollin Finanzas. Si lo olvidas no hay forma de " +
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
