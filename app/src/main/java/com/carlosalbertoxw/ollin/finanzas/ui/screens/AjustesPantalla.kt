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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.carlosalbertoxw.ollin.finanzas.data.notify.Recordatorios
import com.carlosalbertoxw.ollin.finanzas.data.prefs.Ajustes
import com.carlosalbertoxw.ollin.finanzas.data.prefs.AjustesRepositorio
import com.carlosalbertoxw.ollin.finanzas.data.prefs.ModoBloqueo
import com.carlosalbertoxw.ollin.finanzas.data.repo.FinanzasRepositorio
import com.carlosalbertoxw.ollin.finanzas.data.seguridad.ControlBloqueo
import com.carlosalbertoxw.ollin.finanzas.ui.AvisosEnRiesgo
import com.carlosalbertoxw.ollin.finanzas.ui.recuerdaPidePermisoDeAvisos
import com.carlosalbertoxw.ollin.finanzas.ui.recuerdaVm
import com.carlosalbertoxw.ollin.finanzas.ui.seguridad.DialogoNuevoPin
import com.carlosalbertoxw.ollin.finanzas.ui.seguridad.DialogoPinActual
import com.carlosalbertoxw.ollin.finanzas.ui.seguridad.pedirCredencialDelSistema
import com.carlosalbertoxw.ollin.finanzas.ui.seguridad.telefonoAsegurado
import com.carlosalbertoxw.ollin.finanzas.ui.theme.LocalColoresOllin
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AjustesPantalla(
    ajustes: AjustesRepositorio,
    repo: FinanzasRepositorio,
    bloqueo: ControlBloqueo,
    alAbrirCuentas: () -> Unit,
    alAbrirCategorias: () -> Unit,
    alAbrirCompromisos: () -> Unit,
    alAbrirArchivo: () -> Unit,
    alAbrirTutoriales: () -> Unit,
    alAbrirAcercaDe: () -> Unit,
    alCerrar: () -> Unit
) {
    val vm = recuerdaVm("ajustes") { AjustesVm(ajustes, repo, bloqueo) }
    val ajustes by vm.ajustes.collectAsStateWithLifecycle()
    val movimientos by vm.movimientos.collectAsStateWithLifecycle()
    val colores = LocalColoresOllin.current
    // Encender un aviso y conceder el permiso son la misma intencion, asi que
    // van en el mismo gesto: si el sistema lo niega, el interruptor no se
    // enciende, porque quedaria prometiendo algo que no puede cumplir.
    val pideAvisos = recuerdaPidePermisoDeAvisos()

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
                        "El saldo inicial solo se ocupa al dar de alta una cuenta. Apágalo " +
                            "cuando ya hayas capturado los tuyos y la captura queda más corta. " +
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
                alCambiarHora = vm::cambiaHoraDeAviso,
                pideAvisos = pideAvisos
            )

            HorizontalDivider()

            SeccionBloqueo(
                ajustes = ajustes,
                bloqueo = bloqueo,
                alQuitar = vm::quitaBloqueo,
                alUsarSistema = vm::usaBloqueoDelSistema,
                alUsarPin = vm::usaBloqueoConPin
            )

            HorizontalDivider()

            Text("Catálogo", style = MaterialTheme.typography.titleMedium)
            Text(
                "Las cuentas y las categorías con las que capturas. Renómbralas a lo tuyo: " +
                    "las que trae Ollin Finanzas de fábrica son solo un punto de partida.",
                style = MaterialTheme.typography.bodySmall,
                color = colores.textoTenue
            )
            TextButton(onClick = alAbrirCuentas, modifier = Modifier.fillMaxWidth()) {
                Text("Administrar cuentas")
            }
            TextButton(onClick = alAbrirCategorias, modifier = Modifier.fillMaxWidth()) {
                Text("Administrar categorías")
            }
            // Entrada fija a compromisos. La del tablero solo aparece cuando ya
            // hay alguno por vencer, asi que sin esta no habria forma de dar de
            // alta el primero.
            TextButton(onClick = alAbrirCompromisos, modifier = Modifier.fillMaxWidth()) {
                Text("Administrar compromisos")
            }
            // Archivo dejo de ser pestaña y esta es su entrada. El aviso
            // semanal de respaldo sigue llevando ahi directo desde la barra de
            // notificaciones, que es cuando de verdad urge.
            TextButton(onClick = alAbrirArchivo, modifier = Modifier.fillMaxWidth()) {
                Text("Importar y exportar (.xlsx)")
            }

            HorizontalDivider()

            Text("Tus datos", style = MaterialTheme.typography.titleMedium)
            Text(
                "$movimientos movimientos guardados en este teléfono. Ollin Finanzas no manda " +
                    "nada a ningún servidor y el respaldo automático del sistema está " +
                    "desactivado para la base: tu respaldo es la exportación a .xlsx, que " +
                    "decides tú dónde guardar.",
                style = MaterialTheme.typography.bodyMedium,
                color = colores.textoTenue
            )

            HorizontalDivider()

            Text("Ayuda", style = MaterialTheme.typography.titleMedium)

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Mostrar tutoriales", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Apágalo cuando ya te sepas la app y desaparecen sus atajos del " +
                            "tablero y de las pantallas. Los tutoriales se siguen abriendo " +
                            "desde aquí.",
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

            Text("Respaldo", style = MaterialTheme.typography.titleMedium)

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Recordarme respaldar", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Un aviso cada semana si no has exportado, y otro cuando salga una " +
                            "versión nueva. La base va cifrada con una llave que no se respalda " +
                            "ni viaja a otro teléfono: el .xlsx que tú exportas es lo único que " +
                            "sobrevive a perder este.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colores.textoTenue
                    )
                }
                Switch(
                    checked = ajustes.recuerdaRespaldo,
                    onCheckedChange = { quiere ->
                        // El permiso se pide solo al encenderlo: preguntarlo al
                        // abrir Ajustes seria pedir algo que quiza nadie quiere.
                        if (quiere) {
                            pideAvisos(vm::cambiaRecuerdaRespaldo)
                        } else {
                            vm.cambiaRecuerdaRespaldo(false)
                        }
                    }
                )
            }

            HorizontalDivider()

            Text("Actualizaciones", style = MaterialTheme.typography.titleMedium)

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Avisarme de versiones nuevas", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Ollin Finanzas no se instala desde Play, así que nadie más avisa de " +
                            "una versión nueva. Una vez al día le pregunta al sitio del " +
                            "proyecto si salió alguna, y lo enseña en Acerca de. Es una " +
                            "petición a un archivo fijo que no manda ningún dato tuyo: la " +
                            "comparación pasa en el teléfono, y GitHub Pages, que sirve el " +
                            "sitio, solo ve tu IP. Apagado, la app no toca internet " +
                            "en ningún momento.",
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
    alCambiarHora: (Int, Int) -> Unit,
    pideAvisos: ((Boolean) -> Unit) -> Unit
) {
    val contexto = LocalContext.current
    val colores = LocalColoresOllin.current
    val de24Horas = remember(contexto) { DateFormat.is24HourFormat(contexto) }
    var eligiendoHora by remember { mutableStateOf(false) }

    Text("Avisos", style = MaterialTheme.typography.titleMedium)
    Text(
        "Una vez al día Ollin Finanzas revisa tus compromisos y te avisa de los que " +
            "entraron en su ventana de aviso o ya se pasaron de fecha. Cada compromiso " +
            "decide con cuántos días de anticipación quiere sonar; aquí se elige a qué " +
            "hora se hace esa revisión.",
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

    AvisosEnRiesgo(pideAvisos)

    Text(
        "El sistema puede correrlo unos minutos para ahorrar batería: es una hora " +
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
    bloqueo: ControlBloqueo,
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
        titulo = "Confirma que eres tú",
        alLograr = {
            pendiente?.invoke()
            pendiente = null
        },
        alFallar = {
            pendiente = null
            aviso = it
        },
        alSalirAlSistema = bloqueo::esperaVueltaDelSistema,
        alNoSalirAlSistema = bloqueo::cancelaVueltaEsperada
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

            ModoBloqueo.SISTEMA -> {
                pendiente = accion
                confirmaConSistema()
            }

            ModoBloqueo.PIN -> {
                pendiente = accion
                pidiendoPinActual = true
            }
        }
    }

    Text("Bloqueo", style = MaterialTheme.typography.titleMedium)
    Text(
        "Ollin Finanzas pide la llave al abrirse y al volver después de un minuto fuera. " +
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
                            if (telefonoAsegurado) {
                                conConfirmacion(alUsarSistema)
                            } else {
                                aviso = "Tu teléfono no tiene patrón, PIN ni contraseña. " +
                                    "Configúralo en los ajustes de Android y vuelve aquí."
                            }

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
        Text(
            it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }

    Text(
        when (modoActual) {
            ModoBloqueo.NINGUNO ->
                "Cualquiera que tome tu teléfono desbloqueado puede abrir Ollin Finanzas."

            ModoBloqueo.SISTEMA ->
                "Se usa el patrón, PIN o huella con que desbloqueas el teléfono. " +
                    "Ollin Finanzas no guarda ningún secreto."

            ModoBloqueo.PIN ->
                "Se usa un PIN solo de Ollin Finanzas. Si lo olvidas no hay forma de " +
                    "recuperarlo: tendrías que reinstalar la app y perderías los datos."
        },
        style = MaterialTheme.typography.bodySmall,
        color = colores.textoTenue
    )

    Text(
        "La base de datos está cifrada con AES-256 y su llave vive en el Keystore del " +
            "teléfono. El archivo no dice nada aunque alguien lo saque por USB.",
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
            bloqueo = bloqueo,
            alConfirmar = {
                pidiendoPinActual = false
                pendiente?.invoke()
                pendiente = null
            },
            alCancelar = {
                pidiendoPinActual = false
                pendiente = null
            }
        )
    }

    if (pidiendoPinNuevo) {
        DialogoNuevoPin(
            alGuardar = { pin ->
                alUsarPin(pin)
                pidiendoPinNuevo = false
            },
            alCancelar = { pidiendoPinNuevo = false }
        )
    }
}
