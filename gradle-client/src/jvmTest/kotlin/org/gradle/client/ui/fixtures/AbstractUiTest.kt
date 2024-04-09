package org.gradle.client.ui.fixtures

import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import org.gradle.client.core.files.AppDirs
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File

abstract class AbstractUiTest {

    @get:Rule
    val tmpDir = TemporaryFolder()

    protected val appDirs: AppDirs by lazy {
        TestAppDirs(tmpDir.root.resolve("appDirs"))
    }

    @OptIn(ExperimentalTestApi::class)
    fun DesktopComposeUiTest.takeScreenshot(name: String) {
        org.jetbrains.skia.Image.makeFromBitmap(captureToImage().asSkiaBitmap()).use { image ->
            val pngFile = File("build/reports/tests-screenshots/$name.png").absoluteFile
            val imageData = image.encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)
                ?: error("Could not encode test screenshot '$name' as png")
            pngFile.apply {
                parentFile.mkdirs()
                writeBytes(imageData.bytes)
            }
            println("Screenshot '$name' saved to file://$pngFile")
        }
    }
}
