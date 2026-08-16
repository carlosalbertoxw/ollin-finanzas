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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.carlosalbertoxw.ollin.finanzas.data.excel.DiagnosticoAgrupado
import com.carlosalbertoxw.ollin.finanzas.data.excel.EsquemaExportacion
import com.carlosalbertoxw.ollin.finanzas.data.excel.HojaExportable
import com.carlosalbertoxw.ollin.finanzas.data.excel.OpcionesImportacion
import com.carlosalbertoxw.ollin.finanzas.data.excel.ResultadoImportacion
import com.carlosalbertoxw.ollin.finanzas.data.excel.Severidad
import com.carlosalbertoxw.ollin.finanzas.data.excel.XlsxLector
import com.carlosalbertoxw.ollin.finanzas.data.prefs.Ajustes
import com.carlosalbertoxw.ollin.finanzas.di.Contenedor
import com.carlosalbertoxw.ollin.finanzas.ui.components.Marco
import com.carlosalbertoxw.ollin.finanzas.ui.components.SeccionTitulo
import com.carlosalbertoxw.ollin.finanzas.ui.recuerdaVm
import com.carlosalbertoxw.ollin.finanzas.ui.theme.LocalColoresOllin
import java.time.LocalDate

private const val MIME_XLSX =
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

/**
 * Crea el archivo destino proponiendo Descargas como punto de partida, en vez
 * de abrir donde haya quedado la vez anterior.
 *
 * Es solo una sugerencia: si esa carpeta no existe, el selector la ignora y
 * abre donde pueda. Cuando el destino elegido no admite crear el archivo, quien
 * avisa es el propio selector con su "Error al guardar el documento"; la app no
 * recibe ningun uri y no puede distinguir ese caso de una cancelacion.
 */
private class CreaLibro : ActivityResultContracts.CreateDocument(MIME_XLSX) {
    override fun createIntent(context: Context, input: String): Intent =
        super.createIntent(context, input).apply {
            putExtra(
                DocumentsContract.EXTRA_INITIAL_URI,
                DocumentsContract.buildDocumentUri(
                    "com.android.externalstorage.documents",
                    "primary:Download"
                )
            )
        }
}

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

class ArchivoVm(private val contenedor: Contenedor) : ViewModel() {

    private val repo = contenedor.repositorio
    private val prefs = contenedor.ajustes

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
                    val hallazgos = runCatching { contenedor.revisaCalidad.ejecuta().size }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivoPantalla(contenedor: Contenedor, alAbrirCalidad: () -> Unit) {
    val vm = recuerdaVm("archivo") { ArchivoVm(contenedor) }
    val ajustes by vm.ajustes.collectAsStateWithLifecycle()
    val estado by vm.estado.collectAsStateWithLifecycle()
    val total by vm.totalMovimientos.collectAsStateWithLifecycle()
    val colores = LocalColoresOllin.current

    val abrir = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(vm::importa) }

    val crear = rememberLauncherForActivityResult(CreaLibro()) { uri ->
        uri?.let(vm::exporta)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp, 16.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Archivo", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Ollin Finanzas guarda tus datos en el telefono y usa el .xlsx como formato de " +
                "intercambio: lo lees, lo escribes, y sigue siendo tuyo.",
            style = MaterialTheme.typography.bodyMedium,
            color = colores.textoTenue
        )

        when (val e = estado) {
            is EstadoArchivo.Trabajando -> Marco {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.height(20.dp))
                    Spacer(Modifier.fillMaxWidth(0.05f))
                    Text(e.mensaje)
                }
            }

            is EstadoArchivo.Fallo -> Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("No se pudo completar", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(e.mensaje, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = vm::limpia) { Text("Entendido") }
                }
            }

            is EstadoArchivo.Exportado -> Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Libro generado", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${e.hojas} pestañas con ${e.movimientos} movimientos. " +
                            "Las hojas de analisis llevan formulas vivas: se recalculan al abrir.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TextButton(onClick = vm::limpia) { Text("Listo") }
                }
            }

            is EstadoArchivo.Importado ->
                ResumenImportacion(e.resultado, e.hallazgosEnSalud, vm::limpia, alAbrirCalidad)

            EstadoArchivo.Reposo -> Unit
        }

        // ----------------------------------------------------------- importar
        SeccionTitulo("Importar")
        Text(
            "Lee un .xlsx y reconoce sus encabezados sin importar acentos ni mayusculas. " +
                "Al entrar corrige lo que encuentre mal. Ademas de los movimientos, entran " +
                "Diccionarios, Presupuesto y Compromisos si el libro trae esas pestañas.",
            style = MaterialTheme.typography.bodySmall,
            color = colores.textoTenue
        )

        Marco {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                InterruptorConNota(
                    titulo = "Corregir al importar",
                    detalle = "Alinea el tipo con el signo del importe y recalcula si el movimiento " +
                        "es entre tus cuentas o con un tercero.",
                    valor = ajustes.corregirAlImportar,
                    alCambiar = vm::cambiaCorregir
                )
                InterruptorConNota(
                    titulo = "Reemplazar todo",
                    detalle = if (ajustes.reemplazarAlImportar)
                        "Se borran los $total movimientos actuales —y las metas y compromisos— " +
                            "y se cargan los del archivo. Las cuentas y categorias que queden " +
                            "sin un solo movimiento, incluidas las de ejemplo, tambien se van."
                    else "Lo del archivo se agrega a lo que ya tienes.",
                    valor = ajustes.reemplazarAlImportar,
                    alCambiar = vm::cambiaReemplazar
                )
            }
        }

        Button(
            onClick = {
                lanza(vm, "abrir") {
                    abrir.launch(arrayOf(MIME_XLSX, "application/octet-stream", "*/*"))
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.Upload, contentDescription = null)
            Text("  Elegir archivo .xlsx")
        }

        // ----------------------------------------------------------- exportar
        SeccionTitulo("Exportar")

        Text("Columnas de la hoja Registros", style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            EsquemaExportacion.entries.forEachIndexed { i, esquema ->
                SegmentedButton(
                    selected = ajustes.esquema == esquema,
                    onClick = { vm.cambiaEsquema(esquema) },
                    shape = SegmentedButtonDefaults.itemShape(i, EsquemaExportacion.entries.size)
                ) { Text(esquema.etiqueta) }
            }
        }
        Text(
            ajustes.esquema.descripcion,
            style = MaterialTheme.typography.bodySmall,
            color = colores.textoTenue
        )

        Spacer(Modifier.height(4.dp))
        Text("Pestañas a incluir", style = MaterialTheme.typography.labelLarge)

        HojaExportable.entries.forEach { hoja ->
            val activa = hoja in HojaExportable.normaliza(ajustes.hojas)
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Checkbox(
                    checked = activa,
                    onCheckedChange = { vm.alternaHoja(hoja) },
                    enabled = !hoja.obligatoria
                )
                Column(Modifier.padding(top = 12.dp)) {
                    Text(
                        hoja.titulo + if (hoja.obligatoria) "  (siempre)" else "",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        hoja.descripcion,
                        style = MaterialTheme.typography.bodySmall,
                        color = colores.textoTenue
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { vm.cambiaHojasPreset(HojaExportable.MINIMA) },
                modifier = Modifier.weight(1f)
            ) { Text("Solo datos") }
            OutlinedButton(
                onClick = { vm.cambiaHojasPreset(HojaExportable.PREDETERMINADAS) },
                modifier = Modifier.weight(1f)
            ) { Text("Libro completo") }
        }

        Button(
            onClick = { lanza(vm, "guardar") { crear.launch(vm.nombreSugerido()) } },
            modifier = Modifier.fillMaxWidth(),
            enabled = total > 0
        ) {
            Icon(Icons.Filled.Download, contentDescription = null)
            Text("  Exportar ${HojaExportable.normaliza(ajustes.hojas).size} pestañas")
        }

        if (total == 0) {
            Text(
                "Todavia no hay movimientos que exportar.",
                style = MaterialTheme.typography.bodySmall,
                color = colores.textoTenue
            )
        }
    }
}

/**
 * Abrir el selector de archivos del sistema es lo unico de esta pantalla que
 * corre fuera de una corrutina protegida: pasa en el hilo principal, asi que lo
 * que falle ahi se lleva la app entera. Se atrapa ancho a proposito: el caso
 * conocido es que el telefono no traiga DocumentsUI (ROMs recortadas, perfiles
 * de trabajo restringidos), pero cualquier otro fallo del sistema al abrir el
 * selector merece un mensaje, no un cierre en seco.
 */
private inline fun lanza(vm: ArchivoVm, accion: String, bloque: () -> Unit) {
    try {
        bloque()
    } catch (e: ActivityNotFoundException) {
        vm.avisa(
            "Este telefono no tiene una aplicacion de archivos con la que $accion el .xlsx. " +
                "Instala un gestor de archivos o activa el de Android."
        )
    } catch (e: Exception) {
        vm.avisa(
            "No se pudo abrir el selector de archivos para $accion el .xlsx " +
                "(${e.javaClass.simpleName}). Revisa que tu gestor de archivos este activo."
        )
    }
}

/**
 * "(renglones 4, 9 y 12)" o, si son muchos, los primeros con un "y N mas".
 * El numero de renglon es lo unico que permite ir al archivo a arreglarlo.
 */
private fun sufijoDeFilas(diagnostico: DiagnosticoAgrupado): String {
    if (diagnostico.filas.isEmpty()) return ""
    val muestra = diagnostico.filas.take(5).joinToString(", ")
    val resto = diagnostico.filas.size - 5
    val palabra = if (diagnostico.filas.size == 1) "renglon" else "renglones"
    return if (resto > 0) " ($palabra $muestra y $resto mas)" else " ($palabra $muestra)"
}

@Composable
private fun InterruptorConNota(
    titulo: String,
    detalle: String,
    valor: Boolean,
    alCambiar: (Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(titulo, style = MaterialTheme.typography.bodyLarge)
            Text(
                detalle,
                style = MaterialTheme.typography.bodySmall,
                color = LocalColoresOllin.current.textoTenue
            )
        }
        Switch(checked = valor, onCheckedChange = alCambiar)
    }
}

/**
 * El parte de la importacion.
 *
 * Los avisos se muestran aqui mismo, agrupados: hablan del archivo —renglones
 * incompletos, metas que apuntan a una categoria inexistente— y ninguno de esos
 * deja rastro en la base, asi que la pantalla de Salud no los conoce. Ella se
 * ofrece aparte y solo cuando de verdad encontro algo en los datos importados.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResumenImportacion(
    resultado: ResultadoImportacion,
    hallazgosEnSalud: Int,
    alCerrar: () -> Unit,
    alAbrirCalidad: () -> Unit
) {
    val colores = LocalColoresOllin.current
    var detalleAbierto by rememberSaveable { mutableStateOf(false) }
    val diagnosticos = remember(resultado) { resultado.diagnosticosAgrupados() }
    val problemas = diagnosticos.count { it.severidad != Severidad.INFO }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (resultado.huboProblemas) MaterialTheme.colorScheme.tertiaryContainer
            else MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Importados ${resultado.importados} de ${resultado.filasLeidas} renglones",
                style = MaterialTheme.typography.titleSmall
            )

            if (resultado.tiposCorregidos > 0) {
                Text("· ${resultado.tiposCorregidos} tipos corregidos por contradecir al importe")
            }
            if (resultado.contrapartesRecalculadas > 0) {
                Text("· ${resultado.contrapartesRecalculadas} contrapartes recalculadas")
            }
            if (resultado.transferenciasEmparejadas > 0) {
                Text("· ${resultado.transferenciasEmparejadas} transferencias emparejadas")
            }
            if (resultado.transferenciasHuerfanas > 0) {
                Text("· ${resultado.transferenciasHuerfanas} patas de transferencia sin pareja")
            }
            if (resultado.cuentasCreadas.isNotEmpty()) {
                Text("· Cuentas nuevas: ${resultado.cuentasCreadas.joinToString()}")
            }
            if (resultado.categoriasCreadas.isNotEmpty()) {
                Text("· ${resultado.categoriasCreadas.size} categorias nuevas")
            }
            if (resultado.presupuestosImportados > 0) {
                Text("· ${resultado.presupuestosImportados} metas de presupuesto")
            }
            if (resultado.compromisosImportados > 0) {
                Text("· ${resultado.compromisosImportados} compromisos")
            }
            if (resultado.sinCategoria > 0) {
                Text("· ${resultado.sinCategoria} movimientos sin categoria")
            }
            if (resultado.omitidos > 0) {
                Text("· ${resultado.omitidos} renglones omitidos por venir incompletos")
            }
            if (resultado.cuentasEliminadas.isNotEmpty() || resultado.categoriasEliminadas > 0) {
                Text(
                    "· Al reemplazar se fueron ${resultado.cuentasEliminadas.size} cuentas y " +
                        "${resultado.categoriasEliminadas} categorias que quedaron sin uso"
                )
            }

            if (detalleAbierto) {
                Spacer(Modifier.height(2.dp))
                diagnosticos.forEach { d ->
                    Text(
                        "· ${d.mensaje}${sufijoDeFilas(d)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = when (d.severidad) {
                            Severidad.ERROR -> colores.salida
                            Severidad.AVISO -> MaterialTheme.colorScheme.onSurface
                            Severidad.INFO -> colores.textoTenue
                        }
                    )
                }
            }

            // Tres botones con etiquetas largas no caben en un renglon de
            // telefono angosto, y el ultimo es justo el que hay que alcanzar.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = alCerrar) { Text("Listo") }
                if (diagnosticos.isNotEmpty()) {
                    TextButton(onClick = { detalleAbierto = !detalleAbierto }) {
                        Text(
                            when {
                                detalleAbierto -> "Ocultar detalle"
                                problemas == 1 -> "Ver 1 aviso"
                                problemas > 1 -> "Ver $problemas avisos"
                                else -> "Ver detalle"
                            }
                        )
                    }
                }
                // Solo si la auditoria encontro algo: mandar a Salud cuando no
                // hay nada que ver es el paseo que hacia esta pantalla antes.
                if (hallazgosEnSalud > 0) {
                    TextButton(onClick = alAbrirCalidad) {
                        Icon(Icons.Filled.HealthAndSafety, contentDescription = null)
                        Text("  Salud ($hallazgosEnSalud)")
                    }
                }
            }
        }
    }
}
