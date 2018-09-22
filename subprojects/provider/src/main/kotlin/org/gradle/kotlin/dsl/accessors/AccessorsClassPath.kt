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
import org.gradle.kotlin.dsl.support.ClassBytesRepository
import org.gradle.kotlin.dsl.support.compileToDirectory
import org.gradle.kotlin.dsl.support.loggerFor
import org.gradle.kotlin.dsl.support.serviceOf

import org.jetbrains.kotlin.metadata.ProtoBuf
import org.jetbrains.kotlin.metadata.ProtoBuf.Visibility
import org.jetbrains.kotlin.metadata.deserialization.Flags
import org.jetbrains.kotlin.metadata.jvm.deserialization.JvmProtoBufUtil

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


fun accessorsClassPathFor(project: Project, classPath: ClassPath) =
    project.getOrCreateSingletonProperty {
        buildAccessorsClassPathFor(project, classPath)
            ?: AccessorsClassPath.empty
    }


data class AccessorsClassPath(val bin: ClassPath, val src: ClassPath) {
    companion object {
        val empty = AccessorsClassPath(ClassPath.EMPTY, ClassPath.EMPTY)
    }
}


private
fun buildAccessorsClassPathFor(project: Project, classPath: ClassPath) =
    configuredProjectSchemaOf(project)?.let { projectSchema ->
        val cacheDir =
            scriptCacheOf(project)
                .cacheDirFor(cacheKeyFor(projectSchema, classPath)) { baseDir ->
                    buildAccessorsClassesFor(projectSchema, classPath, outputDir = baseDir)
                }
        AccessorsClassPath(
            DefaultClassPath.of(accessorsClassesDir(cacheDir)),
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
        schemaFor(project).takeIf { it.isNotEmpty() }?.withKotlinTypeStrings()
    }


internal
fun <T> ProjectSchema<T>.groupedByTarget(): Map<T, ProjectSchema<T>> =
    entriesPairedWithEntryKind()
        .groupBy { (entry, _) -> entry.target }
        .mapValues { (_, entries) ->
            ProjectSchema(
                extensions = entries.projectSchemaEntriesOf(EntryKind.Extension),
                conventions = entries.projectSchemaEntriesOf(EntryKind.Convention),
                containerElements = entries.projectSchemaEntriesOf(EntryKind.ContainerElement),
                configurations = emptyList()
            )
        }


private
fun <T> ProjectSchema<T>.entriesPairedWithEntryKind() =
    (extensions.map { it to EntryKind.Extension }
        + conventions.map { it to EntryKind.Convention }
        + containerElements.map { it to EntryKind.ContainerElement })


private
fun <T> List<Pair<ProjectSchemaEntry<T>, EntryKind>>.projectSchemaEntriesOf(entryKind: EntryKind) =
    mapNotNull { (entry, kind) -> entry.takeIf { kind == entryKind } }


private
enum class EntryKind { Extension, Convention, ContainerElement }


internal
fun schemaFor(project: Project): ProjectSchema<TypeOf<*>> =
    projectSchemaProviderOf(project).schemaFor(project)


private
fun projectSchemaProviderOf(project: Project) =
    project.serviceOf<ProjectSchemaProvider>()


private
fun scriptCacheOf(project: Project) = project.serviceOf<ScriptCache>()


private
fun buildAccessorsClassesFor(projectSchema: ProjectSchema<String>, classPath: ClassPath, outputDir: File) {

    val availableSchema = availableProjectSchemaFor(projectSchema, classPath)

    val sourceFiles = sourceFilesWithAccessorsFor(availableSchema, outputDir)

    require(
        compileToDirectory(
            accessorsClassesDir(outputDir),
            sourceFiles,
            logger,
            classPath.asFiles
        )
    ) {
        """
            Failed to compile accessors.

                projectSchema: $projectSchema

                classPath: $classPath

                availableSchema: $availableSchema

        """.replaceIndent()
    }
}


private
fun sourceFilesWithAccessorsFor(projectSchema: ProjectSchema<TypeAccessibility>, outputDir: File): List<File> {

    val schemaPerTarget =
        projectSchema.groupedByTarget()

    val sourceFiles =
        ArrayList<File>(schemaPerTarget.size + 1)

    val packageDir =
        accessorsSourceDir(outputDir).resolve("org/gradle/kotlin/dsl")

    fun sourceFile(name: String) =
        packageDir.resolve(name).also { sourceFiles.add(it) }

    packageDir.mkdirs()

    for ((index, schemaSubset) in schemaPerTarget.values.withIndex()) {
        writeAccessorsTo(
            sourceFile("Accessors$index.kt"),
            schemaSubset.extensionAccessors(),
            importsRequiredBy(schemaSubset)
        )
    }

    writeAccessorsTo(
        sourceFile("ConfigurationAccessors.kt"),
        projectSchema.configurationAccessors()
    )

    return sourceFiles
}


private
fun importsRequiredBy(schemaSubset: ProjectSchema<TypeAccessibility>): List<String> =
    defaultPackageTypesIn(
        schemaSubset
            .extensions
            .flatMap { listOf(it.target, it.type) }
            .filterIsInstance<TypeAccessibility.Accessible>()
            .map { it.type }
    )


internal
fun defaultPackageTypesIn(typeStrings: List<String>): List<String> =
    typeStrings
        .flatMap { classNamesFromTypeString(it).all }
        .filter { '.' !in it }
        .distinct()


internal
fun availableProjectSchemaFor(projectSchema: ProjectSchema<String>, classPath: ClassPath) =
    TypeAccessibilityProvider(classPath).use { accessibilityProvider ->
        projectSchema.map(accessibilityProvider::accessibilityForType)
    }


sealed class TypeAccessibility {
    data class Accessible(val type: String) : TypeAccessibility()
    data class Inaccessible(val type: String, val reasons: List<InaccessibilityReason>) : TypeAccessibility()
}


sealed class InaccessibilityReason {

    data class NonPublic(val type: String) : InaccessibilityReason()
    data class NonAvailable(val type: String) : InaccessibilityReason()
    data class Synthetic(val type: String) : InaccessibilityReason()
    data class TypeErasure(val type: String) : InaccessibilityReason()

    val explanation
        get() = when (this) {
            is NonPublic -> "`$type` is not public"
            is NonAvailable -> "`$type` is not available"
            is Synthetic -> "`$type` is synthetic"
            is TypeErasure -> "`$type` parameter types are missing"
        }
}


private
data class TypeAccessibilityInfo(
    val inaccessibilityReasons: List<InaccessibilityReason>,
    val hasTypeParameter: Boolean = false
)


internal
class TypeAccessibilityProvider(classPath: ClassPath) : Closeable {

    private
    val classBytesRepository = ClassBytesRepository(classPath)

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
        val classBytes = classBytesRepository.classBytesFor(className)
            ?: return TypeAccessibilityInfo(listOf(nonAvailable(className)))
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

    override fun close() {
        classBytesRepository.close()
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


internal
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
fun accessorsSourceDir(baseDir: File) = baseDir.resolve("src")


private
fun accessorsClassesDir(baseDir: File) = baseDir.resolve("classes")


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
fun cacheKeyFor(projectSchema: ProjectSchema<String>, classPath: ClassPath): CacheKeySpec =
    (CacheKeySpec.withPrefix("gradle-kotlin-dsl-accessors")
        + projectSchema.toCacheKeyString()
        + classPath)


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
fun writeAccessorsTo(outputFile: File, accessors: Sequence<String>, imports: List<String> = emptyList()): Unit =
    outputFile.bufferedWriter().use { writer ->
        writeAccessorsTo(writer, accessors, imports)
    }


private
fun writeAccessorsTo(writer: BufferedWriter, accessors: Sequence<String>, imports: List<String>) {
    writer.apply {
        write(fileHeader)
        newLine()
        appendln("import org.gradle.api.Incubating")
        appendln("import org.gradle.api.NamedDomainObjectProvider")
        appendln("import org.gradle.api.Project")
        appendln("import org.gradle.api.artifacts.Configuration")
        appendln("import org.gradle.api.artifacts.ConfigurationContainer")
        appendln("import org.gradle.api.artifacts.Dependency")
        appendln("import org.gradle.api.artifacts.DependencyConstraint")
        appendln("import org.gradle.api.artifacts.ExternalModuleDependency")
        appendln("import org.gradle.api.artifacts.ModuleDependency")
        appendln("import org.gradle.api.artifacts.dsl.DependencyConstraintHandler")
        appendln("import org.gradle.api.artifacts.dsl.DependencyHandler")
        newLine()
        appendln("import org.gradle.kotlin.dsl.*")
        newLine()
        if (imports.isNotEmpty()) {
            imports.forEach {
                appendln("import $it")
            }
            newLine()
        }
        accessors.forEach {
            appendln(it)
        }
    }
}


/**
 * Location of the project schema snapshot taken by the _kotlinDslAccessorsSnapshot_ task relative to the root project.
 */
const val PROJECT_SCHEMA_RESOURCE_PATH = "gradle/project-schema.json"
