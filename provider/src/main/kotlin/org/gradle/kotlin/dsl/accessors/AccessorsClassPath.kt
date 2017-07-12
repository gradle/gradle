/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.kotlin.dsl.accessors

import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal

import org.gradle.cache.internal.CacheKeyBuilder.CacheKeySpec

import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath

import org.gradle.kotlin.dsl.cache.ScriptCache
import org.gradle.kotlin.dsl.codegen.fileHeader
import org.gradle.kotlin.dsl.support.compileToJar
import org.gradle.kotlin.dsl.support.loggerFor
import org.gradle.kotlin.dsl.support.serviceOf

import org.jetbrains.kotlin.utils.addToStdlib.firstNotNullResult

import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.Opcodes.*

import java.io.BufferedWriter
import java.io.Closeable
import java.io.File

import java.util.*
import java.util.jar.JarFile


fun accessorsClassPathFor(project: Project, classPath: ClassPath) =
    project.getOrCreateSingletonProperty {
        buildAccessorsClassPathFor(project, classPath)
            ?: AccessorsClassPath(ClassPath.EMPTY, ClassPath.EMPTY)
    }


data class AccessorsClassPath(val bin: ClassPath, val src: ClassPath)


private
fun buildAccessorsClassPathFor(project: Project, classPath: ClassPath) =
    configuredProjectSchemaOf(project)?.let { projectSchema ->
        val cacheDir =
            scriptCacheOf(project)
                .cacheDirFor(cacheKeyFor(projectSchema)) {
                    buildAccessorsJarFor(projectSchema, classPath, outputDir = baseDir)
                }
        AccessorsClassPath(
            DefaultClassPath(accessorsJar(cacheDir)),
            DefaultClassPath(accessorsSourceDir(cacheDir)))
    }


private
fun configuredProjectSchemaOf(project: Project) =
    aotProjectSchemaOf(project) ?: jitProjectSchemaOf(project)


internal
sealed class TypeAccessibility {
    data class Accessible(val type: String) : TypeAccessibility()
    data class Inaccessible(val type: String, val reasons: List<InaccessibilityReason>) : TypeAccessibility()
}


internal
sealed class InaccessibilityReason {

    abstract val explanation: String

    data class NonPublic(val type: String) : InaccessibilityReason() {
        override val explanation = "`$type` is not public"
    }

    data class NonAvailable(val type: String) : InaccessibilityReason() {
        override val explanation = "`$type` is not available"
    }

    data class Synthetic(val type: String) : InaccessibilityReason() {
        override val explanation = "`$type` is synthetic"
    }
}


internal
fun availableProjectSchemaFor(projectSchema: ProjectSchema<String>, classPath: ClassPath) =
    TypeAccessibilityProvider(classPath).use { accessibilityProvider ->
        projectSchema.map(accessibilityProvider::accessibilityForType)
    }


private
typealias ClassFileIndex = (String) -> ByteArray?


private
class TypeAccessibilityProvider(classPath: ClassPath) : Closeable {

    private
    val classPathIndex = classPath.asFiles.map { classFileIndexFor(it) }

    private
    val openJars = mutableMapOf<File, JarFile>()

    private
    val inaccessibilityReasonsPerClass = mutableMapOf<String, List<InaccessibilityReason>>()

    fun accessibilityForType(type: String): TypeAccessibility =
        classNamesFrom(type)
            .flatMap { inaccessibilityReasonsFor(it) }
            .let { inaccessibilityReasons ->
                if (inaccessibilityReasons.isNotEmpty()) inaccessible(type, inaccessibilityReasons)
                else accessible(type)
            }

    private
    fun inaccessibilityReasonsFor(className: String): List<InaccessibilityReason> =
        inaccessibilityReasonsPerClass.computeIfAbsent(className) {
            listOfNotNull(inaccessibilityReasonFor(className))
        }

    private
    fun inaccessibilityReasonFor(className: String): InaccessibilityReason? {
        val classBytes = classBytesFor(className) ?: return nonAvailable(className)
        val access = classAccessFrom(classBytes)
        return when {
            !access.hasFlag(ACC_PUBLIC)   -> nonPublic(className)
            access.hasFlag(ACC_SYNTHETIC) -> synthetic(className)
            else                          -> null
        }
    }

    private
    fun classBytesFor(className: String): ByteArray? {
        val classFilePath = className.replace(".", "/") + ".class"
        return classPathIndex.firstNotNullResult { it(classFilePath) }
    }

    private
    fun classFileIndexFor(jarOrDir: File): ClassFileIndex =
        when {
            jarOrDir.isFile      -> jarIndexFor(jarOrDir)
            jarOrDir.isDirectory -> directoryIndexFor(jarOrDir)
            else                 -> { _ -> null }
        }

    private
    fun jarIndexFor(file: File): ClassFileIndex = { classFilePath ->
        openJarFile(file).run {
            getJarEntry(classFilePath)?.let { jarEntry ->
                getInputStream(jarEntry).use { jarInput ->
                    jarInput.readBytes()
                }
            }
        }
    }

    private
    fun openJarFile(file: File) =
        openJars.computeIfAbsent(file, { JarFile(file) })

    override fun close() {
        openJars.values.forEach(JarFile::close)
    }

    private
    fun directoryIndexFor(baseDir: File): ClassFileIndex = { classFilePath ->
        File(baseDir, classFilePath).takeIf { it.isFile }?.readBytes()
    }

    // TODO Exclude primitives
    private
    fun classNamesFrom(typeString: String): List<String> =
        typeString.split(classNameSeparators).filter { it.isNotBlank() }

    private
    val classNameSeparators = Regex("[<,> ]")

    private
    fun classAccessFrom(classBytes: ByteArray): Int =
        ClassReader(classBytes).access
}


private
fun Int.hasFlag(flag: Int) =
    and(flag) == flag


internal
fun nonAvailable(type: String) =
    InaccessibilityReason.NonAvailable(type)


internal
fun nonPublic(type: String) =
    InaccessibilityReason.NonPublic(type)


internal
fun synthetic(type: String) =
    InaccessibilityReason.Synthetic(type)


internal
fun accessible(type: String) =
    TypeAccessibility.Accessible(type)


internal
fun inaccessible(type: String, vararg reasons: InaccessibilityReason) =
    inaccessible(type, reasons.toList())


internal
fun inaccessible(type: String, reasons: List<InaccessibilityReason>) =
    TypeAccessibility.Inaccessible(type, reasons)


private
fun aotProjectSchemaOf(project: Project) =
    project
        .rootProject
        .getOrCreateSingletonProperty { multiProjectSchemaSnapshotOf(project) }
        .schema
        ?.let { it[project.path] }


private
fun jitProjectSchemaOf(project: Project) =
    project.takeIf(::enabledJitAccessors)?.let {
        require(classLoaderScopeOf(project).isLocked) {
            "project.classLoaderScope must be locked before querying the project schema"
        }
        schemaFor(project).withKotlinTypeStrings()
    }


private
fun scriptCacheOf(project: Project) = project.serviceOf<ScriptCache>()


private
fun buildAccessorsJarFor(projectSchema: ProjectSchema<String>, classPath: ClassPath, outputDir: File) {
    val sourceFile = File(accessorsSourceDir(outputDir), "org/gradle/kotlin/dsl/accessors.kt")
    val availableSchema = availableProjectSchemaFor(projectSchema, classPath)
    writeAccessorsTo(sourceFile, availableSchema)
    require(compileToJar(accessorsJar(outputDir), listOf(sourceFile), logger, classPath.asFiles), {
        """
            Failed to compile accessors.

                projectSchema: $projectSchema

                classPath: $classPath

                availableSchema: $availableSchema

        """.replaceIndent()
    })
}

private
val logger by lazy { loggerFor<AccessorsClassPath>() }


private
fun accessorsSourceDir(baseDir: File) = File(baseDir, "src")


private
fun accessorsJar(baseDir: File) = File(baseDir, "gradle-kotlin-dsl-accessors.jar")


private
fun multiProjectSchemaSnapshotOf(project: Project) =
    MultiProjectSchemaSnapshot(
        projectSchemaSnapshotFileOf(project)?.let {
            loadMultiProjectSchemaFrom(it)
        })


private
data class MultiProjectSchemaSnapshot(val schema: Map<String, ProjectSchema<String>>?)


private
fun projectSchemaSnapshotFileOf(project: Project): File? =
    project
        .rootProject
        .file(PROJECT_SCHEMA_RESOURCE_PATH)
        .takeIf { it.isFile }


private
fun classLoaderScopeOf(project: Project) =
    (project as ProjectInternal).classLoaderScope


private
fun cacheKeyFor(projectSchema: ProjectSchema<String>): CacheKeySpec =
    CacheKeySpec.withPrefix("gradle-kotlin-dsl-accessors") + projectSchema.toCacheKeyString()


private
fun ProjectSchema<String>.toCacheKeyString(): String =
    (extensions.entries.asSequence()
     + conventions.entries.asSequence()
     + mapEntry("configurations", configurations.sorted().joinToString(",")))
        .map { "${it.key}=${it.value}" }
        .sorted()
        .joinToString(separator = ":")


private
fun <K, V> mapEntry(key: K, value: V) =
    AbstractMap.SimpleEntry(key, value)


private
fun enabledJitAccessors(project: Project) =
    project.findProperty("org.gradle.kotlin.dsl.accessors")?.let {
        it != "false" && it != "off"
    } ?: true


private
fun writeAccessorsTo(outputFile: File, projectSchema: ProjectSchema<TypeAccessibility>): File =
    outputFile.apply {
        parentFile.mkdirs()
        bufferedWriter().use { writer ->
            writeAccessorsFor(projectSchema, writer)
        }
    }


private
fun writeAccessorsFor(projectSchema: ProjectSchema<TypeAccessibility>, writer: BufferedWriter) {
    writer.apply {
        write(fileHeader)
        newLine()
        appendln("import org.gradle.api.Project")
        appendln("import org.gradle.api.artifacts.Configuration")
        appendln("import org.gradle.api.artifacts.ConfigurationContainer")
        appendln("import org.gradle.api.artifacts.Dependency")
        appendln("import org.gradle.api.artifacts.ExternalModuleDependency")
        appendln("import org.gradle.api.artifacts.ModuleDependency")
        appendln("import org.gradle.api.artifacts.dsl.DependencyHandler")
        newLine()
        appendln("import org.gradle.kotlin.dsl.*")
        newLine()
        projectSchema.forEachAccessor {
            appendln(it)
        }
    }
}


/**
 * Location of the project schema snapshot taken by the _kotlinDslAccessorsSnapshot_ task relative to the root project.
 *
 * @see org.gradle.kotlin.dsl.accessors.tasks.UpdateProjectSchema
 */
internal
const val PROJECT_SCHEMA_RESOURCE_PATH = "gradle/project-schema.json"
