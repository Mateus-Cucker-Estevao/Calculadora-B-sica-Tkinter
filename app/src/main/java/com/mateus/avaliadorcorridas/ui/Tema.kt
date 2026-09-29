package com.mateus.avaliadorcorridas.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Cores do modelo (code.html), tema escuro. */
object Cores {
    val fundo = Color(0xFF131315)
    val superficie = Color(0xFF131315)
    val container = Color(0xFF201F21)
    val containerBaixo = Color(0xFF1B1B1D)
    val containerAlto = Color(0xFF2A2A2C)
    val containerMaisAlto = Color(0xFF353437)
    val primaria = Color(0xFFADC6FF)
    val primariaContainer = Color(0xFF4D8EFF)
    val naPrimariaContainer = Color(0xFF00285D)
    val verde = Color(0xFF4AE176)
    val amarelo = Color(0xFFF7BE1D)
    val vermelho = Color(0xFFFFB4AB)
    val vermelhoForte = Color(0xFFE5484D)
    val texto = Color(0xFFE5E1E4)
    val textoVariante = Color(0xFFC2C6D6)
    val contorno = Color(0xFF8C909F)
    val contornoVariante = Color(0xFF424754)
}

private val esquema = darkColorScheme(
    primary = Cores.primaria,
    onPrimary = Color(0xFF002E6A),
    primaryContainer = Cores.primariaContainer,
    onPrimaryContainer = Cores.naPrimariaContainer,
    secondary = Cores.verde,
    onSecondary = Color(0xFF003915),
    tertiary = Cores.amarelo,
    error = Cores.vermelho,
    background = Cores.fundo,
    onBackground = Cores.texto,
    surface = Cores.superficie,
    onSurface = Cores.texto,
    surfaceVariant = Cores.containerMaisAlto,
    onSurfaceVariant = Cores.textoVariante,
    surfaceContainer = Cores.container,
    surfaceContainerLow = Cores.containerBaixo,
    surfaceContainerHigh = Cores.containerAlto,
    surfaceContainerHighest = Cores.containerMaisAlto,
    outline = Cores.contorno,
    outlineVariant = Cores.contornoVariante,
)

@Composable
fun TemaAvaliador(conteudo: @Composable () -> Unit) {
    MaterialTheme(colorScheme = esquema, content = conteudo)
}
