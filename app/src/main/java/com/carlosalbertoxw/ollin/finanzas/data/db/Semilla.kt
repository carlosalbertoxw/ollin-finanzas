package com.carlosalbertoxw.ollin.finanzas.data.db

import com.carlosalbertoxw.ollin.finanzas.domain.model.Medio
import com.carlosalbertoxw.ollin.finanzas.domain.model.TipoCategoria
import com.carlosalbertoxw.ollin.finanzas.domain.model.TipoCuenta
import com.carlosalbertoxw.ollin.finanzas.domain.model.TipoMovimiento
import com.carlosalbertoxw.ollin.finanzas.domain.model.normalizaClave

/**
 * Catalogo generico con el que arranca la app.
 *
 * Son nombres deliberadamente neutros: describen la naturaleza de la cuenta o
 * del rubro, no un banco ni una persona concretos. La idea es que sirvan de
 * punto de partida y que cada quien los renombre a lo suyo, no que adivinen la
 * vida de nadie.
 */
object Semilla {

    // ---------------------------------------------------------------- cuentas

    data class PlantillaCuenta(
        val nombre: String,
        val tipo: TipoCuenta,
        val orden: Int,
        val colorHex: String
    )

    /** Una cuenta por naturaleza, para que el balance sepa clasificarlas desde el dia uno. */
    val CUENTAS = listOf(
        PlantillaCuenta("Efectivo", TipoCuenta.EFECTIVO, 0, "#E9A13B"),
        PlantillaCuenta("Cuenta de banco", TipoCuenta.DEBITO, 1, "#3D6DB5"),
        PlantillaCuenta("Inversion", TipoCuenta.INVERSION, 2, "#2F9E6E"),
        PlantillaCuenta("Creditos - prestamos", TipoCuenta.CREDITO, 3, "#C4453F"),
        PlantillaCuenta("Creditos - MSI", TipoCuenta.CREDITO_MSI, 4, "#7A4FA3"),
        // Cuenta de patrimonio: existe para que comprar un inmueble o un bien
        // duradero deje de contarse como gasto y pase a ser traslado de patrimonio.
        PlantillaCuenta("Patrimonio", TipoCuenta.ACTIVO, 5, "#8A6B3F")
    )

    // ------------------------------------------------------------ categorias

    data class PlantillaCategoria(
        val padre: String,
        val tipo: TipoCategoria,
        val esencial: Boolean,
        val hijas: List<String>
    )

    val CATEGORIAS = listOf(
        PlantillaCategoria(
            "Alimentos", TipoCategoria.GASTO, esencial = true,
            hijas = listOf("Despensa")
        ),
        PlantillaCategoria(
            "Vivienda", TipoCategoria.GASTO, esencial = true,
            hijas = listOf("Mantenimiento hogar")
        ),
        PlantillaCategoria(
            "Transporte", TipoCategoria.GASTO, esencial = true,
            hijas = listOf(
                "Gasolina", "Transporte publico", "Transporte privado", "Mantenimiento vehiculo",
                "Estacionamiento", "Seguro de auto"
            )
        ),
        PlantillaCategoria(
            "Salud", TipoCategoria.GASTO, esencial = true,
            hijas = listOf("Consulta medica", "Medicamentos", "Estudios laboratorio")
        ),
        PlantillaCategoria(
            "Comunicaciones", TipoCategoria.GASTO, esencial = true,
            hijas = listOf("Telefono", "Internet")
        ),
        PlantillaCategoria(
            "Impuestos y tramites", TipoCategoria.GASTO, esencial = true,
            hijas = listOf("Impuestos", "Tramites", "Comisiones bancarias")
        ),
        PlantillaCategoria(
            "Compras", TipoCategoria.GASTO, esencial = false,
            hijas = listOf("Ropa", "Articulos para el hogar", "Articulos personales")
        ),
        PlantillaCategoria(
            "Suscripciones", TipoCategoria.GASTO, esencial = false,
            hijas = listOf("Streaming", "Software", "Membresias")
        ),
        PlantillaCategoria(
            "Entretenimiento", TipoCategoria.GASTO, esencial = false,
            hijas = listOf("Salidas", "Eventos")
        ),
        PlantillaCategoria(
            "Viajes", TipoCategoria.GASTO, esencial = false,
            hijas = listOf("Hospedaje", "Vuelos")
        ),
        PlantillaCategoria(
            "Cuidado personal", TipoCategoria.GASTO, esencial = false,
            hijas = listOf("Peluqueria")
        ),
        PlantillaCategoria(
            "Educacion", TipoCategoria.GASTO, esencial = false,
            hijas = listOf("Cursos")
        ),
        PlantillaCategoria(
            "Mascotas", TipoCategoria.GASTO, esencial = false,
            hijas = listOf("Alimento para mascota", "Veterinario")
        ),
        PlantillaCategoria(
            "Otros gastos", TipoCategoria.GASTO, esencial = false,
            hijas = listOf("Regalos", "Donativos", "Gastos varios")
        ),

        PlantillaCategoria(
            "Sueldo", TipoCategoria.INGRESO, esencial = false,
            hijas = listOf("Salario", "Aguinaldo", "Bonos")
        ),
        PlantillaCategoria(
            "Negocio", TipoCategoria.INGRESO, esencial = false,
            hijas = listOf("Ventas", "Honorarios")
        ),
        PlantillaCategoria(
            "Rendimientos", TipoCategoria.INGRESO, esencial = false,
            hijas = listOf("Intereses", "Dividendos", "Cashback")
        ),
        PlantillaCategoria(
            "Otros ingresos", TipoCategoria.INGRESO, esencial = false,
            hijas = listOf("Reembolsos", "Apoyo recibido")
        ),

        PlantillaCategoria(
            "Inmuebles", TipoCategoria.PATRIMONIO, esencial = false,
            hijas = listOf("Construccion")
        ),
        PlantillaCategoria(
            "Inversion variable", TipoCategoria.PATRIMONIO, esencial = false,
            hijas = listOf("Criptomonedas", "Metales")
        ),
        PlantillaCategoria(
            "Bienes duraderos", TipoCategoria.PATRIMONIO, esencial = false,
            hijas = listOf("Vehiculo", "Equipo de computo")
        ),

        PlantillaCategoria(
            "Movimientos internos", TipoCategoria.TRASPASO, esencial = false,
            hijas = listOf("Transferencia entre cuentas", "Pago de tarjeta", "Retiro de efectivo")
        )
    )

    // -------------------------------------------------------------- mapeo

    /**
     * Descripciones que no coinciden por nombre con su categoria destino. Se
     * resuelven antes que la coincidencia directa, asi que sirven para las
     * formas de escribir lo mismo que no valen una categoria aparte.
     *
     * La clave puede llevar sufijo "|TIPO" cuando la misma descripcion significa
     * cosas distintas segun la direccion: los intereses son ingreso cuando
     * entran y comision del banco cuando salen.
     */
    val ALIAS_DESCRIPCION: Map<String, String> = buildMap {
        put("supermercado", "Despensa")
        put("super", "Despensa")
        put("mandado", "Despensa")
        put("farmacia", "Medicamentos")
        put("doctor", "Consulta medica")
        put("taxi", "Transporte publico")
        put("recarga telefonica", "Telefono")
        put("pago tarjeta credito", "Pago de tarjeta")
        put("pago de tarjeta de credito", "Pago de tarjeta")
        put("transferencia", "Transferencia entre cuentas")
        put("traspaso", "Transferencia entre cuentas")
        put("retiro", "Retiro de efectivo")
        put("retiro de cajero", "Retiro de efectivo")
        // Ambiguas por direccion
        put("intereses|${TipoMovimiento.ENTRADA.name}", "Intereses")
        put("intereses|${TipoMovimiento.SALIDA.name}", "Comisiones bancarias")
    }

    /** Descripciones que no deben generar categoria (son metadatos, no gasto). */
    val DESCRIPCIONES_SIN_CATEGORIA: Set<String> = setOf("balance inicial")

    fun medioSugerido(tipo: TipoCuenta): Medio =
        if (tipo == TipoCuenta.EFECTIVO) Medio.EFECTIVO else Medio.ELECTRONICO

    fun claveAlias(descripcion: String, tipo: TipoMovimiento): String =
        "${descripcion.normalizaClave()}|${tipo.name}"
}
