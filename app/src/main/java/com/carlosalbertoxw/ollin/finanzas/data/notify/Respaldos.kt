package com.carlosalbertoxw.ollin.finanzas.data.notify

import kotlin.math.abs

/**
 * Cuando toca recordar que hay que respaldar.
 *
 * El respaldo de Ollin Finanzas no lo hace nadie mas: la base va cifrada con
 * una llave del Keystore que no se puede restaurar ni pasar a otro telefono, y
 * por eso el respaldo automatico de Android esta desactivado para ella a
 * proposito. Lo unico que sobrevive a perder el telefono es el `.xlsx` que
 * alguien decidio exportar. Un libro de finanzas de dos años que nadie exporto
 * nunca se pierde entero, y se descubre el peor dia.
 *
 * Todo lo que decide algo vive aqui, en funciones puras sobre instantes: el
 * receptor de la alarma solo pregunta.
 */
object Respaldos {

    /** Una semana. Suficiente para no volverse ruido y para no perder un mes. */
    const val CADA_MS = 7L * 24 * 60 * 60 * 1000

    /**
     * Desde cuando se cuenta: el ultimo respaldo, o el ancla si no hubo
     * ninguno.
     *
     * El ancla se pone en el primer arranque que ve esta funcion y no en la
     * instalacion de la app, para que quien ya la tenia no reciba un aviso el
     * mismo dia que actualiza por no haber exportado nunca.
     */
    fun cuentaDesde(ultimoRespaldo: Long, ancla: Long): Long = maxOf(ultimoRespaldo, ancla)

    /**
     * Si toca avisar.
     *
     * Sin punto de partida no se avisa: es el arranque el que lo pone, y hasta
     * entonces no hay semana que contar.
     *
     * El valor absoluto cubre el reloj movido hacia atras, igual que en la
     * comprobacion de versiones: lo peor que consigue quien lo adelante es un
     * recordatorio de mas, y eso no le hace dano a nadie. Congelar el aviso
     * hasta que el reloj vuelva a alcanzar una marca del futuro, si.
     */
    fun toca(ultimoRespaldo: Long, ancla: Long, ahora: Long): Boolean {
        val desde = cuentaDesde(ultimoRespaldo, ancla)
        if (desde <= 0L) return false
        return abs(ahora - desde) >= CADA_MS
    }

    /** Dias enteros transcurridos, para poder decirlo en el aviso. */
    fun diasDesde(instante: Long, ahora: Long): Int =
        (abs(ahora - instante) / (24L * 60 * 60 * 1000)).toInt()

    /**
     * Que dice el aviso semanal.
     *
     * Se nombra el tiempo que lleva sin respaldo en vez de repetir siempre la
     * misma frase: "hace 21 dias" mueve a alguien a exportar y "acuerdate de
     * respaldar" ya no lo lee nadie a la tercera semana.
     */
    fun textoDelAviso(ultimoRespaldo: Long, ancla: Long, ahora: Long): String {
        if (ultimoRespaldo <= 0L) {
            return "Todavia no has exportado tu libro. Si pierdes el telefono, no hay de donde recuperarlo."
        }
        val dias = diasDesde(ultimoRespaldo, ahora)
        return "Tu ultimo respaldo es de hace $dias dias. Exportalo a Excel y guardalo donde tu decidas."
    }
}
