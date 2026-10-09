package com.carlosalbertoxw.ollin.finanzas.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.carlosalbertoxw.ollin.finanzas.BuildConfig
import com.carlosalbertoxw.ollin.finanzas.R
import com.carlosalbertoxw.ollin.finanzas.data.actualizaciones.ComprobadorActualizaciones
import com.carlosalbertoxw.ollin.finanzas.data.actualizaciones.DestinosPermitidos
import com.carlosalbertoxw.ollin.finanzas.data.actualizaciones.Resultado
import com.carlosalbertoxw.ollin.finanzas.data.actualizaciones.Version
import com.carlosalbertoxw.ollin.finanzas.data.actualizaciones.VersionPublicada
import com.carlosalbertoxw.ollin.finanzas.data.diagnostico.RegistroDeFallos
import com.carlosalbertoxw.ollin.finanzas.data.prefs.Ajustes
import com.carlosalbertoxw.ollin.finanzas.data.prefs.AjustesRepositorio
import com.carlosalbertoxw.ollin.finanzas.ui.recuerdaVm
import com.carlosalbertoxw.ollin.finanzas.ui.theme.LocalColoresOllin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AcercaDeVm(
    private val comprobador: ComprobadorActualizaciones,
    ajustes: AjustesRepositorio,
    private val instalada: Version?
) : ViewModel() {

    private val pedidaAMano = MutableStateFlow<Resultado?>(null)

    /**
     * Lo que se sabe de la version publicada: lo que dejo la comprobacion
     * diaria, y encima lo que conteste una pedida a mano.
     *
     * Sin lo guardado, entrar aqui despues de que la comprobacion automatica
     * encontrara una version nueva no enseñaria nada: habria que pulsar el
     * boton para volver a preguntar lo que la app ya sabia.
     */
    val estado: StateFlow<Resultado?> =
        combine(ajustes.ajustes, pedidaAMano) { preferencias, reciente ->
            reciente ?: loGuardado(preferencias)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun loGuardado(preferencias: Ajustes): Resultado? {
        val publicada = Version.de(preferencias.versionPublicada) ?: return null
        // Lo guardado pudo escribirlo una version anterior, que aceptaba
        // cualquier https: se vuelve a pasar por la misma lista.
        val url = preferencias.urlDeDescarga
            ?.let { DestinosPermitidos.apk(it) ?: DestinosPermitidos.sitio(it) }
            ?: return null

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
            pedidaAMano.value = runCatching { comprobador.compruebaAhora() }
                .getOrElse { Resultado.Fallo("No se pudo consultar el sitio.") }
            _comprobando.value = false
        }
    }
}
