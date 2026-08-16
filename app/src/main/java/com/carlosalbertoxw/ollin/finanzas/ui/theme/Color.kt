package com.carlosalbertoxw.ollin.finanzas.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Paleta de Ollin Finanzas.
 *
 * Los pigmentos vienen de la tradicion mexicana y cada uno tiene un trabajo:
 * jade para lo que entra, grana para lo que sale, cempasuchil para lo que pide
 * atencion, añil para lo interno (traspasos y patrimonio). El fondo es
 * obsidiana, que deja respirar a los cuatro.
 */
object Pigmento {
    val Obsidiana = Color(0xFF12181B)
    val ObsidianaSuave = Color(0xFF1A2226)
    val ObsidianaClara = Color(0xFF242E33)

    val Jade = Color(0xFF2F9E6E)
    val JadeClaro = Color(0xFF6FCFA1)
    val JadeTenue = Color(0xFF163828)

    val Grana = Color(0xFFC4453F)
    val GranaClaro = Color(0xFFF08A83)
    val GranaTenue = Color(0xFF3B1917)

    val Cempasuchil = Color(0xFFE9A13B)
    val CempasuchilClaro = Color(0xFFF5C57E)
    val CempasuchilTenue = Color(0xFF3A2A12)

    val Anil = Color(0xFF3D6DB5)
    val AnilClaro = Color(0xFF8FB2E3)
    val AnilTenue = Color(0xFF16243A)

    val Papel = Color(0xFFF7F5F0)
    val PapelSombra = Color(0xFFE7E3DA)
    val Tinta = Color(0xFF1A1A18)
    val TintaSuave = Color(0xFF5B615E)
}

/** Colores semanticos, para no repartir decisiones de color por las pantallas. */
data class ColoresOllin(
    val entrada: Color,
    val salida: Color,
    val traspaso: Color,
    val patrimonio: Color,
    val alerta: Color,
    val superficieElevada: Color,
    val textoTenue: Color,
    val trazoSuave: Color
)

val ColoresOscuros = ColoresOllin(
    entrada = Pigmento.JadeClaro,
    salida = Pigmento.GranaClaro,
    traspaso = Pigmento.AnilClaro,
    patrimonio = Pigmento.CempasuchilClaro,
    alerta = Pigmento.Cempasuchil,
    superficieElevada = Pigmento.ObsidianaClara,
    textoTenue = Color(0xFF9AA5A1),
    trazoSuave = Color(0xFF2E3A3F)
)

val ColoresClaros = ColoresOllin(
    entrada = Pigmento.Jade,
    salida = Pigmento.Grana,
    traspaso = Pigmento.Anil,
    patrimonio = Color(0xFFB07A22),
    alerta = Color(0xFFB07A22),
    superficieElevada = Color(0xFFFFFFFF),
    textoTenue = Pigmento.TintaSuave,
    trazoSuave = Pigmento.PapelSombra
)
