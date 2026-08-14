package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ChemopunkColorScheme = darkColorScheme(
    primary = PhosphorGreen,
    onPrimary = TerminalBackground,
    primaryContainer = PhosphorGreenDark,
    secondary = AmberTerminal,
    onSecondary = TerminalBackground,
    tertiary = CyberCyan,
    background = TerminalBackground,
    surface = TerminalCardBackground,
    onBackground = TextGreen,
    onSurface = TextGreen,
    error = ToxicRed
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ChemopunkColorScheme,
        typography = Typography,
        content = content
    )
}

