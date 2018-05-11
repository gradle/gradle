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
import org.gradle.api.reflect.TypeOf

import org.gradle.cache.internal.CacheKeyBuilder.CacheKeySpec

import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath

import org.gradle.kotlin.dsl.cache.ScriptCache
import org.gradle.kotlin.dsl.codegen.fileHeader
import org.gradle.kotlin.dsl.provider.spi.AccessorsClassPath
import org.gradle.kotlin.dsl.provider.spi.InaccessibilityReason
import org.gradle.kotlin.dsl.provider.spi.ProjectSchema
import org.gradle.kotlin.dsl.provider.spi.ProjectSchemaProvider
import org.gradle.kotlin.dsl.provider.spi.TypeAccessibility
import org.gradle.kotlin.dsl.provider.spi.loadMultiProjectSchemaFrom
import org.gradle.kotlin.dsl.provider.spi.primitiveKotlinTypeNames
import org.gradle.kotlin.dsl.provider.spi.PROJECT_SCHEMA_RESOURCE_PATH
import org.gradle.kotlin.dsl.provider.spi.withKotlinTypeStrings
import org.gradle.kotlin.dsl.support.compileToJar
import org.gradle.kotlin.dsl.support.loggerFor
import org.gradle.kotlin.dsl.support.serviceOf

import org.jetbrains.kotlin.metadata.ProtoBuf
import org.jetbrains.kotlin.metadata.ProtoBuf.Visibility
import org.jetbrains.kotlin.metadata.deserialization.Flags
import org.jetbrains.kotlin.metadata.jvm.deserialization.JvmProtoBufUtil

import org.jetbrains.kotlin.utils.addToStdlib.firstNotNullResult

import org.jetbrains.org.objectweb.asm.AnnotationVisitor
import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.ClassVisitor
import org.jetbrains.org.objectweb.asm.Opcodes.ACC_PUBLIC
import org.jetbrains.org.objectweb.asm.Opcodes.ACC_SYNTHETIC
import org.jetbrains.org.objectweb.asm.Opcodes.ASM6
import org.jetbrains.org.objectweb.asm.signature.SignatureReader
import org.jetbrains.org.objectweb.asm.signature.SignatureVisitor

import java.io.BufferedWriter
import java.io.Closeable
import java.io.File

import java.util.*
import java.util.jar.JarFile


fun accessorsClassPathFor(project: Project, classPath: ClassPath) =
    project.getOrCreateSingletonProperty {
        buildAccessorsClassPathFor(project, classPath)
            ?: AccessorsClassPath.empty
    }


private
fun buildAccessorsClassPathFor(project: Project, classPath: ClassPath) =
    configuredProjectSchemaOf(project)?.let { projectSchema ->
        val cacheDir =
            scriptCacheOf(project)
                .cacheDirFor(cacheKeyFor(projectSchema)) {
                    buildAccessorsJarFor(projectSchema, classPath, outputDir = baseDir)
                }
        AccessorsClassPath(
            DefaultClassPath.of(accessorsJar(cacheDir)),
            DefaultClassPath.of(accessorsSourceDir(cacheDir)))
    }


private
fun configuredProjectSchemaOf(project: Project) =
    aotProjectSchemaOf(project) ?: jitProjectSchemaOf(project)


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


internal
fun schemaFor(project: Project): ProjectSchema<TypeOf<*>> =
    projectSchemaProviderOf(project).schemaFor(project)


private
fun projectSchemaProviderOf(project: Project) =
    project.serviceOf<ProjectSchemaProvider>()


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


internal
fun availableProjectSchemaFor(projectSchema: ProjectSchema<String>, classPath: ClassPath) =
    TypeAccessibilityProvider(classPath).use { accessibilityProvider ->
        projectSchema.map(accessibilityProvider::accessibilityForType)
    }


private
typealias ClassFileIndex = (String) -> ByteArray?


private
data class TypeAccessibilityInfo(
    val inaccessibilityReasons: List<InaccessibilityReason>,
    val hasTypeParameter: Boolean = false
)


internal
class TypeAccessibilityProvider(classPath: ClassPath) : Closeable {

    private
    val classPathIndex = classPath.asFiles.map { classFileIndexFor(it) }

    private
    val openJars = mutableMapOf<File, JarFile>()

    private
    val typeAccessibilityInfoPerClass = mutableMapOf<String, TypeAccessibilityInfo>()

    fun accessibilityForType(type: String): TypeAccessibility =
        inaccessibilityReasonsFor(classNamesFromTypeString(type)).let { inaccessibilityReasons ->
            if (inaccessibilityReasons.isNotEmpty()) inaccessible(type, inaccessibilityReasons)
            else accessible(type)
        }

    private
    fun inaccessibilityReasonsFor(classNames: ClassNamesFromTypeString): List<InaccessibilityReason> =
        classNames.all.flatMap { inaccessibilityReasonsFor(it) }.let { inaccessibilityReasons ->
            if (inaccessibilityReasons.isNotEmpty()) inaccessibilityReasons
            else classNames.leafs.filter { hasTypeParameter(it) }.map { typeErasure(it) }
        }

    private
    fun inaccessibilityReasonsFor(className: String): List<InaccessibilityReason> =
        accessibilityInfoFor(className).inaccessibilityReasons

    private
    fun hasTypeParameter(className: String) =
        accessibilityInfoFor(className).hasTypeParameter

    private
    fun accessibilityInfoFor(className: String): TypeAccessibilityInfo =
        typeAccessibilityInfoPerClass.computeIfAbsent(className) {
            loadAccessibilityInfoFor(it)
        }

    private
    fun loadAccessibilityInfoFor(className: String): TypeAccessibilityInfo {
        val classBytes = classBytesFor(className) ?: return TypeAccessibilityInfo(listOf(nonAvailable(className)))
        val classReader = ClassReader(classBytes)
        val access = classReader.access
        return TypeAccessibilityInfo(
            listOfNotNull(
                when {
                    ACC_PUBLIC !in access -> nonPublic(className)
                    ACC_SYNTHETIC in access -> synthetic(className)
                    isNonPublicKotlinType(classReader) -> nonPublic(className)
                    else -> null
                }),
            hasTypeParameters(classReader)
        )
    }

    private
    fun isNonPublicKotlinType(classReader: ClassReader) =
        kotlinVisibilityFor(classReader)?.let { it != Visibility.PUBLIC } ?: false

    private
    fun kotlinVisibilityFor(classReader: ClassReader) =
        classReader(KotlinVisibilityClassVisitor()).visibility

    private
    fun hasTypeParameters(classReader: ClassReader): Boolean =
        classReader(HasTypeParameterClassVisitor()).hasTypeParameters

    private
    operator fun <T : ClassVisitor> ClassReader.invoke(visitor: T): T =
        visitor.also {
            accept(it, ClassReader.SKIP_CODE + ClassReader.SKIP_DEBUG + ClassReader.SKIP_FRAMES)
        }

    private
    fun classBytesFor(className: String): ByteArray? {
        val classFilePath = className.replace(".", "/") + ".class"
        return classPathIndex.firstNotNullResult { it(classFilePath) }
    }

    private
    fun classFileIndexFor(jarOrDir: File): ClassFileIndex =
        when {
            jarOrDir.isFile -> jarIndexFor(jarOrDir)
            jarOrDir.isDirectory -> directoryIndexFor(jarOrDir)
            else -> { _ -> null }
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
        openJars.computeIfAbsent(file, ::JarFile)

    override fun close() {
        openJars.values.forEach(JarFile::close)
    }

    private
    fun directoryIndexFor(baseDir: File): ClassFileIndex = { classFilePath ->
        File(baseDir, classFilePath).takeIf { it.isFile }?.readBytes()
    }
}


internal
class ClassNamesFromTypeString(
    val all: List<String>,
    val leafs: List<String>
)


internal
fun classNamesFromTypeString(typeString: String): ClassNamesFromTypeString {

    val all = mutableListOf<String>()
    val leafs = mutableListOf<String>()
    var buffer = StringBuilder()

    fun nonPrimitiveKotlinType(): String? =
        if (buffer.isEmpty()) null
        else buffer.toString().let {
            if (it in primitiveKotlinTypeNames) null
            else it
        }

    typeString.forEach { char ->
        when (char) {
            '<' -> {
                nonPrimitiveKotlinType()?.also { all.add(it) }
                buffer = StringBuilder()
            }
            in " ,>" -> {
                nonPrimitiveKotlinType()?.also {
                    all.add(it)
                    leafs.add(it)
                }
                buffer = StringBuilder()
            }
            else -> buffer.append(char)
        }
    }
    nonPrimitiveKotlinType()?.also {
        all.add(it)
        leafs.add(it)
    }
    return ClassNamesFromTypeString(all, leafs)
}


private
class HasTypeParameterClassVisitor : ClassVisitor(ASM6) {

    var hasTypeParameters = false

    override fun visit(version: Int, access: Int, name: String, signature: String?, superName: String?, interfaces: Array<out String>?) {
        if (signature != null) {
            SignatureReader(signature).accept(object : SignatureVisitor(ASM6) {
                override fun visitFormalTypeParameter(name: String) {
                    hasTypeParameters = true
                }
            })
        }
    }
}


private
class KotlinVisibilityClassVisitor : ClassVisitor(ASM6) {

    var visibility: Visibility? = null

    override fun visitAnnotation(desc: String?, visible: Boolean): AnnotationVisitor? =
        when (desc) {
            "Lkotlin/Metadata;" -> ClassDataFromKotlinMetadataAnnotationVisitor { classData ->
                visibility = Flags.VISIBILITY[classData.flags]
            }
            else -> null
        }
}


/**
 * Reads the serialized [ProtoBuf.Class] information stored in the visited [kotlin.Metadata] annotation.
 */
private
class ClassDataFromKotlinMetadataAnnotationVisitor(
    private val onClassData: (ProtoBuf.Class) -> Unit
) : AnnotationVisitor(ASM6) {

    /**
     * @see kotlin.Metadata.d1
     */
    private
    var d1 = mutableListOf<String>()

    /**
     * @see kotlin.Metadata.d2
     */
    private
    var d2 = mutableListOf<String>()

    override fun visitArray(name: String?): AnnotationVisitor? =
        when (name) {
            "d1" -> AnnotationValueCollector(d1)
            "d2" -> AnnotationValueCollector(d2)
            else -> null
        }

    override fun visitEnd() {
        val (_, classData) = JvmProtoBufUtil.readClassDataFrom(d1.toTypedArray(), d2.toTypedArray())
        onClassData(classData)
        super.visitEnd()
    }
}


private
class AnnotationValueCollector<T>(val output: MutableList<T>) : AnnotationVisitor(ASM6) {
    override fun visit(name: String?, value: Any?) {
        @Suppress("unchecked_cast")
        output.add(value as T)
    }
}


private
operator fun Int.contains(flag: Int) =
    and(flag) == flag


internal
fun nonAvailable(type: String): InaccessibilityReason =
    InaccessibilityReason.NonAvailable(type)


internal
fun nonPublic(type: String): InaccessibilityReason =
    InaccessibilityReason.NonPublic(type)


internal
fun synthetic(type: String): InaccessibilityReason =
    InaccessibilityReason.Synthetic(type)


internal
fun typeErasure(type: String): InaccessibilityReason =
    InaccessibilityReason.TypeErasure(type)


internal
fun accessible(type: String): TypeAccessibility =
    TypeAccessibility.Accessible(type)


internal
fun inaccessible(type: String, vararg reasons: InaccessibilityReason) =
    inaccessible(type, reasons.toList())


internal
fun inaccessible(type: String, reasons: List<InaccessibilityReason>): TypeAccessibility =
    TypeAccessibility.Inaccessible(type, reasons)


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
    (extensions.associateBy { "${it.target}.${it.name}" }.mapValues { it.value.type }.asSequence()
        + conventions.associateBy { "${it.target}.${it.name}" }.mapValues { it.value.type }.asSequence()
        + mapEntry("configuration", configurations.sorted().joinToString(",")))
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
