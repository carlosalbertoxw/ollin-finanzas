package mx.ollin.finanzas.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import mx.ollin.finanzas.di.Contenedor
import mx.ollin.finanzas.domain.usecase.GravedadHallazgo
import mx.ollin.finanzas.domain.usecase.Hallazgo
import mx.ollin.finanzas.ui.components.EstadoVacio
import mx.ollin.finanzas.ui.recuerdaVm
import mx.ollin.finanzas.ui.theme.LocalColoresOllin

class CalidadVm(private val contenedor: Contenedor) : ViewModel() {

    private val _hallazgos = MutableStateFlow<List<Hallazgo>>(emptyList())
    val hallazgos: StateFlow<List<Hallazgo>> = _hallazgos

    private val _cargando = MutableStateFlow(true)
    val cargando: StateFlow<Boolean> = _cargando

    private val _ultimaReparacion = MutableStateFlow<String?>(null)
    val ultimaReparacion: StateFlow<String?> = _ultimaReparacion

    /** Se vuelve a correr cada vez que la pantalla queda al frente, no solo al crearla. */
    fun revisa() {
        viewModelScope.launch {
            _hallazgos.value = runCatching { contenedor.revisaCalidad.ejecuta() }.getOrDefault(emptyList())
            _cargando.value = false
        }
    }

    fun repara(hallazgo: Hallazgo) {
        viewModelScope.launch {
            val n = runCatching { contenedor.reparaDatos.repara(hallazgo.clave) }.getOrDefault(0)
            _ultimaReparacion.value = when {
                n > 0 -> "Se corrigieron $n movimientos."
                hallazgo.idsMovimiento.isNotEmpty() ->
                    "Ninguno se podia corregir solo. Abrelos uno por uno para resolverlos a mano."
                else -> "No hubo nada que corregir."
            }
            revisa()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalidadPantalla(
    contenedor: Contenedor,
    alRevisarHallazgo: (String) -> Unit,
    alCerrar: () -> Unit
) {
    val vm = recuerdaVm("calidad") { CalidadVm(contenedor) }
    val hallazgos by vm.hallazgos.collectAsStateWithLifecycle()
    val cargando by vm.cargando.collectAsStateWithLifecycle()
    val reparacion by vm.ultimaReparacion.collectAsStateWithLifecycle()
    val colores = LocalColoresOllin.current

    // Al volver de revisar movimientos la foto ya cambio: se vuelve a auditar.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.revisa() }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Salud de los datos") },
            navigationIcon = {
                IconButton(onClick = alCerrar) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                }
            },
            actions = { TextButton(onClick = vm::revisa) { Text("Revisar") } }
        )

        when {
            cargando -> Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) { CircularProgressIndicator() }

            hallazgos.isEmpty() -> EstadoVacio(
                icono = Icons.Filled.CheckCircle,
                titulo = "Todo cuadra",
                detalle = "No encontre contradicciones entre tipos e importes, transferencias " +
                    "a medias ni movimientos sin clasificar.",
                modifier = Modifier.fillMaxSize()
            )

            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        "Estas comprobaciones corren sobre tus datos cada vez que abres esta " +
                            "pantalla. Corregir nunca cambia un importe: solo la etiqueta que lo describe.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colores.textoTenue
                    )
                }

                reparacion?.let {
                    item {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = colores.entrada)
                    }
                }

                items(hallazgos, key = { it.clave }) { hallazgo ->
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = when (hallazgo.gravedad) {
                                GravedadHallazgo.ALTA -> MaterialTheme.colorScheme.errorContainer
                                GravedadHallazgo.MEDIA -> MaterialTheme.colorScheme.tertiaryContainer
                                GravedadHallazgo.BAJA -> MaterialTheme.colorScheme.surfaceContainer
                            }
                        )
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(hallazgo.titulo, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${hallazgo.afectados}",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(hallazgo.detalle, style = MaterialTheme.typography.bodyMedium)
                            if (hallazgo.reparable || hallazgo.idsMovimiento.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (hallazgo.reparable) {
                                        TextButton(onClick = { vm.repara(hallazgo) }) {
                                            Text("Corregir automaticamente")
                                        }
                                    }
                                    if (hallazgo.idsMovimiento.isNotEmpty()) {
                                        TextButton(onClick = { alRevisarHallazgo(hallazgo.clave) }) {
                                            Text("Ver los ${hallazgo.afectados}")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
