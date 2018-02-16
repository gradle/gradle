package org.gradle.modules

import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.*
import java.io.File
import java.util.concurrent.Callable

@CacheableTask
open class PatchExternalModules : DefaultTask() {
    @Internal
    lateinit var modulesToPatch: Configuration

    @Input
    fun getNamesOfModulesToPatch(): Set<String> {
        return modulesToPatch.dependencies.map { it.name }.toSet()
    }

    @Internal
    lateinit var allModules: Configuration

    @Input
    fun getFileNamesOfAllModules(): Set<String> {
        return allModules.incoming.artifacts.map { it.file.name }.toSet()
    }

    @Internal
    lateinit var coreModules: Configuration

    @Input
    fun getFileNamesOfCoreModules(): Set<String> {
        return coreModules.incoming.artifacts.map { it.file.name }.toSet()
    }

    @Classpath
    fun getExternalModules(): FileCollection {
        return allModules.minus(coreModules)
    }

    @OutputDirectory
    lateinit var destination: File

    init {
        description = "Patches the classpath manifests and content of external modules such as gradle-kotlin-dsl to match the Gradle runtime configuration."
        dependsOn(Callable { modulesToPatch })
    }

    @TaskAction
    fun patch() {
        project.sync {
            this.from(getExternalModules())
            this.into(destination)
        }

        ClasspathManifestPatcher(project.rootProject, temporaryDir, allModules, getNamesOfModulesToPatch())
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
