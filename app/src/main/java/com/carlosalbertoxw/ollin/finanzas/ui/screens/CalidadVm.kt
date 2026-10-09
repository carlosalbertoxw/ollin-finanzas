package com.carlosalbertoxw.ollin.finanzas.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import com.carlosalbertoxw.ollin.finanzas.domain.usecase.GravedadHallazgo
import com.carlosalbertoxw.ollin.finanzas.domain.usecase.Hallazgo
import com.carlosalbertoxw.ollin.finanzas.domain.usecase.ReparaDatos
import com.carlosalbertoxw.ollin.finanzas.domain.usecase.RevisaCalidad
import com.carlosalbertoxw.ollin.finanzas.ui.components.EstadoVacio
import com.carlosalbertoxw.ollin.finanzas.ui.detalle
import com.carlosalbertoxw.ollin.finanzas.ui.recuerdaVm
import com.carlosalbertoxw.ollin.finanzas.ui.theme.LocalColoresOllin
import com.carlosalbertoxw.ollin.finanzas.ui.titulo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class CalidadVm(
    private val revisaCalidad: RevisaCalidad,
    private val reparaDatos: ReparaDatos
) : ViewModel() {

    private val _hallazgos = MutableStateFlow<List<Hallazgo>>(emptyList())
    val hallazgos: StateFlow<List<Hallazgo>> = _hallazgos

    private val _cargando = MutableStateFlow(true)
    val cargando: StateFlow<Boolean> = _cargando

    /** Lo ultimo que paso por decision del usuario: una reparacion o una revision a mano. */
    private val _aviso = MutableStateFlow<String?>(null)
    val aviso: StateFlow<String?> = _aviso

    /** Corre mientras el boton de la barra esta trabajando, para que se vea que trabaja. */
    private val _revisando = MutableStateFlow(false)
    val revisando: StateFlow<Boolean> = _revisando

    /**
     * Se vuelve a correr cada vez que la pantalla queda al frente, no solo al
     * crearla. El aviso se limpia aqui: pertenece a la accion que lo produjo, y
     * volver de otra pantalla ya no es esa accion.
     */
    fun revisa() {
        viewModelScope.launch {
            _aviso.value = null
            audita()
        }
    }

    /**
     * El boton "Revisar" de la barra.
     *
     * La auditoria ya corre sola al entrar, asi que volver a correrla casi
     * siempre devuelve exactamente lo mismo y la pantalla no se mueve un pixel.
     * Sin anunciarla, el boton pareceria roto: por eso lleva indicador mientras
     * trabaja y deja una linea con el resultado, para que se distinga "no cambio
     * nada" de "no hizo nada".
     */
    fun revisaAPeticion() {
        if (_revisando.value) return
        viewModelScope.launch {
            _revisando.value = true
            val encontrados = audita()
            _revisando.value = false
            _aviso.value = when (encontrados.size) {
                0 -> "Revisado: ya no queda nada por revisar."
                1 -> "Revisado: queda 1 cosa por revisar."
                else -> "Revisado: quedan ${encontrados.size} cosas por revisar."
            }
        }
    }

    private suspend fun audita(): List<Hallazgo> {
        val encontrados = runCatching { revisaCalidad.ejecuta() }.getOrDefault(emptyList())
        _hallazgos.value = encontrados
        _cargando.value = false
        return encontrados
    }

    fun repara(hallazgo: Hallazgo) {
        viewModelScope.launch {
            val n = runCatching { reparaDatos.repara(hallazgo.clave) }.getOrDefault(0)
            _aviso.value = when {
                n > 0 -> "Se corrigieron $n movimientos."
                hallazgo.idsMovimiento.isNotEmpty() ->
                    "Ninguno se podia corregir solo. Abrelos uno por uno para resolverlos a mano."
                else -> "No hubo nada que corregir."
            }
            audita()
        }
    }
}
