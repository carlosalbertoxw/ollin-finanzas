package com.carlosalbertoxw.ollin.finanzas.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.carlosalbertoxw.ollin.finanzas.data.db.Cuenta
import com.carlosalbertoxw.ollin.finanzas.data.db.SaldoCuenta
import com.carlosalbertoxw.ollin.finanzas.data.repo.FinanzasRepositorio
import com.carlosalbertoxw.ollin.finanzas.domain.model.Dinero
import com.carlosalbertoxw.ollin.finanzas.domain.model.Medio
import com.carlosalbertoxw.ollin.finanzas.domain.model.TipoCuenta
import com.carlosalbertoxw.ollin.finanzas.ui.components.SeccionTitulo
import com.carlosalbertoxw.ollin.finanzas.ui.components.TextoDinero
import com.carlosalbertoxw.ollin.finanzas.ui.recuerdaVm
import com.carlosalbertoxw.ollin.finanzas.ui.theme.LocalColoresOllin
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CuentasVm(private val repo: FinanzasRepositorio) : ViewModel() {

    val saldos: StateFlow<List<SaldoCuenta>> = repo.observaSaldos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val cuentas: StateFlow<List<Cuenta>> = repo.observaTodasLasCuentas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun guarda(cuenta: Cuenta, alLograr: () -> Unit, alFallar: (String) -> Unit) {
        viewModelScope.launch {
            // El indice unico sobre el nombre rebota los duplicados. Se revisa
            // antes para poder decir cual estorba: si la que choca esta archivada,
            // el nombre se ve libre y el rechazo no se entiende solo.
            val choque = repo.cuentaPorNombre(cuenta.nombre)
            if (choque != null && choque.id != cuenta.id) {
                alFallar(
                    "Ya existe una cuenta llamada \"${choque.nombre}\"" +
                        (if (choque.archivada) ", archivada" else "") +
                        ". Usa otro nombre."
                )
                return@launch
            }
            runCatching { repo.guardaCuenta(cuenta) }
                .onSuccess { alLograr() }
                .onFailure {
                    alFallar("No se pudo guardar la cuenta: revisa que el nombre no este repetido.")
                }
        }
    }

    fun elimina(cuenta: Cuenta) {
        // Room bloquea el borrado si la cuenta tiene movimientos (RESTRICT),
        // asi que en ese caso se archiva en vez de romper el historial.
        viewModelScope.launch {
            runCatching { repo.eliminaCuenta(cuenta) }
                .onFailure { repo.guardaCuenta(cuenta.copy(archivada = true)) }
        }
    }
}
