package com.carlosalbertoxw.ollin.finanzas.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.carlosalbertoxw.ollin.finanzas.R
import com.carlosalbertoxw.ollin.finanzas.data.actualizacion.Actualizaciones
import com.carlosalbertoxw.ollin.finanzas.data.actualizacion.BuscadorDeActualizaciones
import com.carlosalbertoxw.ollin.finanzas.data.actualizacion.ResultadoBusqueda
import com.carlosalbertoxw.ollin.finanzas.data.actualizacion.VersionInstalada
import com.carlosalbertoxw.ollin.finanzas.data.prefs.Ajustes
import com.carlosalbertoxw.ollin.finanzas.data.prefs.AjustesRepositorio
import com.carlosalbertoxw.ollin.finanzas.ui.recuerdaVm
import com.carlosalbertoxw.ollin.finanzas.ui.theme.LocalColoresOllin

class AcercaDeVm(
    private val prefs: AjustesRepositorio,
    private val buscador: BuscadorDeActualizaciones,
    val version: VersionInstalada
) : ViewModel() {

    val ajustes: StateFlow<Ajustes> = prefs.ajustes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Ajustes())

    private val _buscando = MutableStateFlow(false)
    val buscando: StateFlow<Boolean> = _buscando

    /** Lo que dejo la ultima busqueda pedida a mano. */
    private val _aviso = MutableStateFlow<String?>(null)
    val aviso: StateFlow<String?> = _aviso

    /**
     * La busqueda a peticion no mira el interruptor ni el intervalo: tocar un
     * boton y que no ocurra nada se lee como una app rota. Lo que si respeta
     * es el resto del trato -- un GET al archivo del sitio, sin mandar nada.
     */
    fun buscaAhora() {
        if (_buscando.value) return
        viewModelScope.launch {
            _buscando.value = true
            _aviso.value = null
            val resultado = runCatching { buscador.busca(forzada = true) }
                .getOrDefault(ResultadoBusqueda.SIN_RESPUESTA)
            _buscando.value = false
            _aviso.value = when (resultado) {
                ResultadoBusqueda.HAY_NOVEDAD -> null   // lo dice la tarjeta, con su enlace
                ResultadoBusqueda.AL_DIA -> "Ya tienes la version mas reciente."
                else -> "No se pudo preguntar. Revisa tu conexion e intentalo otra vez."
            }
        }
    }

    fun cambiaBusquedaAutomatica(valor: Boolean) {
        viewModelScope.launch { prefs.guardaBuscaActualizaciones(valor) }
    }
}

/**
 * Quien es la app, de donde viene el nombre, que version traes y como trata
 * tus datos.
 *
 * Vive aparte de Ajustes a proposito: lo de aqui se lee de corrido, y mezclarlo
 * con los interruptores alargaria la pantalla que si se usa a diario. La unica
 * excepcion es el interruptor de la busqueda de versiones, que va pegado a lo
 * que enciende y apaga.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcercaDePantalla(
    ajustes: AjustesRepositorio,
    buscador: BuscadorDeActualizaciones,
    version: VersionInstalada,
    alAbrirTutoriales: () -> Unit,
    alCerrar: () -> Unit
) {
    val contexto = LocalContext.current
    val colores = LocalColoresOllin.current
    val vm = recuerdaVm("acercaDe") { AcercaDeVm(ajustes, buscador, version) }
    val preferencias by vm.ajustes.collectAsStateWithLifecycle()
    val buscando by vm.buscando.collectAsStateWithLifecycle()
    val aviso by vm.aviso.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Acerca de") },
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
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_ollin_glyph),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp)
                )
                Column(Modifier.weight(1f)) {
                    Text("Ollin Finanzas", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "El libro de tus movimientos",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colores.textoTenue
                    )
                    Text(
                        "Version $version",
                        style = MaterialTheme.typography.bodySmall,
                        color = colores.textoTenue
                    )
                }
            }

            HorizontalDivider()

            SeccionVersion(
                version = version,
                ajustes = preferencias,
                buscando = buscando,
                aviso = aviso,
                alBuscar = vm::buscaAhora,
                alCambiarAutomatica = vm::cambiaBusquedaAutomatica,
                alAbrirDescarga = { url ->
                    runCatching {
                        contexto.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            )

            HorizontalDivider()

            Text("De donde viene el nombre", style = MaterialTheme.typography.titleMedium)
            Text(
                "Ollin es \"movimiento\" en nahuatl, y es el nombre del glifo del calendario " +
                    "mexica que representa el cambio. Un libro de finanzas no es mas que el " +
                    "registro de tus movimientos, y de ahi sale tambien el icono: dos listones " +
                    "cruzados, el jade para lo que entra y la grana para lo que sale, con el " +
                    "saldo en el centro.",
                style = MaterialTheme.typography.bodyMedium,
                color = colores.textoTenue
            )

            HorizontalDivider()

            Text("Que hace", style = MaterialTheme.typography.titleMedium)
            Vineta(
                "Registra entradas, salidas, transferencias y saldos de apertura, con " +
                    "cuentas y categorias de listas cerradas."
            )
            Vineta(
                "Separa el patrimonio del consumo: comprar un terreno o cripto es trasladar " +
                    "patrimonio, no gastarlo, y los tableros lo cuentan aparte."
            )
            Vineta(
                "Vigila la salud de tus datos y repara con un boton lo que se puede reparar solo."
            )
            Vineta(
                "Lleva presupuesto por categoria, tendencia mensual y compromisos por vencer."
            )
            Vineta("Importa y exporta libros .xlsx que abren igual en Excel, WPS, LibreOffice y Sheets.")

            HorizontalDivider()

            Text("Tu privacidad", style = MaterialTheme.typography.titleMedium)
            Text(
                "Todo vive en este telefono. No hay cuentas de usuario, ni servidor, ni " +
                    "analitica, ni publicidad: nada de lo que capturas sale de aqui, salvo " +
                    "el .xlsx que tu exportas a donde tu decides.",
                style = MaterialTheme.typography.bodyMedium,
                color = colores.textoTenue
            )
            Text(
                "La app hace una sola llamada a internet: preguntarle al sitio del proyecto " +
                    "si hay una version mas nueva, una vez al dia. Es una peticion a un " +
                    "archivo fijo que no manda ningun dato tuyo -- ni siquiera que version " +
                    "traes -- y se apaga aqui mismo.",
                style = MaterialTheme.typography.bodySmall,
                color = colores.textoTenue
            )
            Text(
                "La base de datos esta cifrada con AES-256 y su llave vive en el Keystore del " +
                    "telefono. El respaldo automatico del sistema esta desactivado para la base " +
                    "a proposito: tu respaldo es la exportacion a .xlsx.",
                style = MaterialTheme.typography.bodySmall,
                color = colores.textoTenue
            )

            HorizontalDivider()

            Text("Como estan hechas las cuentas", style = MaterialTheme.typography.titleMedium)
            Text(
                "Los importes se guardan en centavos enteros, nunca en decimales flotantes: " +
                    "por eso un saldo en cero es exactamente cero y las conciliaciones cuadran.",
                style = MaterialTheme.typography.bodyMedium,
                color = colores.textoTenue
            )
            Text(
                "Las hojas de analisis se exportan con formulas vivas en vez de tablas " +
                    "dinamicas, para que se recalculen solas en cualquier suite de oficina.",
                style = MaterialTheme.typography.bodySmall,
                color = colores.textoTenue
            )

            HorizontalDivider()

            Text("¿Apenas empiezas?", style = MaterialTheme.typography.titleMedium)
            Text(
                "Los tutoriales explican paso a paso la captura, las transferencias, el " +
                    "presupuesto y el respaldo.",
                style = MaterialTheme.typography.bodySmall,
                color = colores.textoTenue
            )
            TextButton(onClick = alAbrirTutoriales, modifier = Modifier.fillMaxWidth()) {
                Text("Ver los tutoriales")
            }

            Spacer(Modifier.height(24.dp))
            TextButton(onClick = alCerrar, modifier = Modifier.fillMaxWidth()) { Text("Volver") }
        }
    }
}

/**
 * Que version traes y si hay una mas nueva.
 *
 * Ollin Finanzas no se instala desde Play, asi que este es el unico lugar donde
 * alguien puede enterarse de que salio una version: sin el, el APK que bajaste
 * es el que te quedas para siempre.
 */
@Composable
private fun SeccionVersion(
    version: VersionInstalada,
    ajustes: Ajustes,
    buscando: Boolean,
    aviso: String?,
    alBuscar: () -> Unit,
    alCambiarAutomatica: (Boolean) -> Unit,
    alAbrirDescarga: (String) -> Unit
) {
    val colores = LocalColoresOllin.current
    val hayNovedad = Actualizaciones.hayNovedad(ajustes.versionPublicada, version.codigo)

    Text("Tu version", style = MaterialTheme.typography.titleMedium)

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(version.nombre, style = MaterialTheme.typography.bodyLarge)
            Text(
                if (version.esConocida) "Build ${version.codigo}" else "No se pudo leer del sistema",
                style = MaterialTheme.typography.bodySmall,
                color = colores.textoTenue
            )
        }
        TextButton(onClick = alBuscar, enabled = !buscando) {
            if (buscando) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(8.dp))
            }
            Text("Buscar ahora")
        }
    }

    if (hayNovedad) {
        Text(
            "Hay una version nueva: ${ajustes.nombreVersionPublicada ?: "mas reciente"}.",
            style = MaterialTheme.typography.bodyMedium,
            color = colores.entrada
        )
        TextButton(
            onClick = { alAbrirDescarga(ajustes.urlVersionPublicada ?: Actualizaciones.SITIO) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Ir a la descarga") }
    }

    aviso?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = colores.textoTenue)
    }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Avisarme de versiones nuevas", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Pregunta una vez al dia si salio una version. Apagado, la app no toca " +
                    "internet en ningun momento.",
                style = MaterialTheme.typography.bodySmall,
                color = colores.textoTenue
            )
        }
        Switch(checked = ajustes.buscaActualizaciones, onCheckedChange = alCambiarAutomatica)
    }
}

/** Renglon de vineta. Se repite lo suficiente para no escribirlo a mano cada vez. */
@Composable
private fun Vineta(texto: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("·", style = MaterialTheme.typography.bodyMedium, color = LocalColoresOllin.current.textoTenue)
        Text(
            texto,
            style = MaterialTheme.typography.bodyMedium,
            color = LocalColoresOllin.current.textoTenue
        )
    }
}
