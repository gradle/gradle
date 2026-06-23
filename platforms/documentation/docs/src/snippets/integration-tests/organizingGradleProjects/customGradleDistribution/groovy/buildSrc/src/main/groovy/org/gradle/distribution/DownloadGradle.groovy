package org.gradle.distribution

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.*
import org.gradle.api.provider.*
import org.gradle.api.file.*

import groovy.transform.CompileStatic

/**
 * Downloads a given version of Gradle into a known location.
 */
@CompileStatic
class DownloadGradle extends DefaultTask {
    @Input
    final Property<String> gradleVersion = project.objects.property(String).value("5.4")

    @Input
    final Property<String> gradleDownloadBase =
        project.objects.property(String).convention("https://services.gradle.org/distributions")

    @OutputFile
    final RegularFileProperty destinationFile =
        project.objects.fileProperty().convention(project.layout.projectDirectory.dir("gradle-downloads/").file(downloadFileName))

    @TaskAction
    void doDownloadGradle() {
        URL downloadUrl = new URL(gradleDownloadBase.get() + "/" + downloadFileName.get())
        destinationFile.get().asFile.withOutputStream { it << downloadUrl.newInputStream() }
    }

    @Internal
    Provider<String> getDistributionNameBase() {
        return gradleVersion.map { "gradle-" + it }
    }

    private Provider<String> getDownloadFileName() {
        return distributionNameBase.map { it + "-bin.zip" }
    }
}
