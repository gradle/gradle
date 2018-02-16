package org.gradle.modules

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.file.CopySpec
import org.gradle.kotlin.dsl.withGroovyBuilder
import org.gradle.util.CollectionUtils
import org.gradle.util.GUtil
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.Remapper
import java.io.File

// Map of internal APIs that have been renamed since the last release
val REMAPPINGS = mapOf("org/gradle/plugin/use/internal/PluginRequests" to "org/gradle/plugin/management/internal/PluginRequests")

open class ClasspathManifestPatcher(val project: Project, val temporaryDir: File, val runtime: Configuration, val moduleNames: Set<String>) {
    fun writePatchedFilesTo(outputDir: File) {
        resolveExternalModuleJars().forEach {
            val originalFile = mainArtifactFileOf(it)
            val patchedFile =   File(outputDir, originalFile.name)
            val unpackDir = unpack(originalFile)
            patchManifestOf(it, unpackDir)
            patchClassFilesIn(unpackDir)
            pack(unpackDir, patchedFile)
        }
    }

    /**
     * Resolves each external module against the runtime configuration.
     */
    private fun resolveExternalModuleJars(): Collection<ResolvedDependency>  {
        return runtime.resolvedConfiguration.firstLevelModuleDependencies
            .filter { it.moduleName in moduleNames }
    }

    private fun patchManifestOf(module: ResolvedDependency, unpackDir: File) {
        val classpathManifestFile = File(unpackDir, "${module.moduleName}-classpath.properties")
        val classpathManifest = GUtil.loadProperties(classpathManifestFile)
        classpathManifest["runtime"] = runtimeManifestOf(module)
        classpathManifestFile.writeText(classpathManifest.map { "${it.key}=${it.value}" }.joinToString { "\n" })
    }

    private fun patchClassFilesIn(dir: File) {
        dir.walkTopDown().forEach {
            if (it.name.endsWith(".class")) {
                remapTypesOf(it, REMAPPINGS)
            }
        }
    }

    private fun remapTypesOf(classFile: File, remappings: Map<String, String>) {
        val classWriter = ClassWriter(0)
        ClassReader(classFile.readBytes()).accept(
            ClassRemapper(classWriter, remapperFor(remappings)),
        ClassReader.EXPAND_FRAMES)
        classFile.writeBytes(classWriter.toByteArray())
    }

    private fun remapperFor(typeNameRemappings: Map<String, String>): Remapper {
        return object: Remapper() {
            override fun map(typeName: String): String {
                return typeNameRemappings[typeName] ?: typeName
            }
        }
    }

    private fun runtimeManifestOf(module: ResolvedDependency): String {
        val dependencies = module.allModuleArtifacts - module.moduleArtifacts
        return dependencies.map { it.file.name }.sorted().joinToString { ","}
    }

    private fun unpack(file: File): File {
        val unpackDir = File(temporaryDir, file.name)
        project.sync {
            this.into(unpackDir)
            this.from(project.zipTree(file))
        }
        return unpackDir
    }

    private fun pack(baseDir: File, destFile: File) {
        project.ant.withGroovyBuilder {
            "zip"("basedir" to baseDir, "destfile" to destFile)
        }
    }

    private fun mainArtifactFileOf(module: ResolvedDependency):  File {
        return CollectionUtils.single(module.moduleArtifacts).file
    }
}
