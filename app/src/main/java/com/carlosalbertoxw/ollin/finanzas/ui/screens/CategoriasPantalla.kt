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
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.carlosalbertoxw.ollin.finanzas.data.db.Categoria
import com.carlosalbertoxw.ollin.finanzas.data.repo.FinanzasRepositorio
import com.carlosalbertoxw.ollin.finanzas.domain.model.TipoCategoria
import com.carlosalbertoxw.ollin.finanzas.ui.components.SeccionTitulo
import com.carlosalbertoxw.ollin.finanzas.ui.recuerdaVm
import com.carlosalbertoxw.ollin.finanzas.ui.theme.LocalColoresOllin

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriasPantalla(repo: FinanzasRepositorio, alCerrar: () -> Unit) {
    val vm = recuerdaVm("categorias") { CategoriasVm(repo) }
    val renglones by vm.renglones.collectAsStateWithLifecycle()
    val colores = LocalColoresOllin.current

    var editando by remember { mutableStateOf<Categoria?>(null) }
    var aviso by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Categorias") },
            navigationIcon = {
                IconButton(onClick = alCerrar) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                }
            },
            actions = {
                IconButton(onClick = {
                    editando = Categoria(
                        nombre = "",
                        tipo = TipoCategoria.GASTO,
                        orden = renglones.size
                    )
                }) { Icon(Icons.Filled.Add, contentDescription = "Nueva categoria") }
            }
        )

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
            TipoCategoria.entries.forEach { tipo ->
                val delTipo = renglones.filter { it.categoria.tipo == tipo }
                if (delTipo.isEmpty()) return@forEach

                item(key = "cab-${tipo.name}") {
                    SeccionTitulo(tipo.etiqueta, Modifier.padding(vertical = 10.dp)) {
                        Text(
                            "${delTipo.count { it.esRaiz }} rubros",
                            style = MaterialTheme.typography.bodySmall,
                            color = colores.textoTenue
                        )
                    }
                }

                // Cada raiz seguida de sus hijas, para que se vea el arbol.
                val ordenado = delTipo.filter { it.esRaiz }.flatMap { raiz ->
                    listOf(raiz) + delTipo.filter { it.categoria.padreId == raiz.categoria.id }
                }
                val sueltas = delTipo.filter { !it.esRaiz && ordenado.none { o -> o.categoria.id == it.categoria.id } }

                items(ordenado + sueltas, key = { it.categoria.id }) { renglon ->
                    RenglonCategoriaVista(renglon) { editando = renglon.categoria }
                }
            }

            item {
                Spacer(Modifier.height(24.dp))
                Text(
                    "Un rubro agrupa; sus hijas son las que capturas. La descripcion del " +
                        "movimiento es el detalle, no la categoria: por eso el analisis puede " +
                        "sumar todo un rubro aunque cada gasto se llame distinto.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colores.textoTenue
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Al borrar una categoria con movimientos se archiva en vez de eliminarse: " +
                        "asi el historial no se queda sin clasificar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colores.textoTenue
                )
            }
        }
    }

    editando?.let { categoria ->
        DialogoCategoria(
            categoria = categoria,
            renglones = renglones,
            alGuardar = { vm.guarda(it) { mensaje -> aviso = mensaje }; editando = null },
            alEliminar = {
                renglones.firstOrNull { it.categoria.id == categoria.id }?.let(vm::elimina)
                editando = null
            },
            alCancelar = { editando = null }
        )
    }

    aviso?.let { mensaje ->
        AlertDialog(
            onDismissRequest = { aviso = null },
            title = { Text("No se pudo guardar") },
            text = { Text(mensaje) },
            confirmButton = { TextButton(onClick = { aviso = null }) { Text("Entendido") } }
        )
    }
}

@Composable
private fun RenglonCategoriaVista(renglon: RenglonCategoria, alTocar: () -> Unit) {
    val colores = LocalColoresOllin.current
    val categoria = renglon.categoria

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = alTocar)
            .padding(start = if (renglon.esRaiz) 0.dp else 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                categoria.nombre,
                style = if (renglon.esRaiz) MaterialTheme.typography.titleSmall
                else MaterialTheme.typography.bodyLarge
            )
            Text(
                buildString {
                    if (renglon.esRaiz) append("${renglon.hijas} subcategorias")
                    else append("${renglon.movimientos} movimientos")
                    if (categoria.esencial) append("  ·  esencial")
                    if (categoria.archivada) append("  ·  archivada")
                },
                style = MaterialTheme.typography.bodySmall,
                color = colores.textoTenue
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogoCategoria(
    categoria: Categoria,
    renglones: List<RenglonCategoria>,
    alGuardar: (Categoria) -> Unit,
    alEliminar: () -> Unit,
    alCancelar: () -> Unit
) {
    var nombre by remember(categoria.id) { mutableStateOf(categoria.nombre) }
    var tipo by remember(categoria.id) { mutableStateOf(categoria.tipo) }
    var padreId by remember(categoria.id) { mutableStateOf(categoria.padreId) }
    var esencial by remember(categoria.id) { mutableStateOf(categoria.esencial) }
    var archivada by remember(categoria.id) { mutableStateOf(categoria.archivada) }
    var abreTipo by remember { mutableStateOf(false) }
    var abrePadre by remember { mutableStateOf(false) }

    val esteRenglon = renglones.firstOrNull { it.categoria.id == categoria.id }
    val tieneHijas = (esteRenglon?.hijas ?: 0) > 0

    // Solo raices del mismo tipo pueden ser madre, y nunca ella misma. Un nivel
    // de anidamiento basta para el analisis, asi que una categoria con hijas no
    // puede a su vez colgar de otra.
    val posiblesPadres = renglones
        .filter { it.esRaiz && it.categoria.tipo == tipo && it.categoria.id != categoria.id }
        .map { it.categoria }

    val padre = posiblesPadres.firstOrNull { it.id == padreId }
    val nombrePadre = padre?.nombre ?: "Ninguna (es un rubro)"

    AlertDialog(
        onDismissRequest = alCancelar,
        title = { Text(if (categoria.id == 0L) "Nueva categoria" else "Editar categoria") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    label = { Text("Nombre") },
                    singleLine = true
                )

                ExposedDropdownMenuBox(
                    expanded = abreTipo,
                    onExpandedChange = { if (padre == null) abreTipo = it }
                ) {
                    OutlinedTextField(
                        value = tipo.etiqueta,
                        onValueChange = {},
                        readOnly = true,
                        enabled = padre == null,
                        label = { Text("Naturaleza") },
                        supportingText = if (padre != null) {
                            { Text("La hereda de ${padre.nombre}.") }
                        } else null,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(abreTipo) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(expanded = abreTipo, onDismissRequest = { abreTipo = false }) {
                        TipoCategoria.entries.forEach { opcion ->
                            DropdownMenuItem(
                                text = { Text(opcion.etiqueta) },
                                onClick = { tipo = opcion; padreId = null; abreTipo = false }
                            )
                        }
                    }
                }

                if (!tieneHijas) {
                    ExposedDropdownMenuBox(expanded = abrePadre, onExpandedChange = { abrePadre = it }) {
                        OutlinedTextField(
                            value = nombrePadre,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Cuelga de") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(abrePadre) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(
                            expanded = abrePadre,
                            onDismissRequest = { abrePadre = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Ninguna (es un rubro)") },
                                onClick = { padreId = null; abrePadre = false }
                            )
                            posiblesPadres.forEach { opcion ->
                                DropdownMenuItem(
                                    text = { Text(opcion.nombre) },
                                    onClick = {
                                        padreId = opcion.id
                                        tipo = opcion.tipo
                                        abrePadre = false
                                    }
                                )
                            }
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Esencial", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Lo que no puedes dejar de pagar.",
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalColoresOllin.current.textoTenue
                        )
                    }
                    Switch(checked = esencial, onCheckedChange = { esencial = it })
                }

                if (categoria.id != 0L) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Archivada", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Deja de ofrecerse al capturar, pero el historial la conserva.",
                                style = MaterialTheme.typography.bodySmall,
                                color = LocalColoresOllin.current.textoTenue
                            )
                        }
                        Switch(checked = archivada, onCheckedChange = { archivada = it })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    alGuardar(
                        categoria.copy(
                            nombre = nombre.trim(),
                            padreId = padreId,
                            tipo = tipo,
                            esencial = esencial,
                            archivada = archivada
                        )
                    )
                },
                enabled = nombre.isNotBlank()
            ) { Text("Guardar") }
        },
        dismissButton = {
            Row {
                if (categoria.id != 0L) {
                    TextButton(onClick = alEliminar) {
                        Text(if (esteRenglon?.sePuedeBorrar == true) "Eliminar" else "Archivar")
                    }
                }
                TextButton(onClick = alCancelar) { Text("Cancelar") }
            }
        }
    )
}
