package org.gradle.client.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.loadImageBitmap
import com.materialkolor.PaletteStyle
import com.materialkolor.ktx.rememberThemeColor
import com.materialkolor.rememberDynamicColorScheme
import java.io.InputStream

@Composable
fun GradleClientTheme(
    content: @Composable () -> Unit
) {
    // Derive theme colors from Gradle logo image
    val seedImage = remember { loadImageBitmap(GradleClientTheme.iconResourceAsStream()) }
    val seedColor = rememberThemeColor(seedImage)
    val colors = rememberDynamicColorScheme(
        seedColor = seedColor,
        isDark = false, // isSystemInDarkTheme(),
        style = PaletteStyle.Fidelity,
    )
    MaterialTheme(
        colorScheme = colors,
        content = { content() }
    )
}

private object GradleClientTheme {
    fun iconResourceAsStream(): InputStream =
        this::class.java.classLoader.getResourceAsStream("icons/icon_gradle_rgb.png")!!
}
