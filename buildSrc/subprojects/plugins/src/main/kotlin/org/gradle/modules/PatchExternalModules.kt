package org.gradle.modules

import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

import java.io.File


private
val upperCaseLetters = "\\p{Upper}".toRegex()


private
fun String.toKebabCase() =
    replace(upperCaseLetters) { "-${it.value.toLowerCase()}" }


@CacheableTask
open class PatchExternalModules : DefaultTask() {

    @Internal
    lateinit var modulesToPatch: Configuration

    @get:Input
    val namesOfModulesToPatch: Set<String>
        get() = modulesToPatch.dependencies.map {
            when (it) {
                is ProjectDependency -> "gradle-${it.name.toKebabCase()}"
                else -> it.name
            }
        }.toSet()

    @Internal
    lateinit var allModules: Configuration

    @get:Input
    val fileNamesOfAllModules: Set<String>
        get() = allModules.incoming.artifacts.map { it.file.name }.toSet()

    @Internal
    lateinit var coreModules: Configuration

    @get:Input
    val fileNamesOfCoreModules
        get() = coreModules.incoming.artifacts.map { it.file.name }.toSet()

    @get:Classpath
    val externalModules: FileCollection
        get() = allModules.minus(coreModules)

    @OutputDirectory
    lateinit var destination: File

    init {
        description = "Patches the classpath manifests and content of external modules such as gradle-kotlin-dsl to match the Gradle runtime configuration."
        dependsOn(project.provider { modulesToPatch })
    }

    @TaskAction
    fun patch() {
        project.sync {
            from(externalModules)
            into(destination)
        }

        ClasspathManifestPatcher(project.rootProject, temporaryDir, allModules, namesOfModulesToPatch)
            .writePatchedFilesTo(destination)

        // TODO: Should this be configurable?
        JarPatcher(project.rootProject, temporaryDir, allModules, "kotlin-compiler-embeddable")
            .exclude("META-INF/services/java.nio.charset.spi.CharsetProvider")
            .exclude("META-INF/services/javax.annotation.processing.Processor")
            .exclude("net/rubygrapefruit/platform/**")
            .exclude("org/fusesource/jansi/**")
            .exclude("META-INF/native/**/*jansi.*")
            .includeJar("native-platform-")
            .includeJar("jansi-", "META-INF/native/**", "org/fusesource/jansi/internal/CLibrary*.class")
            .writePatchedFilesTo(destination)
    }
}
