package com.carlosalbertoxw.ollin.finanzas.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.carlosalbertoxw.ollin.finanzas.data.db.Categoria
import com.carlosalbertoxw.ollin.finanzas.data.repo.FinanzasRepositorio
import com.carlosalbertoxw.ollin.finanzas.domain.model.TipoCategoria
import com.carlosalbertoxw.ollin.finanzas.ui.components.SeccionTitulo
import com.carlosalbertoxw.ollin.finanzas.ui.recuerdaVm
import com.carlosalbertoxw.ollin.finanzas.ui.theme.LocalColoresOllin
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Una categoria con lo que la pantalla necesita saber para decidir que se puede hacer con ella. */
data class RenglonCategoria(
    val categoria: Categoria,
    val movimientos: Int,
    val hijas: Int
) {
    val esRaiz: Boolean get() = categoria.padreId == null

    /**
     * Borrar solo es seguro cuando nada cuelga de la categoria. Con movimientos,
     * la clave foranea los dejaria sin categoria en silencio; con hijas, las
     * subiria a raiz. En esos casos se archiva.
     */
    val sePuedeBorrar: Boolean get() = movimientos == 0 && hijas == 0
}
class CategoriasVm(private val repo: FinanzasRepositorio) : ViewModel() {

    val renglones: StateFlow<List<RenglonCategoria>> = combine(
        repo.observaTodasLasCategorias(),
        repo.observaUsoDeCategorias()
    ) { categorias, uso ->
        val porCategoria = uso.associate { it.categoriaId to it.movimientos }
        val hijasPorPadre = categorias.groupingBy { it.padreId }.eachCount()
        categorias.map {
            RenglonCategoria(
                categoria = it,
                movimientos = porCategoria[it.id] ?: 0,
                hijas = hijasPorPadre[it.id] ?: 0
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun guarda(categoria: Categoria, alFallar: (String) -> Unit) {
        viewModelScope.launch {
            // El indice unico (nombre, padreId) rebota los duplicados. Vale mas
            // explicarlo que dejar que la excepcion se lleve la pantalla.
            runCatching { repo.guardaCategoria(categoria) }
                .onFailure { alFallar("Ya existe una categoria con ese nombre en el mismo nivel.") }
        }
    }

    fun elimina(renglon: RenglonCategoria) {
        viewModelScope.launch {
            if (renglon.sePuedeBorrar) repo.eliminaCategoria(renglon.categoria)
            else repo.guardaCategoria(renglon.categoria.copy(archivada = true))
        }
    }
}
