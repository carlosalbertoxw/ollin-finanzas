package com.carlosalbertoxw.ollin.finanzas.data.actualizacion

import com.carlosalbertoxw.ollin.finanzas.data.prefs.Ajustes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** Como termino una busqueda, para poder decirlo en pantalla. */
enum class ResultadoBusqueda {
    /** No tocaba todavia, o esta apagada en Ajustes. */
    OMITIDA,
    AL_DIA,
    HAY_NOVEDAD,
    /** Sin red, el sitio caido, un json ilegible. Da igual cual: se reintenta mañana. */
    SIN_RESPUESTA
}

/**
 * Decide cuando preguntar por una version nueva y guarda lo que encuentre.
 *
 * Recibe el flujo de preferencias, la funcion que guarda, la consulta y el
 * reloj, en vez del repositorio entero: asi sus reglas —cada cuando toca, que
 * cuenta como novedad, que se escribe y que no— se prueban sin red, sin
 * DataStore y sin esperar un dia. Es el mismo trato que tiene
 * [com.carlosalbertoxw.ollin.finanzas.data.seguridad.ControlBloqueo].
 */
class BuscadorDeActualizaciones(
    private val ajustes: Flow<Ajustes>,
    private val guardaVersion: suspend (codigo: Int, nombre: String, url: String, momento: Long) -> Unit,
    private val versionInstalada: Int,
    private val consulta: suspend () -> String? = { Actualizaciones.descarga() },
    private val ahora: () -> Long = System::currentTimeMillis
) {

    /**
     * @param forzada la pide la persona desde Acerca de, asi que ni el
     *   intervalo ni el interruptor de Ajustes la detienen: pedirla a mano y
     *   que no pase nada se lee como una app rota.
     */
    suspend fun busca(forzada: Boolean = false): ResultadoBusqueda {
        val preferencias = ajustes.first()
        val instante = ahora()

        if (!forzada) {
            if (!preferencias.buscaActualizaciones) return ResultadoBusqueda.OMITIDA
            if (!Actualizaciones.toca(preferencias.ultimaBusquedaDeVersion, instante)) {
                return ResultadoBusqueda.OMITIDA
            }
        }

        val publicada = Actualizaciones.interpreta(consulta())
            ?: return ResultadoBusqueda.SIN_RESPUESTA

        // La fecha se guarda solo cuando hubo respuesta: si se guardara siempre,
        // un dia sin red gastaria el turno y la app pasaria otras 24 horas sin
        // volver a intentarlo.
        guardaVersion(publicada.codigo, publicada.nombre, publicada.url, instante)

        return if (Actualizaciones.hayNovedad(publicada.codigo, versionInstalada)) {
            ResultadoBusqueda.HAY_NOVEDAD
        } else {
            ResultadoBusqueda.AL_DIA
        }
    }
}
