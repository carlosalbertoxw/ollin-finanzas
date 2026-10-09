package com.carlosalbertoxw.ollin.finanzas.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.carlosalbertoxw.ollin.finanzas.data.excel.DiagnosticoAgrupado
import com.carlosalbertoxw.ollin.finanzas.data.excel.EsquemaExportacion
import com.carlosalbertoxw.ollin.finanzas.data.excel.HojaExportable
import com.carlosalbertoxw.ollin.finanzas.data.excel.OpcionesImportacion
import com.carlosalbertoxw.ollin.finanzas.data.excel.ResultadoImportacion
import com.carlosalbertoxw.ollin.finanzas.data.excel.Severidad
import com.carlosalbertoxw.ollin.finanzas.data.excel.XlsxLector
import com.carlosalbertoxw.ollin.finanzas.data.prefs.Ajustes
import com.carlosalbertoxw.ollin.finanzas.data.prefs.AjustesRepositorio
import com.carlosalbertoxw.ollin.finanzas.data.repo.FinanzasRepositorio
import com.carlosalbertoxw.ollin.finanzas.data.seguridad.ControlBloqueo
import com.carlosalbertoxw.ollin.finanzas.domain.usecase.RevisaCalidad
import com.carlosalbertoxw.ollin.finanzas.ui.components.Marco
import com.carlosalbertoxw.ollin.finanzas.ui.components.SeccionTitulo
import com.carlosalbertoxw.ollin.finanzas.ui.recuerdaVm
import com.carlosalbertoxw.ollin.finanzas.ui.theme.LocalColoresOllin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

sealed interface EstadoArchivo {
    data object Reposo : EstadoArchivo
    data class Trabajando(val mensaje: String) : EstadoArchivo

    /**
     * [hallazgosEnSalud] es lo que la auditoria encontro en los datos ya
     * importados. Se cuenta aqui porque los avisos de la importacion hablan del
     * archivo —renglones incompletos, metas sin categoria— y casi ninguno deja
     * rastro en la base: mandar a Salud por ellos llevaba a una pantalla que
     * decia "todo cuadra".
     */
    data class Importado(
        val resultado: ResultadoImportacion,
        val hallazgosEnSalud: Int = 0
    ) : EstadoArchivo

    data class Exportado(val hojas: Int, val movimientos: Int) : EstadoArchivo
    data class Fallo(val mensaje: String) : EstadoArchivo
}
class ArchivoVm(
    private val repo: FinanzasRepositorio,
    private val prefs: AjustesRepositorio,
    private val revisaCalidad: RevisaCalidad
) : ViewModel() {

    val ajustes: StateFlow<Ajustes> = prefs.ajustes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Ajustes())

    val totalMovimientos: StateFlow<Int> = repo.observaConteoMovimientos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _estado = MutableStateFlow<EstadoArchivo>(EstadoArchivo.Reposo)
    val estado: StateFlow<EstadoArchivo> = _estado

    fun cambiaEsquema(esquema: EsquemaExportacion) {
        viewModelScope.launch { prefs.guardaEsquema(esquema) }
    }

    fun alternaHoja(hoja: HojaExportable) {
        if (hoja.obligatoria) return
        viewModelScope.launch {
            val actuales = ajustes.value.hojas
            prefs.guardaHojas(if (hoja in actuales) actuales - hoja else actuales + hoja)
        }
    }

    fun cambiaHojasPreset(hojas: Set<HojaExportable>) {
        viewModelScope.launch { prefs.guardaHojas(hojas) }
    }

    fun cambiaCorregir(valor: Boolean) {
        viewModelScope.launch { prefs.guardaCorregir(valor) }
    }

    fun cambiaReemplazar(valor: Boolean) {
        viewModelScope.launch { prefs.guardaReemplazar(valor) }
    }

    fun importa(uri: Uri) {
        _estado.value = EstadoArchivo.Trabajando("Leyendo el archivo...")
        viewModelScope.launch {
            val a = ajustes.value
            runCatching {
                repo.importa(
                    uri,
                    OpcionesImportacion(
                        corregirTipoSegunSigno = a.corregirAlImportar,
                        derivarContraparte = a.corregirAlImportar,
                        emparejarTransferencias = true,
                        reemplazarTodo = a.reemplazarAlImportar
                    )
                )
            }.fold(
                onSuccess = { resultado ->
                    // La auditoria corre aqui, sobre los datos ya importados,
                    // para saber si mandar a Salud tiene algo que ofrecer.
                    val hallazgos = runCatching { revisaCalidad.ejecuta().size }
                        .getOrDefault(0)
                    _estado.value = EstadoArchivo.Importado(resultado, hallazgos)
                },
                onFailure = { _estado.value = EstadoArchivo.Fallo(explica(it, "importar")) }
            )
        }
    }

    fun exporta(uri: Uri) {
        _estado.value = EstadoArchivo.Trabajando("Generando el libro...")
        viewModelScope.launch {
            val a = ajustes.value
            runCatching { repo.exporta(uri, a.esquema, a.hojas) }.fold(
                onSuccess = {
                    // La semana del recordatorio vuelve a contar desde aqui:
                    // acaba de hacerse lo que el aviso pediria.
                    prefs.guardaRespaldoHecho(System.currentTimeMillis())
                    _estado.value = EstadoArchivo.Exportado(
                        hojas = HojaExportable.normaliza(a.hojas).size,
                        movimientos = totalMovimientos.value
                    )
                },
                onFailure = { _estado.value = EstadoArchivo.Fallo(explica(it, "exportar")) }
            )
        }
    }

    /**
     * Traduce el fallo a algo accionable. El mensaje crudo de una excepcion
     * habla de rutas internas, clases y consultas: al usuario no le sirve de
     * nada y de paso le ensena como esta hecha la app por dentro.
     */
    private fun explica(fallo: Throwable, accion: String): String {
        // El mensaje que ve el usuario oculta los internos a proposito, asi que
        // el fallo real se manda a logcat: sin esto, un error de exportacion no
        // deja rastro de que lo causo. No lleva ningun dato del usuario.
        android.util.Log.w("OllinFinanzas", "Fallo al $accion", fallo)
        return mensajeDe(fallo, accion)
    }

    private fun mensajeDe(fallo: Throwable, accion: String): String = when (fallo) {
        // Los suyos si estan escritos para leerse; el resto no.
        is XlsxLector.ArchivoInvalido -> fallo.message ?: "El archivo no se pudo leer."
        is SecurityException -> "Ya no hay permiso sobre ese archivo. Vuelve a elegirlo."
        is java.io.IOException -> "No se pudo leer o escribir el archivo. Revisa que haya " +
            "espacio libre y que la ubicacion siga disponible."
        is OutOfMemoryError -> "El libro es demasiado grande para la memoria del telefono. " +
            "Exporta menos pestanas desde \"Solo datos\"."
        else -> "No se pudo $accion. Intenta de nuevo."
    }

    fun limpia() { _estado.value = EstadoArchivo.Reposo }

    fun avisa(mensaje: String) { _estado.value = EstadoArchivo.Fallo(mensaje) }

    fun nombreSugerido(): String = "Finanzas-${LocalDate.now()}.xlsx"
}
