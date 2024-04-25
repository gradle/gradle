package org.gradle.client.ui.fixtures

import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import org.gradle.client.core.files.AppDirs
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image.Companion.makeFromBitmap
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestName
import java.io.File

abstract class AbstractUiTest {

    @get:Rule
    val tmpDir = TemporaryFolder()

    protected val appDirs: AppDirs by lazy {
        TestAppDirs(tmpDir.root.resolve("appDirs"))
    }

    @get:Rule
    val testName = TestName()

    private var shotCounter = 0

    @OptIn(ExperimentalTestApi::class)
    fun DesktopComposeUiTest.takeScreenshot(name: String? = null) {
        val filename = buildString {
            append(this@AbstractUiTest::class.simpleName)
            append("_")
            append(testName.methodName.replace(" ", "-"))
            append("_")
            append((++shotCounter).toString().padStart(3, '0'))
            if (!name.isNullOrBlank()) {
                append("_")
                append(name)
            }
        }
        makeFromBitmap(captureToImage().asSkiaBitmap()).use { image ->
            val pngFile = File("build/reports/tests-screenshots/$filename.png").absoluteFile
            val imageData = image.encodeToData(EncodedImageFormat.PNG)
                ?: error("Could not encode test screenshot '$name' as png")
            pngFile.apply {
                parentFile.mkdirs()
                writeBytes(imageData.bytes)
            }
            println("Screenshot '$name' saved to file://$pngFile")
        }
    }
}
