package com.carlosalbertoxw.ollin.finanzas.data.db

import com.carlosalbertoxw.ollin.finanzas.domain.model.normalizaClave

/**
 * Deja el catalogo listo la primera vez que corre la app. Es idempotente:
 * si una cuenta o categoria ya existe, no la duplica ni la pisa.
 */
class Sembrador(
    private val cuentaDao: CuentaDao,
    private val categoriaDao: CategoriaDao,
    private val mapeoDao: MapeoDescripcionDao
) {

    suspend fun sembrarSiHaceFalta() {
        if (categoriaDao.cuenta() > 0) return
        sembrarCuentas()
        val porNombre = sembrarCategorias()
        sembrarMapeo(porNombre)
    }

    private suspend fun sembrarCuentas() {
        val existentes = cuentaDao.todas().associateBy { it.nombre.normalizaClave() }
        Semilla.CUENTAS.forEach { plantilla ->
            if (existentes.containsKey(plantilla.nombre.normalizaClave())) return@forEach
            cuentaDao.inserta(
                Cuenta(
                    nombre = plantilla.nombre,
                    tipo = plantilla.tipo,
                    medioPorDefecto = Semilla.medioSugerido(plantilla.tipo),
                    orden = plantilla.orden,
                    colorHex = plantilla.colorHex,
                    incluirEnPatrimonio = true
                )
            )
        }
    }

    /** Devuelve nombre normalizado -> id, para toda categoria (padre e hija). */
    private suspend fun sembrarCategorias(): Map<String, Long> {
        val indice = mutableMapOf<String, Long>()
        var orden = 0
        Semilla.CATEGORIAS.forEach { plantilla ->
            val idPadre = categoriaDao.inserta(
                Categoria(
                    nombre = plantilla.padre,
                    padreId = null,
                    tipo = plantilla.tipo,
                    esencial = plantilla.esencial,
                    orden = orden++
                )
            )
            indice[plantilla.padre.normalizaClave()] = idPadre

            plantilla.hijas.forEach { hija ->
                val idHija = categoriaDao.inserta(
                    Categoria(
                        nombre = hija,
                        padreId = idPadre,
                        tipo = plantilla.tipo,
                        esencial = plantilla.esencial,
                        orden = orden++
                    )
                )
                indice[hija.normalizaClave()] = idHija
            }
        }
        return indice
    }

    private suspend fun sembrarMapeo(porNombre: Map<String, Long>) {
        val mapeos = mutableListOf<MapeoDescripcion>()

        // 1. Cada categoria hoja se mapea a si misma por nombre.
        Semilla.CATEGORIAS.forEach { plantilla ->
            plantilla.hijas.forEach { hija ->
                porNombre[hija.normalizaClave()]?.let { id ->
                    mapeos += MapeoDescripcion(clave = hija.normalizaClave(), categoriaId = id)
                }
            }
        }

        // 2. Alias explicitos, que ganan por ser mas especificos.
        Semilla.ALIAS_DESCRIPCION.forEach { (clave, destino) ->
            porNombre[destino.normalizaClave()]?.let { id ->
                mapeos += MapeoDescripcion(clave = clave, categoriaId = id)
            }
        }

        mapeoDao.guardaTodos(mapeos.distinctBy { it.clave })
    }
}
