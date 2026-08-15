package mx.ollin.finanzas.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mx.ollin.finanzas.data.db.Categoria
import mx.ollin.finanzas.data.db.Compromiso
import mx.ollin.finanzas.data.db.Cuenta
import mx.ollin.finanzas.data.db.Movimiento
import mx.ollin.finanzas.data.db.SaldoCuenta
import mx.ollin.finanzas.di.Contenedor
import mx.ollin.finanzas.domain.model.Contraparte
import mx.ollin.finanzas.domain.model.Dinero
import mx.ollin.finanzas.domain.model.Medio
import mx.ollin.finanzas.domain.model.TipoCategoria
import mx.ollin.finanzas.domain.model.TipoCuenta
import mx.ollin.finanzas.domain.model.TipoMovimiento
import mx.ollin.finanzas.ui.recuerdaVm
import mx.ollin.finanzas.ui.theme.LocalColoresOllin
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
    private val contenedor: Contenedor,
    private val movimientoId: Long?,
    /** Compromiso que se esta pagando. Precarga la captura y queda ligado al movimiento. */
    private val compromisoId: Long? = null
) : ViewModel() {

    private val repo = contenedor.repositorio

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

    val muestraSaldoInicial: StateFlow<Boolean> = contenedor.ajustes.ajustes
        .map { it.muestraSaldoInicial }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** Lo que la cuenta vale hoy sin contar el ajuste que se esta editando. */
    fun valorBase(): Long =
        (saldos.value.firstOrNull { it.cuentaId == cuentaId }?.saldoCentavos ?: 0L) - importeOriginal

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
                    importeOriginal = if (m.tipo == TipoMovimiento.AJUSTE_VALOR) m.importeCentavos else 0L
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
        fecha = c.fechaPrimerPago.plusMonths(c.pagosRealizados.toLong() * c.periodicidad.meses)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CapturaPantalla(
    contenedor: Contenedor,
    movimientoId: Long?,
    alCerrar: () -> Unit,
    alCambiarATransferencia: () -> Unit,
    alEditarTransferencia: (Long) -> Unit,
    compromisoId: Long? = null
) {
    val vm = recuerdaVm("captura-${movimientoId ?: 0}-${compromisoId ?: 0}") {
        CapturaVm(contenedor, movimientoId, compromisoId)
    }
    val cuentas by vm.cuentas.collectAsStateWithLifecycle()
    val categorias by vm.categorias.collectAsStateWithLifecycle()
    val saldos by vm.saldos.collectAsStateWithLifecycle()
    val muestraSaldoInicial by vm.muestraSaldoInicial.collectAsStateWithLifecycle()
    val cerrar by vm.cerrar.collectAsStateWithLifecycle()
    val colores = LocalColoresOllin.current

    LaunchedEffect(cerrar) { if (cerrar) alCerrar() }

    var muestraCalendario by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    when {
                        vm.compromisoPagado != null -> "Pagar compromiso"
                        movimientoId == null -> "Nuevo movimiento"
                        else -> "Editar movimiento"
                    }
                )
            },
            navigationIcon = {
                IconButton(onClick = alCerrar) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                }
            },
            actions = {
                if (movimientoId != null) {
                    IconButton(onClick = vm::elimina) {
                        Icon(Icons.Filled.Delete, contentDescription = "Eliminar", tint = colores.salida)
                    }
                }
            }
        )

        // Una pata suelta no se edita aqui: guardar solo la mitad descuadraria el
        // patrimonio, asi que la unica salida es abrir la transferencia entera.
        if (vm.cargado && vm.esTraspaso && movimientoId != null) {
            Card(
                Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Esto es media transferencia",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        "Una transferencia son dos renglones que se cancelan entre si, y aqui " +
                            "solo se ve uno. Se edita completa para que el par siga cuadrando.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(
                        onClick = { alEditarTransferencia(movimientoId) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Editar la transferencia completa")
                    }
                }
            }
            return@Column
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            val cuentasActivo = cuentas.filter { it.tipo == TipoCuenta.ACTIVO }

            // Al abrir una compra de patrimonio vieja (una salida suelta venida del
            // Excel) el destino no existe todavia: se sugiere para poder repararla.
            LaunchedEffect(vm.naturaleza, cuentasActivo) {
                if (vm.naturaleza == NaturalezaCaptura.PATRIMONIO && vm.cuentaActivoId == null) {
                    vm.cuentaActivoId = cuentasActivo.firstOrNull()?.id
                }
            }

            // Chips y no segmentos: cuatro etiquetas no caben repartidas a lo ancho
            // de un telefono angosto, y aqui cada una se lee entera aunque haya
            // que deslizar.
            // Se puede esconder el saldo inicial desde Ajustes, pero nunca cuando
            // es lo que se esta editando: dejaria el movimiento sin forma de abrirse.
            val naturalezas = NaturalezaCaptura.entries.filter {
                it != NaturalezaCaptura.SALDO_INICIAL ||
                    muestraSaldoInicial ||
                    vm.naturaleza == NaturalezaCaptura.SALDO_INICIAL
            }

            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                naturalezas.forEach { opcion ->
                    FilterChip(
                        selected = vm.naturaleza == opcion,
                        onClick = { vm.alElegirNaturaleza(opcion, cuentasActivo) },
                        label = { Text(opcion.etiqueta) }
                    )
                }
            }

            if (vm.naturaleza == NaturalezaCaptura.PATRIMONIO) {
                Text(
                    "Comprar un terreno o cripto no es gastar: el dinero sale de una cuenta y " +
                        "entra a otra. Se guarda como transferencia para que tu patrimonio no baje.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colores.textoTenue
                )
            }

            if (vm.naturaleza == NaturalezaCaptura.AJUSTE) {
                Text(
                    "Para revaluar o depreciar algo que ya tienes. No mueve dinero de ninguna " +
                        "cuenta ni cuenta como ingreso: solo cambia lo que vale el bien.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colores.textoTenue
                )
            }

            OutlinedTextField(
                value = vm.importeTexto,
                onValueChange = { vm.importeTexto = it },
                label = {
                    Text(
                        if (vm.naturaleza == NaturalezaCaptura.AJUSTE) "Cuanto vale ahora"
                        else "Importe"
                    )
                },
                prefix = { Text("$") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Se escribe el valor de hoy y la app deduce el movimiento: nadie
            // sabe de memoria cuanto subio su terreno, pero si cuanto vale.
            if (vm.naturaleza == NaturalezaCaptura.AJUSTE && vm.cuentaId != null && saldos.isNotEmpty()) {
                val diferencia = vm.diferenciaDelAjuste()
                Text(
                    buildString {
                        append("Hoy esta en ${Dinero.formatea(vm.valorBase())}.")
                        if (diferencia != null && diferencia != 0L) {
                            append(if (diferencia > 0) "  Sube " else "  Baja ")
                            append(Dinero.formatea(kotlin.math.abs(diferencia)))
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        diferencia == null || diferencia == 0L -> colores.textoTenue
                        diferencia > 0 -> colores.entrada
                        else -> colores.salida
                    }
                )
            }

            // Una tarjeta puede arrancar debiendo: el saldo inicial lleva su propio signo.
            if (vm.naturaleza == NaturalezaCaptura.SALDO_INICIAL) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !vm.saldoEnContra,
                        onClick = { vm.saldoEnContra = false },
                        shape = SegmentedButtonDefaults.itemShape(0, 2)
                    ) { Text("A favor") }
                    SegmentedButton(
                        selected = vm.saldoEnContra,
                        onClick = { vm.saldoEnContra = true },
                        shape = SegmentedButtonDefaults.itemShape(1, 2)
                    ) { Text("En contra") }
                }
            }

            OutlinedTextField(
                value = vm.fecha.toString(),
                onValueChange = {},
                label = { Text("Fecha") },
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    TextButton(onClick = { muestraCalendario = true }) { Text("Cambiar") }
                }
            )

            // Revaluar una cuenta de banco no tiene sentido: si el saldo no cuadra
            // es que falta un movimiento, no que el dinero cambio de valor.
            val revaluables = cuentas.filter {
                it.tipo == TipoCuenta.ACTIVO || it.tipo == TipoCuenta.INVERSION
            }
            val elegibles = if (vm.naturaleza == NaturalezaCaptura.AJUSTE) revaluables else cuentas

            SelectorDesplegable(
                etiqueta = when (vm.naturaleza) {
                    NaturalezaCaptura.PATRIMONIO -> "Sale de"
                    NaturalezaCaptura.AJUSTE -> "Que bien"
                    else -> "Cuenta"
                },
                valor = elegibles.firstOrNull { it.id == vm.cuentaId }?.nombre.orEmpty(),
                opciones = elegibles.map { it.id to it.nombre },
                alElegir = { id -> elegibles.firstOrNull { it.id == id }?.let(vm::alElegirCuenta) }
            )

            if (vm.naturaleza == NaturalezaCaptura.AJUSTE && revaluables.isEmpty()) {
                Text(
                    "No tienes cuentas de tipo Activo ni Inversion. Crea una en Ajustes > " +
                        "Cuentas para poder registrar lo que vale.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colores.alerta
                )
            }

            if (vm.naturaleza == NaturalezaCaptura.PATRIMONIO) {
                if (cuentasActivo.isEmpty()) {
                    Text(
                        "No tienes ninguna cuenta de tipo Activo. Crea una en Ajustes > Cuentas " +
                            "(por ejemplo \"Patrimonio\") para poder registrar la compra.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colores.alerta
                    )
                } else {
                    SelectorDesplegable(
                        etiqueta = "Entra a",
                        valor = cuentasActivo.firstOrNull { it.id == vm.cuentaActivoId }?.nombre.orEmpty(),
                        opciones = cuentasActivo.map { it.id to it.nombre },
                        alElegir = { vm.cuentaActivoId = it }
                    )
                }
            }

            if (vm.naturaleza != NaturalezaCaptura.SALDO_INICIAL &&
                vm.naturaleza != NaturalezaCaptura.AJUSTE
            ) {
                val aplicables = vm.categoriasAplicables(categorias)
                SelectorDesplegable(
                    etiqueta = "Categoria",
                    valor = aplicables.firstOrNull { it.id == vm.categoriaId }?.nombre.orEmpty(),
                    opciones = aplicables.map { it.id to it.nombre },
                    alElegir = { vm.categoriaId = it }
                )
            }

            OutlinedTextField(
                value = vm.descripcion,
                onValueChange = { vm.descripcion = it },
                label = { Text("Descripcion") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // En patrimonio y saldo inicial el medio lo dicta la cuenta, no el usuario.
            if (vm.naturaleza == NaturalezaCaptura.GASTO || vm.naturaleza == NaturalezaCaptura.INGRESO) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Medio.entries.forEachIndexed { i, m ->
                        SegmentedButton(
                            selected = vm.medio == m,
                            onClick = { vm.medio = m },
                            shape = SegmentedButtonDefaults.itemShape(i, Medio.entries.size)
                        ) { Text(m.etiqueta) }
                    }
                }
            }

            OutlinedTextField(
                value = vm.nota,
                onValueChange = { vm.nota = it },
                label = { Text("Nota (opcional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            // Dos saldos iniciales en la misma cuenta se suman en silencio y el
            // saldo queda al doble sin que nada lo delate.
            vm.saldoInicialPrevio
                ?.takeIf { vm.naturaleza == NaturalezaCaptura.SALDO_INICIAL }
                ?.let { previo ->
                    Text(
                        "Esta cuenta ya tiene un saldo inicial de ${Dinero.formatea(previo.importeCentavos)} " +
                            "con fecha ${previo.fecha}. Si guardas otro, los dos se suman: " +
                            "conviene editar el que ya existe.",
                        color = colores.alerta,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

            vm.error?.let {
                Text(it, color = colores.salida, style = MaterialTheme.typography.bodyMedium)
            }

            Button(onClick = vm::guarda, modifier = Modifier.fillMaxWidth()) {
                Text("Guardar")
            }

            if (movimientoId == null) {
                TextButton(onClick = alCambiarATransferencia, modifier = Modifier.fillMaxWidth()) {
                    Text("Es un traspaso entre mis cuentas")
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }

    if (muestraCalendario) {
        val estado = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = vm.fecha.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { muestraCalendario = false },
            confirmButton = {
                TextButton(onClick = {
                    estado.selectedDateMillis?.let {
                        vm.fecha = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    muestraCalendario = false
                }) { Text("Listo") }
            },
            dismissButton = {
                TextButton(onClick = { muestraCalendario = false }) { Text("Cancelar") }
            }
        ) { DatePicker(state = estado) }
    }
}

/** Desplegable simple sobre una lista de pares id/etiqueta. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectorDesplegable(
    etiqueta: String,
    valor: String,
    opciones: List<Pair<Long, String>>,
    alElegir: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var abierto by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = abierto,
        onExpandedChange = { abierto = it },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = valor,
            onValueChange = {},
            readOnly = true,
            label = { Text(etiqueta) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = abierto) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            opciones.forEach { (id, texto) ->
                DropdownMenuItem(
                    text = { Text(texto) },
                    onClick = { alElegir(id); abierto = false }
                )
            }
        }
    }
}
