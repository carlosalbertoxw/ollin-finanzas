package com.carlosalbertoxw.ollin.finanzas.di

import android.content.Context
import com.carlosalbertoxw.ollin.finanzas.data.actualizacion.BuscadorDeActualizaciones
import com.carlosalbertoxw.ollin.finanzas.data.actualizacion.VersionInstalada
import com.carlosalbertoxw.ollin.finanzas.data.actualizacion.versionInstalada
import com.carlosalbertoxw.ollin.finanzas.data.db.OllinDatabase
import com.carlosalbertoxw.ollin.finanzas.data.db.Sembrador
import com.carlosalbertoxw.ollin.finanzas.data.prefs.AjustesRepositorio
import com.carlosalbertoxw.ollin.finanzas.data.repo.FinanzasRepositorio
import com.carlosalbertoxw.ollin.finanzas.data.seguridad.ControlBloqueo
import com.carlosalbertoxw.ollin.finanzas.domain.usecase.ReparaDatos
import com.carlosalbertoxw.ollin.finanzas.domain.usecase.RevisaCalidad

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

    val version: VersionInstalada by lazy { versionInstalada(app) }

    val buscadorDeActualizaciones: BuscadorDeActualizaciones by lazy {
        BuscadorDeActualizaciones(
            ajustes.ajustes,
            ajustes::guardaVersionPublicada,
            version.codigo
        )
    }

    val controlBloqueo: ControlBloqueo by lazy {
        ControlBloqueo(ajustes.ajustes, ajustes::guardaFallosDePin)
    }


    val revisaCalidad: RevisaCalidad by lazy { RevisaCalidad(repositorio) }

    val reparaDatos: ReparaDatos by lazy { ReparaDatos(repositorio) }

    val sembrador: Sembrador by lazy {
        Sembrador(
            baseDeDatos.cuentaDao(),
            baseDeDatos.categoriaDao(),
            baseDeDatos.mapeoDescripcionDao()
        )
    }
}
