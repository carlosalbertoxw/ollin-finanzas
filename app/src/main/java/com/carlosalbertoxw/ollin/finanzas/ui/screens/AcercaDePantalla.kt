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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.carlosalbertoxw.ollin.finanzas.BuildConfig
import com.carlosalbertoxw.ollin.finanzas.R
import com.carlosalbertoxw.ollin.finanzas.data.actualizaciones.ComprobadorActualizaciones
import com.carlosalbertoxw.ollin.finanzas.data.actualizaciones.Resultado
import com.carlosalbertoxw.ollin.finanzas.data.actualizaciones.Version
import com.carlosalbertoxw.ollin.finanzas.data.actualizaciones.VersionPublicada
import com.carlosalbertoxw.ollin.finanzas.data.prefs.Ajustes
import com.carlosalbertoxw.ollin.finanzas.data.prefs.AjustesRepositorio
import com.carlosalbertoxw.ollin.finanzas.ui.recuerdaVm
import com.carlosalbertoxw.ollin.finanzas.ui.theme.LocalColoresOllin

class AcercaDeVm(
    private val comprobador: ComprobadorActualizaciones,
    ajustes: AjustesRepositorio,
    private val instalada: Version?
) : ViewModel() {

    private val _reciente = MutableStateFlow<Resultado?>(null)

    /**
     * Lo que se sabe de la version publicada: lo que dejo la comprobacion
     * diaria, y encima lo que conteste una pedida a mano.
     *
     * Sin lo guardado, entrar aqui despues de que la comprobacion automatica
     * encontrara una version nueva no enseñaria nada: habria que pulsar el
     * boton para volver a preguntar lo que la app ya sabia.
     */
    val estado: StateFlow<Resultado?> =
        combine(ajustes.ajustes, _reciente) { preferencias, reciente ->
            reciente ?: loGuardado(preferencias)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun loGuardado(preferencias: Ajustes): Resultado? {
        val publicada = Version.de(preferencias.versionPublicada) ?: return null
        val url = preferencias.urlDeDescarga ?: return null

        return if (instalada != null && publicada <= instalada) {
            Resultado.AlDia
        } else {
            Resultado.HayVersionNueva(
                VersionPublicada(publicada, url, preferencias.notasDeVersion)
            )
        }
    }

    private val _comprobando = MutableStateFlow(false)
    val comprobando: StateFlow<Boolean> = _comprobando

    /**
     * La comprobacion a peticion no mira el reloj ni el interruptor de Ajustes:
     * tocar un boton y que no ocurra nada se lee como una app rota. Lo que si
     * respeta es el resto del trato --un GET al archivo del sitio, sin mandar
     * nada--.
     */
    fun compruebaAhora() {
        if (_comprobando.value) return
        viewModelScope.launch {
            _comprobando.value = true
            _reciente.value = runCatching { comprobador.compruebaAhora() }
                .getOrElse { Resultado.Fallo("No se pudo consultar el sitio.") }
            _comprobando.value = false
        }
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
    comprobador: ComprobadorActualizaciones,
    ajustes: AjustesRepositorio,
    instalada: Version?,
    alAbrirTutoriales: () -> Unit,
    alCerrar: () -> Unit
) {
    val contexto = LocalContext.current
    val colores = LocalColoresOllin.current
    val vm = recuerdaVm("acercaDe") { AcercaDeVm(comprobador, ajustes, instalada) }
    val estado by vm.estado.collectAsStateWithLifecycle()
    val comprobando by vm.comprobando.collectAsStateWithLifecycle()

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
                        "Versión ${BuildConfig.VERSION_NAME}  ·  build ${BuildConfig.VERSION_CODE}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colores.textoTenue
                    )
                }
            }

            HorizontalDivider()

            SeccionVersion(
                estado = estado,
                comprobando = comprobando,
                alComprobar = vm::compruebaAhora,
                alAbrir = { url ->
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
 * Si hay una version mas nueva que la que corre.
 *
 * Ollin Finanzas no se instala desde Play, asi que este es el unico lugar donde
 * alguien puede enterarse de que salio una version: sin el, el APK que bajaste
 * es el que te quedas para siempre. Enseña el enlace, nunca descarga ni instala
 * nada: eso lo decide quien mira la pantalla, en su navegador.
 */
@Composable
private fun SeccionVersion(
    estado: Resultado?,
    comprobando: Boolean,
    alComprobar: () -> Unit,
    alAbrir: (String) -> Unit
) {
    val colores = LocalColoresOllin.current
    val nueva = estado as? Resultado.HayVersionNueva

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Actualizaciones", style = MaterialTheme.typography.titleMedium)
            Text(
                when {
                    comprobando -> "Preguntando al sitio..."
                    nueva != null -> "Hay una versión nueva: ${nueva.publicada.version}"
                    estado is Resultado.AlDia -> "Tienes la versión más reciente."
                    estado is Resultado.Fallo -> estado.motivo
                    else -> "Se comprueba una vez al día. Se apaga en Ajustes."
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (nueva != null) colores.entrada else colores.textoTenue
            )
        }
        TextButton(onClick = alComprobar, enabled = !comprobando) {
            if (comprobando) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(8.dp))
            }
            Text("Comprobar")
        }
    }

    nueva?.let { hallada ->
        hallada.publicada.notas?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = colores.textoTenue)
        }
        TextButton(
            onClick = { alAbrir(hallada.publicada.url) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Ir a la descarga") }
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
