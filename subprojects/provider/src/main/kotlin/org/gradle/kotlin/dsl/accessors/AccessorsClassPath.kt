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

import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.Hasher
import org.gradle.internal.hash.Hashing

import org.gradle.kotlin.dsl.cache.ScriptCache
import org.gradle.kotlin.dsl.codegen.fileHeader

import org.gradle.kotlin.dsl.concurrent.IO
import org.gradle.kotlin.dsl.concurrent.withAsynchronousIO

import org.gradle.kotlin.dsl.support.ClassBytesRepository
import org.gradle.kotlin.dsl.support.appendReproducibleNewLine
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.kotlin.dsl.support.useToRun

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

import java.io.Closeable
import java.io.File


fun projectAccessorsClassPath(project: Project, classPath: ClassPath): AccessorsClassPath =
    project.getOrCreateProperty("gradleKotlinDsl.projectAccessorsClassPath") {
        buildAccessorsClassPathFor(project, classPath)
            ?: AccessorsClassPath.empty
    }


private
fun buildAccessorsClassPathFor(project: Project, classPath: ClassPath) =
    configuredProjectSchemaOf(project)?.let { projectSchema ->
        // TODO:accessors make cache key computation more efficient
        cachedAccessorsClassPathFor(project, cacheKeyFor(projectSchema, classPath)) { srcDir, binDir ->
            withAsynchronousIO(project) {
                buildAccessorsFor(
                    projectSchema,
                    classPath,
                    srcDir = srcDir,
                    binDir = binDir
                )
            }
        }
    }


data class AccessorsClassPath(val bin: ClassPath, val src: ClassPath) {

    companion object {
        val empty = AccessorsClassPath(ClassPath.EMPTY, ClassPath.EMPTY)
    }

    operator fun plus(other: AccessorsClassPath) =
        AccessorsClassPath(bin + other.bin, src + other.src)
}


internal
fun cachedAccessorsClassPathFor(
    project: Project,
    cacheKeySpec: CacheKeySpec,
    builder: (File, File) -> Unit
): AccessorsClassPath {

    val cacheDir =
        scriptCacheOf(project)
            .cacheDirFor(cacheKeySpec) { baseDir ->
                builder(
                    accessorsSourceDir(baseDir),
                    accessorsClassesDir(baseDir)
                )
            }

    return AccessorsClassPath(
        DefaultClassPath.of(accessorsClassesDir(cacheDir)),
        DefaultClassPath.of(accessorsSourceDir(cacheDir))
    )
}


private
fun accessorsSourceDir(baseDir: File) = baseDir.resolve("src")


private
fun accessorsClassesDir(baseDir: File) = baseDir.resolve("classes")


private
fun configuredProjectSchemaOf(project: Project): TypedProjectSchema? =
    if (enabledJitAccessors(project)) {
        require(classLoaderScopeOf(project).isLocked) {
            "project.classLoaderScope must be locked before querying the project schema"
        }
        schemaFor(project).takeIf { it.isNotEmpty() }
    } else null


internal
fun schemaFor(project: Project): TypedProjectSchema =
    projectSchemaProviderOf(project).schemaFor(project)


private
fun projectSchemaProviderOf(project: Project) =
    project.serviceOf<ProjectSchemaProvider>()


private
fun scriptCacheOf(project: Project) = project.serviceOf<ScriptCache>()


internal
fun IO.buildAccessorsFor(
    projectSchema: TypedProjectSchema,
    classPath: ClassPath,
    srcDir: File,
    binDir: File
) {
    val availableSchema = availableProjectSchemaFor(projectSchema, classPath)
    emitAccessorsFor(
        availableSchema,
        srcDir,
        binDir
    )
}


internal
fun importsRequiredBy(candidateTypes: List<TypeAccessibility>): List<String> =
    defaultPackageTypesIn(
        candidateTypes
            .filterIsInstance<TypeAccessibility.Accessible>()
            .map { it.type.kotlinString }
    )


internal
fun defaultPackageTypesIn(typeStrings: List<String>): List<String> =
    typeStrings
        .flatMap { classNamesFromTypeString(it).all }
        .filter { '.' !in it }
        .distinct()


internal
fun availableProjectSchemaFor(projectSchema: TypedProjectSchema, classPath: ClassPath) =
    TypeAccessibilityProvider(classPath).use { accessibilityProvider ->
        projectSchema.map(accessibilityProvider::accessibilityForType)
    }


sealed class TypeAccessibility {
    data class Accessible(val type: SchemaType) : TypeAccessibility()
    data class Inaccessible(val type: SchemaType, val reasons: List<InaccessibilityReason>) : TypeAccessibility()
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

    fun accessibilityForType(type: SchemaType): TypeAccessibility =
    // TODO:accessors cache per SchemaType
        inaccessibilityReasonsFor(classNamesFromTypeString(type)).let { inaccessibilityReasons ->
            if (inaccessibilityReasons.isNotEmpty()) inaccessible(type, inaccessibilityReasons)
            else accessible(type)
        }

    private
    fun inaccessibilityReasonsFor(classNames: ClassNamesFromTypeString): List<InaccessibilityReason> =
        classNames.all.flatMap { inaccessibilityReasonsFor(it) }.let { inaccessibilityReasons ->
            if (inaccessibilityReasons.isNotEmpty()) inaccessibilityReasons
            else classNames.leaves.filter(::hasTypeParameter).map(::typeErasure)
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
    val leaves: List<String>
)


internal
fun classNamesFromTypeString(type: SchemaType): ClassNamesFromTypeString =
    classNamesFromTypeString(type.kotlinString)


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
     * @see kotlin.Metadata.data1
     */
    private
    var d1 = mutableListOf<String>()

    /**
     * @see kotlin.Metadata.data2
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
fun accessible(type: SchemaType): TypeAccessibility =
    TypeAccessibility.Accessible(type)


internal
fun inaccessible(type: SchemaType, vararg reasons: InaccessibilityReason) =
    inaccessible(type, reasons.toList())


internal
fun inaccessible(type: SchemaType, reasons: List<InaccessibilityReason>): TypeAccessibility =
    TypeAccessibility.Inaccessible(type, reasons)


private
fun classLoaderScopeOf(project: Project) =
    (project as ProjectInternal).classLoaderScope


internal
val accessorsCacheKeyPrefix = CacheKeySpec.withPrefix("gradle-kotlin-dsl-accessors")


private
fun cacheKeyFor(projectSchema: TypedProjectSchema, classPath: ClassPath): CacheKeySpec =
    (accessorsCacheKeyPrefix
        + hashCodeFor(projectSchema)
        + classPath)


internal
fun hashCodeFor(schema: TypedProjectSchema): HashCode = Hashing.newHasher().run {
    putAll(schema.extensions)
    putAll(schema.conventions)
    putAll(schema.tasks)
    putAll(schema.containerElements)
    putAllSorted(schema.configurations)
    hash()
}


private
fun Hasher.putAllSorted(strings: List<String>) {
    putInt(strings.size)
    strings.sorted().forEach(::putString)
}


private
fun Hasher.putAll(entries: List<ProjectSchemaEntry<SchemaType>>) {
    putInt(entries.size)
    entries.forEach { entry ->
        putString(entry.target.kotlinString)
        putString(entry.name)
        putString(entry.type.kotlinString)
    }
}


private
fun enabledJitAccessors(project: Project) =
    project.findProperty("org.gradle.kotlin.dsl.accessors")?.let {
        it != "false" && it != "off"
    } ?: true


internal
fun IO.writeAccessorsTo(outputFile: File, accessors: List<String>, imports: List<String> = emptyList()) = io {
    outputFile.bufferedWriter().useToRun {
        appendReproducibleNewLine(fileHeaderWithImports)
        if (imports.isNotEmpty()) {
            imports.forEach {
                appendReproducibleNewLine("import $it")
            }
            appendReproducibleNewLine()
        }
        accessors.forEach {
            appendReproducibleNewLine(it.replaceIndent())
            appendReproducibleNewLine()
        }
    }
}


internal
val fileHeaderWithImports = """
$fileHeader

import org.gradle.api.Action
import org.gradle.api.Incubating
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurablePublishArtifact
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.DependencyConstraint
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.artifacts.dsl.ArtifactHandler
import org.gradle.api.artifacts.dsl.DependencyConstraintHandler
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider

import org.gradle.kotlin.dsl.*
import org.gradle.kotlin.dsl.accessors.runtime.*

"""


/**
 * Location of the discontinued project schema snapshot, relative to the root project.
 */
const val projectSchemaResourcePath =
    "gradle/project-schema.json"


const val projectSchemaResourceDiscontinuedWarning =
    "Support for $projectSchemaResourcePath was removed in Gradle 5.0. The file is no longer used and it can be safely deleted."


fun Project.warnAboutDiscontinuedJsonProjectSchema() {
    if (file(projectSchemaResourcePath).isFile) {
        logger.warn(projectSchemaResourceDiscontinuedWarning)
    }
}
