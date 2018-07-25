package org.gradle.distribution

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.*

class DownloadGradle extends DefaultTask {
    @Input
    String gradleVersion

    @Input
    String gradleDownloadBase = 'http://services.gradle.org/distributions'

    @TaskAction
    void doDownloadGradle() {
        destinationFile.withOutputStream { it << downloadUrl.newInputStream() }
    }

    URL getDownloadUrl() {
        new URL("$gradleDownloadBase/$downloadFileName")
    }

    String getDistributionNameBase() {
        "gradle-$gradleVersion"
    }

    String getDownloadFileName() {
        "$distributionNameBase-bin.zip"
    }

    @OutputFile
    File getDestinationFile() {
        new File(project.file("gradle-downloads"), downloadFileName)
    }
}
