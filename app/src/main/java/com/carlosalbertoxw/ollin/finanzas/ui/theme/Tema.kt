package com.carlosalbertoxw.ollin.finanzas.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val EsquemaOscuro = darkColorScheme(
    primary = Pigmento.JadeClaro,
    onPrimary = Pigmento.Obsidiana,
    primaryContainer = Pigmento.JadeTenue,
    onPrimaryContainer = Pigmento.JadeClaro,
    secondary = Pigmento.AnilClaro,
    onSecondary = Pigmento.Obsidiana,
    secondaryContainer = Pigmento.AnilTenue,
    onSecondaryContainer = Pigmento.AnilClaro,
    tertiary = Pigmento.CempasuchilClaro,
    onTertiary = Pigmento.Obsidiana,
    tertiaryContainer = Pigmento.CempasuchilTenue,
    onTertiaryContainer = Pigmento.CempasuchilClaro,
    error = Pigmento.GranaClaro,
    onError = Pigmento.Obsidiana,
    errorContainer = Pigmento.GranaTenue,
    onErrorContainer = Pigmento.GranaClaro,
    background = Pigmento.Obsidiana,
    onBackground = Color(0xFFE6EBE9),
    surface = Pigmento.Obsidiana,
    onSurface = Color(0xFFE6EBE9),
    surfaceVariant = Pigmento.ObsidianaSuave,
    onSurfaceVariant = Color(0xFF9AA5A1),
    surfaceContainer = Pigmento.ObsidianaSuave,
    surfaceContainerHigh = Pigmento.ObsidianaClara,
    outline = Color(0xFF3A464B),
    outlineVariant = Color(0xFF2A3438)
)

private val EsquemaClaro = lightColorScheme(
    primary = Pigmento.Jade,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD4EDE0),
    onPrimaryContainer = Color(0xFF0C3A25),
    secondary = Pigmento.Anil,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE6F7),
    onSecondaryContainer = Color(0xFF13294A),
    tertiary = Color(0xFFB07A22),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFBECD2),
    onTertiaryContainer = Color(0xFF3E2A08),
    error = Pigmento.Grana,
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF4A1310),
    background = Pigmento.Papel,
    onBackground = Pigmento.Tinta,
    surface = Pigmento.Papel,
    onSurface = Pigmento.Tinta,
    surfaceVariant = Color(0xFFEDEAE3),
    onSurfaceVariant = Pigmento.TintaSuave,
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    outline = Color(0xFFC5C0B6),
    outlineVariant = Pigmento.PapelSombra
)

/**
 * Tipografia deliberadamente sobria. En una app de dinero, la cifra es la
 * protagonista: los numeros van tabulares y con peso, el texto se hace a un lado.
 */
private val Tipografia = Typography().let { base ->
    base.copy(
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Medium),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Medium)
    )
}

/** Estilo para importes: ancho fijo por digito para que las columnas cuadren. */
val EstiloCifra = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    fontSize = 15.sp
)

val LocalColoresOllin = staticCompositionLocalOf { ColoresOscuros }

@Composable
fun TemaOllin(
    oscuro: Boolean = isSystemInDarkTheme(),
    colorDinamico: Boolean = false,
    contenido: @Composable () -> Unit
) {
    val contexto = LocalContext.current
    val esquema = when {
        colorDinamico && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (oscuro) dynamicDarkColorScheme(contexto) else dynamicLightColorScheme(contexto)
        oscuro -> EsquemaOscuro
        else -> EsquemaClaro
    }
    val semanticos = if (oscuro) ColoresOscuros else ColoresClaros

    CompositionLocalProvider(LocalColoresOllin provides semanticos) {
        MaterialTheme(
            colorScheme = esquema,
            typography = Tipografia,
            content = contenido
        )
    }
}
