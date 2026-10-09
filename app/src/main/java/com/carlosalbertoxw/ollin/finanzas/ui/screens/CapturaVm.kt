package com.carlosalbertoxw.ollin.finanzas.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.carlosalbertoxw.ollin.finanzas.data.db.Categoria
import com.carlosalbertoxw.ollin.finanzas.data.db.Compromiso
import com.carlosalbertoxw.ollin.finanzas.data.db.Cuenta
import com.carlosalbertoxw.ollin.finanzas.data.db.Movimiento
import com.carlosalbertoxw.ollin.finanzas.data.db.SaldoCuenta
import com.carlosalbertoxw.ollin.finanzas.data.prefs.AjustesRepositorio
import com.carlosalbertoxw.ollin.finanzas.data.repo.FinanzasRepositorio
import com.carlosalbertoxw.ollin.finanzas.domain.model.Contraparte
import com.carlosalbertoxw.ollin.finanzas.domain.model.Dinero
import com.carlosalbertoxw.ollin.finanzas.domain.model.Medio
import com.carlosalbertoxw.ollin.finanzas.domain.model.TipoCategoria
import com.carlosalbertoxw.ollin.finanzas.domain.model.TipoCuenta
import com.carlosalbertoxw.ollin.finanzas.domain.model.TipoMovimiento
import com.carlosalbertoxw.ollin.finanzas.ui.recuerdaVm
import com.carlosalbertoxw.ollin.finanzas.ui.theme.LocalColoresOllin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Que se esta capturando. No es lo mismo que [TipoMovimiento]: separa el gasto
 * de la compra de patrimonio, que en el modelo terminan siendo cosas distintas
 * (una salida contra una transferencia) aunque las dos saquen dinero.
 */
enum class NaturalezaCaptura(val etiqueta: String) {
    GASTO("Gasto"),
    INGRESO("Ingreso"),
    PATRIMONIO("Patrimonio"),
    SALDO_INICIAL("Saldo inicial"),

    /** Revaluar o depreciar algo que ya tienes, sin que se mueva un peso. */
    AJUSTE("Ajuste de valor");

    val esSalidaDeDinero: Boolean get() = this == GASTO || this == PATRIMONIO
}
class CapturaVm(
    private val repo: FinanzasRepositorio,
    private val ajustes: AjustesRepositorio,
    private val movimientoId: Long?,
    /** Compromiso que se esta pagando. Precarga la captura y queda ligado al movimiento. */
    private val compromisoId: Long? = null
) : ViewModel() {

    var fecha by mutableStateOf(LocalDate.now())
    var importeTexto by mutableStateOf("")
    var naturaleza by mutableStateOf(NaturalezaCaptura.GASTO)
        private set
    var cuentaId by mutableStateOf<Long?>(null)

    /** Cuenta de Activo a la que entra una compra de patrimonio. */
    var cuentaActivoId by mutableStateOf<Long?>(null)
    var categoriaId by mutableStateOf<Long?>(null)
    var descripcion by mutableStateOf("")
    var medio by mutableStateOf(Medio.ELECTRONICO)
    var nota by mutableStateOf("")

    /** Un saldo inicial puede nacer en contra: una tarjeta que ya debia dinero. */
    var saldoEnContra by mutableStateOf(false)
    var cargado by mutableStateOf(movimientoId == null && compromisoId == null)
        private set

    /** Datos del compromiso que se paga, para el encabezado de la pantalla. */
    var compromisoPagado by mutableStateOf<Compromiso?>(null)
        private set
    var esTraspaso by mutableStateOf(false)
        private set

    /** Saldo inicial que ya tenia la cuenta elegida, para no duplicarlo sin querer. */
    var saldoInicialPrevio by mutableStateOf<Movimiento?>(null)
        private set

    /** Importe original cuando se edita un ajuste, para recalcular su diferencia. */
    private var importeOriginal = 0L

    /**
     * Compromiso al que ya estaba ligado el movimiento que se edita. Se conserva
     * aparte de [compromisoId] para no perder el vinculo al reescribir el renglon.
     */
    private var compromisoOriginal: Long? = null

    val cuentas: StateFlow<List<Cuenta>> = repo.observaCuentas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categorias: StateFlow<List<Categoria>> = repo.observaCategorias()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val saldos: StateFlow<List<SaldoCuenta>> = repo.observaSaldos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val muestraSaldoInicial: StateFlow<Boolean> = ajustes.ajustes
        .map { it.muestraSaldoInicial }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** Lo que la cuenta vale hoy sin contar el ajuste que se esta editando. */
    fun valorBase(): Long =
        (saldos.value.firstOrNull { it.cuentaId == cuentaId }?.saldoCentavos ?: 0L) -
            importeOriginal

    /** Cuanto subiria o bajaria el saldo con el valor que llevas escrito. */
    fun diferenciaDelAjuste(): Long? {
        val destino = Dinero.parsea(importeTexto)?.let { kotlin.math.abs(it) } ?: return null
        return destino - valorBase()
    }

    private val _cerrar = MutableStateFlow(false)
    val cerrar: StateFlow<Boolean> = _cerrar

    var error by mutableStateOf<String?>(null)
        private set

    init {
        if (movimientoId != null) {
            viewModelScope.launch {
                repo.movimiento(movimientoId)?.let { m ->
                    fecha = m.fecha
                    importeOriginal =
                        if (m.tipo == TipoMovimiento.AJUSTE_VALOR) m.importeCentavos else 0L
                    importeTexto = Dinero.aTextoHoja(kotlin.math.abs(m.importeCentavos))
                    cuentaId = m.cuentaId
                    categoriaId = m.categoriaId
                    descripcion = m.descripcion
                    medio = m.medio
                    nota = m.nota.orEmpty()
                    esTraspaso = m.tipo.esTransferencia
                    saldoEnContra = m.importeCentavos < 0
                    compromisoOriginal = m.compromisoId
                    naturaleza = naturalezaDe(m)
                }
                // Un ajuste no se edita por su diferencia sino por el valor que
                // dejo puesto, que es lo unico que el dueno del bien tiene en la
                // cabeza. Hay que esperar a que lleguen los saldos para saberlo.
                if (naturaleza == NaturalezaCaptura.AJUSTE) {
                    val saldo = saldos.first { lista -> lista.any { it.cuentaId == cuentaId } }
                        .first { it.cuentaId == cuentaId }
                    importeTexto = Dinero.aTextoHoja(kotlin.math.abs(saldo.saldoCentavos))
                }
                cargado = true
            }
        } else if (compromisoId != null) {
            viewModelScope.launch {
                repo.compromiso(compromisoId)?.let { precarga(it) }
                cargado = true
            }
        }
    }

    /**
     * Deja la captura lista con lo que el compromiso ya sabe. Se precarga en vez
     * de escribir directo porque el cargo real no siempre es el planeado: sube
     * la suscripcion, cambia el seguro. Aqui se puede corregir antes de guardar.
     */
    private suspend fun precarga(c: Compromiso) {
        compromisoPagado = c
        fecha = c.proximoPago
        importeTexto = Dinero.aTextoHoja(c.montoCentavos)
        descripcion = c.nombre
        nota = c.notas.orEmpty()
        categoriaId = c.categoriaId
        // La categoria dice si esto es gasto, ingreso o compra de patrimonio.
        naturaleza = when (c.categoriaId?.let { repo.categoria(it) }?.tipo) {
            TipoCategoria.INGRESO -> NaturalezaCaptura.INGRESO
            TipoCategoria.PATRIMONIO -> NaturalezaCaptura.PATRIMONIO
            else -> NaturalezaCaptura.GASTO
        }
        // El medio sigue a la cuenta, igual que en una captura a mano.
        c.cuentaId?.let { id ->
            cuentaId = id
            repo.cuenta(id)?.let { medio = it.medioPorDefecto }
        }
    }

    /**
     * Una salida cuya categoria es de patrimonio se reconoce como compra de
     * patrimonio: es lo que permite convertir en transferencia las que entraron
     * por Excel como simple salida.
     */
    private suspend fun naturalezaDe(m: Movimiento): NaturalezaCaptura = when {
        m.tipo == TipoMovimiento.BALANCE_INICIAL -> NaturalezaCaptura.SALDO_INICIAL
        m.tipo == TipoMovimiento.AJUSTE_VALOR -> NaturalezaCaptura.AJUSTE
        m.tipo == TipoMovimiento.ENTRADA -> NaturalezaCaptura.INGRESO
        m.categoriaId?.let { repo.categoria(it) }?.tipo == TipoCategoria.PATRIMONIO ->
            NaturalezaCaptura.PATRIMONIO
        else -> NaturalezaCaptura.GASTO
    }

    fun alElegirNaturaleza(nueva: NaturalezaCaptura, cuentasActivo: List<Cuenta>) {
        if (nueva == naturaleza) return
        naturaleza = nueva
        // Las categorias no se cruzan entre naturalezas: la elegida deja de aplicar.
        categoriaId = null
        when (nueva) {
            NaturalezaCaptura.SALDO_INICIAL -> {
                if (descripcion.isBlank()) descripcion = "Balance Inicial"
                revisaSaldoInicialPrevio()
            }
            NaturalezaCaptura.AJUSTE -> {
                if (descripcion.isBlank()) descripcion = "Ajuste de valor"
                // Se empieza desde lo que vale hoy: casi siempre solo cambia un poco.
                importeTexto = Dinero.aTextoHoja(kotlin.math.abs(valorBase()))
            }
            NaturalezaCaptura.PATRIMONIO ->
                if (cuentaActivoId == null) cuentaActivoId = cuentasActivo.firstOrNull()?.id
            else -> Unit
        }
    }

    /** Solo se ofrecen las categorias que corresponden a lo que se esta capturando. */
    fun categoriasAplicables(todas: List<Categoria>): List<Categoria> {
        val tipo = when (naturaleza) {
            NaturalezaCaptura.GASTO -> TipoCategoria.GASTO
            NaturalezaCaptura.INGRESO -> TipoCategoria.INGRESO
            NaturalezaCaptura.PATRIMONIO -> TipoCategoria.PATRIMONIO
            NaturalezaCaptura.SALDO_INICIAL, NaturalezaCaptura.AJUSTE -> return emptyList()
        }
        return todas.filter { it.padreId != null && it.tipo == tipo }
    }

    fun alElegirCuenta(cuenta: Cuenta) {
        cuentaId = cuenta.id
        // El medio sigue a la cuenta: es lo que evita marcar la cartera como electronica.
        medio = cuenta.medioPorDefecto
        when (naturaleza) {
            NaturalezaCaptura.SALDO_INICIAL -> {
                saldoEnContra = cuenta.tipo.esDeuda
                revisaSaldoInicialPrevio()
            }
            // Al cambiar de bien, el valor de partida es el de ese bien.
            NaturalezaCaptura.AJUSTE ->
                importeTexto = Dinero.aTextoHoja(kotlin.math.abs(valorBase()))
            else -> Unit
        }
    }

    private fun revisaSaldoInicialPrevio() {
        val id = cuentaId
        if (id == null) { saldoInicialPrevio = null; return }
        viewModelScope.launch {
            saldoInicialPrevio = repo.balanceInicialDe(id)?.takeIf { it.id != movimientoId }
        }
    }

    fun guarda() {
        val centavos = Dinero.parsea(importeTexto)?.let { kotlin.math.abs(it) }
        val cuenta = cuentaId
        val activo = cuentaActivoId
        when {
            centavos == null || centavos == 0L -> { error = "Escribe un importe valido"; return }
            cuenta == null -> { error = "Elige la cuenta"; return }
            descripcion.isBlank() -> { error = "Ponle una descripcion"; return }
            naturaleza == NaturalezaCaptura.PATRIMONIO && activo == null -> {
                error = "Elige la cuenta de Activo a la que entra"; return
            }
            naturaleza == NaturalezaCaptura.PATRIMONIO && activo == cuenta -> {
                error = "El dinero tiene que entrar a una cuenta distinta de la que sale"; return
            }
            naturaleza == NaturalezaCaptura.AJUSTE && diferenciaDelAjuste() == 0L -> {
                error = "Ya vale eso: no hay nada que ajustar"; return
            }
        }
        error = null

        viewModelScope.launch {
            if (naturaleza == NaturalezaCaptura.AJUSTE) {
                // Se guarda la diferencia, no el valor: el saldo sigue siendo la
                // suma de los movimientos, sin excepciones que mantener aparte.
                repo.guardaMovimiento(
                    Movimiento(
                        id = movimientoId ?: 0L,
                        fecha = fecha,
                        importeCentavos = diferenciaDelAjuste() ?: 0L,
                        cuentaId = cuenta!!,
                        categoriaId = null,
                        descripcion = descripcion.trim(),
                        medio = medio,
                        tipo = TipoMovimiento.AJUSTE_VALOR,
                        contraparte = Contraparte.PROPIA,
                        nota = nota.ifBlank { null }
                    )
                )
                _cerrar.value = true
                return@launch
            }

            if (naturaleza == NaturalezaCaptura.PATRIMONIO) {
                // Comprar patrimonio no es gastar: el dinero cambia de cuenta.
                // Se escribe como par ligado para que el patrimonio neto no baje.
                repo.guardaTransferencia(
                    fecha = fecha,
                    importeCentavos = centavos!!,
                    cuentaOrigenId = cuenta!!,
                    cuentaDestinoId = activo!!,
                    descripcion = descripcion.trim(),
                    nota = nota.ifBlank { null },
                    idsAReemplazar = listOfNotNull(movimientoId),
                    categoriaId = categoriaId
                )
            } else {
                repo.guardaMovimiento(
                    Movimiento(
                        id = movimientoId ?: 0L,
                        fecha = fecha,
                        importeCentavos = if (saleDinero()) -centavos!! else centavos!!,
                        cuentaId = cuenta!!,
                        categoriaId = categoriaId,
                        descripcion = descripcion.trim(),
                        medio = medio,
                        tipo = when (naturaleza) {
                            NaturalezaCaptura.INGRESO -> TipoMovimiento.ENTRADA
                            NaturalezaCaptura.SALDO_INICIAL -> TipoMovimiento.BALANCE_INICIAL
                            else -> TipoMovimiento.SALIDA
                        },
                        contraparte = Contraparte.TERCERO,
                        compromisoId = compromisoId ?: compromisoOriginal,
                        nota = nota.ifBlank { null }
                    )
                )
            }
            // El movimiento queda ligado al compromiso, pero el plan no avanza
            // solo: dar por cumplido el pago es un gesto manual en la lista de
            // compromisos, porque el cargo puede llegar por fuera de la app.
            _cerrar.value = true
        }
    }

    private fun saleDinero(): Boolean =
        if (naturaleza == NaturalezaCaptura.SALDO_INICIAL) saldoEnContra
        else naturaleza.esSalidaDeDinero

    fun elimina() {
        val id = movimientoId ?: return
        viewModelScope.launch {
            repo.movimiento(id)?.let { repo.eliminaMovimiento(it) }
            _cerrar.value = true
        }
    }
}
