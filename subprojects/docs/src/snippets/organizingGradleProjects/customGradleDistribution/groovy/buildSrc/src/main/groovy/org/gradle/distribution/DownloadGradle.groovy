package org.gradle.distribution

import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Downloads a given version of Gradle into a known location.
 */
@CompileStatic
class DownloadGradle extends DefaultTask {

    @Input
    final Property<String> gradleVersion = project.objects.property(String).convention("6.7")

    @Input
    final Property<String> gradleDistributionType = project.objects.property(String).convention("bin")

    @Input
    final Property<String> gradleDownloadBase = project.objects.property(String).convention(
            "https://services.gradle.org/distributions"
    )

    @OutputFile
    final RegularFileProperty destinationFile =
            project.objects.fileProperty().convention(
                    project.layout.projectDirectory.file("${temporaryDir.canonicalPath}/${downloadFileName.get()}")
            )

    @TaskAction
    void doDownloadGradle() {
        URL downloadUrl = new URL("${gradleDownloadBase.get()}/${downloadFileName.get()}")
        destinationFile.get().asFile.withOutputStream {
            it << downloadUrl.newInputStream()
        }
    }

    @Internal
    Provider<String> getDistributionNameBase() {
        return gradleVersion.map {
            "gradle-" + it
        }
    }

    private Provider<String> getDownloadFileName() {
        return distributionNameBase.map {
            it + "-" + gradleDistributionType.get() + ".zip"
        }
    }
}
