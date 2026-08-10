package mx.ollin.finanzas.di

import android.content.Context
import mx.ollin.finanzas.data.db.OllinDatabase
import mx.ollin.finanzas.data.db.Sembrador
import mx.ollin.finanzas.data.prefs.AjustesRepositorio
import mx.ollin.finanzas.data.repo.FinanzasRepositorio
import mx.ollin.finanzas.data.seguridad.ControlBloqueo
import mx.ollin.finanzas.domain.usecase.ReparaDatos
import mx.ollin.finanzas.domain.usecase.RevisaCalidad

/**
 * Inyeccion de dependencias a mano.
 *
 * Con un solo modulo y media docena de objetos compartidos, Hilt aportaria
 * anotaciones y tiempo de compilacion sin resolver ningun problema real.
 * Esto se lee de arriba a abajo y no tiene magia.
 */
class Contenedor(contexto: Context) {

    private val app = contexto.applicationContext

    val baseDeDatos: OllinDatabase by lazy { OllinDatabase.obten(app) }

    val repositorio: FinanzasRepositorio by lazy {
        FinanzasRepositorio(baseDeDatos, app.contentResolver)
    }

    val ajustes: AjustesRepositorio by lazy { AjustesRepositorio(app) }

    val controlBloqueo: ControlBloqueo by lazy { ControlBloqueo(ajustes) }


    val revisaCalidad: RevisaCalidad by lazy { RevisaCalidad(repositorio) }

    val reparaDatos: ReparaDatos by lazy { ReparaDatos(baseDeDatos) }

    val sembrador: Sembrador by lazy {
        Sembrador(
            baseDeDatos.cuentaDao(),
            baseDeDatos.categoriaDao(),
            baseDeDatos.mapeoDescripcionDao()
        )
    }
}
