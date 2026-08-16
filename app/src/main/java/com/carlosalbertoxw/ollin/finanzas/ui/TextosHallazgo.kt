package com.carlosalbertoxw.ollin.finanzas.ui

import com.carlosalbertoxw.ollin.finanzas.domain.model.Dinero
import com.carlosalbertoxw.ollin.finanzas.domain.usecase.ClaveHallazgo
import com.carlosalbertoxw.ollin.finanzas.domain.usecase.Hallazgo

/**
 * Redaccion de los hallazgos de Salud de los datos.
 *
 * Vive en la interfaz y no en el caso de uso a proposito: el dominio detecta y
 * mide, pero la frase que lee el usuario es material de producto. Aqui se puede
 * reescribir un texto sin tocar [com.carlosalbertoxw.ollin.finanzas.domain.usecase.RevisaCalidad]
 * ni las pruebas que lo cubren.
 */

fun Hallazgo.titulo(): String = when (clave) {
    ClaveHallazgo.TIPO_VS_SIGNO -> "Tipo y signo se contradicen"
    ClaveHallazgo.TRANSFERENCIA_HUERFANA -> "Transferencias sin su contraparte"
    ClaveHallazgo.SALDO_INICIAL_DUPLICADO -> "Cuentas con mas de un saldo inicial"
    ClaveHallazgo.SIN_CATEGORIA -> "Movimientos sin categoria"
    ClaveHallazgo.MEDIO_INCOHERENTE -> "Medio que no cuadra con la cuenta"
    ClaveHallazgo.PATRIMONIO_SIN_ESPEJO -> "Compras de patrimonio contadas como gasto"
    ClaveHallazgo.DESCRIPCIONES_PARECIDAS -> "Descripciones casi identicas"
    ClaveHallazgo.MESES_VACIOS -> "Meses sin ningun movimiento"
    ClaveHallazgo.SALDO_NEGATIVO -> "Cuentas que no son de credito con saldo negativo"
    else -> "Algo que revisar"
}

fun Hallazgo.detalle(): String = when (clave) {
    ClaveHallazgo.TIPO_VS_SIGNO ->
        "Hay movimientos marcados como entrada con importe negativo, o al reves. " +
            "Los saldos salen bien pero cualquier reporte por tipo queda mal."

    ClaveHallazgo.TRANSFERENCIA_HUERFANA ->
        "Una transferencia debe mover dinero de una cuenta a otra, con dos renglones " +
            "que se cancelan. Estas quedaron a medias, asi que el patrimonio total esta desviado. " +
            "Abre cada una y completa la cuenta que falta."

    ClaveHallazgo.SALDO_INICIAL_DUPLICADO ->
        "Una cuenta arranca una sola vez, pero estas tienen varios y todos se " +
            "suman: ${datos.cuentas.joinToString()}. Los renglones de mas valen " +
            "${Dinero.formatea(datos.montoCentavos ?: 0L)}, y ese desvio se " +
            "arrastra a tu patrimonio y a los meses de colchon. Abrelos y borra el que sobre."

    ClaveHallazgo.SIN_CATEGORIA ->
        "Suman ${Dinero.formatea(datos.montoCentavos ?: 0L)} que no aparecen " +
            "en ningun analisis por categoria. Corregir automaticamente clasifica los que " +
            "repiten una descripcion que ya usaste antes; el resto se revisa uno por uno."

    ClaveHallazgo.MEDIO_INCOHERENTE ->
        "Una salida de la cartera no puede ser electronica, ni una cuenta marcada " +
            "como solo electronica puede recibir efectivo."

    ClaveHallazgo.PATRIMONIO_SIN_ESPEJO ->
        "${Dinero.formatea(datos.montoCentavos ?: 0L)} en terreno, cripto o " +
            "bienes duraderos salieron de tus cuentas pero no entraron a ninguna cuenta de activo" +
            if (datos.hayCuentaDeActivo) ". Registralas como transferencia hacia la cuenta de activo."
            else ". Crea una cuenta de tipo Activo para reflejarlas."

    ClaveHallazgo.DESCRIPCIONES_PARECIDAS ->
        "Probablemente sean la misma cosa escrita de dos formas, y el gasto se te " +
            "esta partiendo en dos renglones: " +
            datos.ejemplos.joinToString("; ") { "\"${it.first}\" / \"${it.second}\"" }

    ClaveHallazgo.MESES_VACIOS ->
        "No hay registros en ${datos.periodos.joinToString(", ")}. O no capturaste, " +
            "o falta importar un periodo."

    ClaveHallazgo.SALDO_NEGATIVO ->
        "Estas cuentas quedaron en rojo: ${datos.cuentas.joinToString()}. " +
            "Suele faltar el saldo inicial o sobrar una salida."

    else -> ""
}
