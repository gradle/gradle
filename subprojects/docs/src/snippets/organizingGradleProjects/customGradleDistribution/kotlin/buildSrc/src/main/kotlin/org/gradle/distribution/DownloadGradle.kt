package org.gradle.distribution

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.property
import java.net.URL

/**
 * Downloads a given version of Gradle into a known location.
 */
open class DownloadGradle : DefaultTask() {

    @Input
    val gradleVersion = project.objects.property<String>().convention("6.7")

    @Input
    val gradleDistributionType = project.objects.property<String>().convention("bin")

    @Input
    val gradleDownloadBase = project.objects.property<String>().convention(
            "https://services.gradle.org/distributions"
    )

    @OutputFile
    val destinationFile = project.objects.fileProperty().convention(
            project.layout.projectDirectory.file("${temporaryDir.canonicalPath}/${getDownloadFileName()}")
    )

    @TaskAction
    fun doDownloadGradle() {
        val downloadUrl = URL("${gradleDownloadBase.get()}/${getDownloadFileName()}")
        logger.info("Download: '$downloadUrl'")
        destinationFile.get().asFile.writeBytes(downloadUrl.readBytes())
    }

    @Internal
    fun getDistributionNameBase() = "gradle-${gradleVersion.get()}-${gradleDistributionType.get()}"

    private fun getDownloadFileName() = "${getDistributionNameBase()}.zip"
}
