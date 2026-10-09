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

class AjustesVm(
    private val prefs: AjustesRepositorio,
    private val repo: FinanzasRepositorio,
    private val bloqueo: ControlBloqueo
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

    fun cambiaRecuerdaRespaldo(valor: Boolean) {
        viewModelScope.launch {
            prefs.guardaRecuerdaRespaldo(valor, System.currentTimeMillis())
        }
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
            val (hash, sal) = bloqueo.huellaNueva(pin)
            prefs.activaBloqueoPin(hash = hash, sal = sal)
        }
    }
}
