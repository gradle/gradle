package org.gradle.modules

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.util.GUtil

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.Remapper

import java.io.File

import org.gradle.kotlin.dsl.*


// Map of internal APIs that have been renamed since the last release
val REMAPPINGS = mapOf(
    "org/gradle/plugin/use/internal/PluginRequests" to "org/gradle/plugin/management/internal/PluginRequests")


open class ClasspathManifestPatcher(
    private val project: Project,
    private val temporaryDir: File,
    private val runtime: Configuration,
    private val moduleNames: Set<String>
) {

    fun writePatchedFilesTo(outputDir: File) =
        resolveExternalModuleJars().forEach {
            val originalFile = mainArtifactFileOf(it)
            val patchedFile = outputDir.resolve(originalFile.name)
            val unpackDir = unpack(originalFile)
            patchManifestOf(it, unpackDir)
            patchClassFilesIn(unpackDir)
            pack(unpackDir, patchedFile)
        }

    /**
     * Resolves each external module against the runtime configuration.
     */
    private
    fun resolveExternalModuleJars(): Collection<ResolvedDependency> =
        runtime.resolvedConfiguration.firstLevelModuleDependencies
            .filter { it.moduleName in moduleNames }

    private
    fun patchManifestOf(module: ResolvedDependency, unpackDir: File) {
        val classpathManifestFile = unpackDir.resolve("${module.moduleName}-classpath.properties")
        val classpathManifest = GUtil.loadProperties(classpathManifestFile)
        classpathManifest["runtime"] = runtimeManifestOf(module)
        classpathManifestFile.writeText(classpathManifest.toMap().asSequence()
            .joinToString(separator = "\n") { "${it.key}=${it.value}" })
    }

    private
    fun patchClassFilesIn(dir: File) =
        dir.walkTopDown().forEach {
            if (it.name.endsWith(".class")) {
                remapTypesOf(it, REMAPPINGS)
            }
        }

    private
    fun remapTypesOf(classFile: File, remappings: Map<String, String>) {
        val classWriter = ClassWriter(0)
        ClassReader(classFile.readBytes()).accept(
            ClassRemapper(classWriter, remapperFor(remappings)),
            ClassReader.EXPAND_FRAMES)
        classFile.writeBytes(classWriter.toByteArray())
    }

    private
    fun remapperFor(typeNameRemappings: Map<String, String>) =
        object : Remapper() {
            override fun map(typeName: String): String =
                typeNameRemappings[typeName] ?: typeName
        }

    private
    fun runtimeManifestOf(module: ResolvedDependency) =
        (module.allModuleArtifacts - module.moduleArtifacts)
            .map { it.file.name }.sorted().joinToString(",")

    private
    fun unpack(file: File) =
        unpackDirFor(file).also { unpackDir ->
            project.run {
                sync {
                    into(unpackDir)
                    from(zipTree(file))
                }
            }
        }

    private
    fun unpackDirFor(file: File) =
        temporaryDir.resolve(file.name)

    private
    fun pack(baseDir: File, destFile: File): Unit = project.run {
        ant.withGroovyBuilder {
            "zip"("basedir" to baseDir, "destfile" to destFile)
        }
    }

    private
    fun mainArtifactFileOf(module: ResolvedDependency) =
        module.moduleArtifacts.single().file
}
