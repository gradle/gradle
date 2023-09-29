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
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.internal.classanalysis.AsmConstants.ASM_LEVEL
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.internal.execution.ExecutionEngine
import org.gradle.internal.execution.InputFingerprinter
import org.gradle.internal.execution.UnitOfWork
import org.gradle.internal.execution.UnitOfWork.InputFileValueSupplier
import org.gradle.internal.execution.UnitOfWork.InputVisitor
import org.gradle.internal.execution.UnitOfWork.OutputFileValueSupplier
import org.gradle.internal.execution.model.InputNormalizer
import org.gradle.internal.file.TreeType.DIRECTORY
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint
import org.gradle.internal.fingerprint.DirectorySensitivity
import org.gradle.internal.fingerprint.LineEndingSensitivity
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.Hasher
import org.gradle.internal.hash.Hashing
import org.gradle.internal.properties.InputBehavior.NON_INCREMENTAL
import org.gradle.internal.snapshot.ValueSnapshot
import org.gradle.kotlin.dsl.cache.KotlinDslWorkspaceProvider
import org.gradle.kotlin.dsl.concurrent.IO
import org.gradle.kotlin.dsl.concurrent.withAsynchronousIO
import org.gradle.kotlin.dsl.internal.sharedruntime.codegen.fileHeaderFor
import org.gradle.kotlin.dsl.internal.sharedruntime.codegen.kotlinDslPackageName
import org.gradle.kotlin.dsl.support.ClassBytesRepository
import org.gradle.kotlin.dsl.support.appendReproducibleNewLine
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
import org.jetbrains.org.objectweb.asm.signature.SignatureReader
import org.jetbrains.org.objectweb.asm.signature.SignatureVisitor
import java.io.Closeable
import java.io.File
import javax.inject.Inject


class ProjectAccessorsClassPathGenerator @Inject internal constructor(
    private val fileCollectionFactory: FileCollectionFactory,
    private val projectSchemaProvider: ProjectSchemaProvider,
    private val executionEngine: ExecutionEngine,
    private val inputFingerprinter: InputFingerprinter,
    private val workspaceProvider: KotlinDslWorkspaceProvider
) {

    fun projectAccessorsClassPath(project: Project, classPath: ClassPath): AccessorsClassPath =
        project.getOrCreateProperty("gradleKotlinDsl.projectAccessorsClassPath") {
            buildAccessorsClassPathFor(project, classPath)
                ?: AccessorsClassPath.empty
        }


    private
    fun buildAccessorsClassPathFor(project: Project, classPath: ClassPath): AccessorsClassPath? {
        return configuredProjectSchemaOf(project)?.let { projectSchema ->
            val work = GenerateProjectAccessors(
                project,
                projectSchema,
                classPath,
                fileCollectionFactory,
                inputFingerprinter,
                workspaceProvider
            )
            val result = executionEngine.createRequest(work).execute()
            result.execution.get().output as AccessorsClassPath
        }
    }


    private
    fun configuredProjectSchemaOf(project: Project): TypedProjectSchema? {
        require(classLoaderScopeOf(project).isLocked) {
            "project.classLoaderScope must be locked before querying the project schema"
        }
        return projectSchemaProvider.schemaFor(project).takeIf { it.isNotEmpty() }
    }
}


internal
class GenerateProjectAccessors(
    private val project: Project,
    private val projectSchema: TypedProjectSchema,
    private val classPath: ClassPath,
    private val fileCollectionFactory: FileCollectionFactory,
    private val inputFingerprinter: InputFingerprinter,
    private val workspaceProvider: KotlinDslWorkspaceProvider
) : UnitOfWork {

    companion object {
        const val PROJECT_SCHEMA_INPUT_PROPERTY = "projectSchema"
        const val CLASSPATH_INPUT_PROPERTY = "classpath"
        const val SOURCES_OUTPUT_PROPERTY = "sources"
        const val CLASSES_OUTPUT_PROPERTY = "classes"
    }

    override fun execute(executionRequest: UnitOfWork.ExecutionRequest): UnitOfWork.WorkOutput {
        val workspace = executionRequest.workspace
        withAsynchronousIO(project) {
            buildAccessorsFor(
                projectSchema,
                classPath,
                srcDir = getSourcesOutputDir(workspace),
                binDir = getClassesOutputDir(workspace)
            )
        }
        return object : UnitOfWork.WorkOutput {
            override fun getDidWork() = UnitOfWork.WorkResult.DID_WORK

            override fun getOutput() = loadAlreadyProducedOutput(workspace)
        }
    }

    override fun loadAlreadyProducedOutput(workspace: File) = AccessorsClassPath(
        DefaultClassPath.of(getClassesOutputDir(workspace)),
        DefaultClassPath.of(getSourcesOutputDir(workspace))
    )

    override fun identify(identityInputs: Map<String, ValueSnapshot>, identityFileInputs: Map<String, CurrentFileCollectionFingerprint>): UnitOfWork.Identity {
        val hasher = Hashing.newHasher()
        requireNotNull(identityInputs[PROJECT_SCHEMA_INPUT_PROPERTY]).appendToHasher(hasher)
        hasher.putHash(requireNotNull(identityFileInputs[CLASSPATH_INPUT_PROPERTY]).hash)
        val identityHash = hasher.hash().toString()
        return UnitOfWork.Identity { identityHash }
    }

    override fun getWorkspaceProvider() = workspaceProvider.accessors

    override fun getInputFingerprinter() = inputFingerprinter

    override fun getDisplayName(): String = "Kotlin DSL accessors for $project"

    override fun visitIdentityInputs(visitor: InputVisitor) {
        visitor.visitInputProperty(PROJECT_SCHEMA_INPUT_PROPERTY) { hashCodeFor(projectSchema) }
        visitor.visitInputFileProperty(
            CLASSPATH_INPUT_PROPERTY,
            NON_INCREMENTAL,
            InputFileValueSupplier(
                classPath,
                InputNormalizer.RUNTIME_CLASSPATH,
                DirectorySensitivity.IGNORE_DIRECTORIES,
                LineEndingSensitivity.DEFAULT,
            ) { fileCollectionFactory.fixed(classPath.asFiles) }
        )
    }

    override fun visitOutputs(workspace: File, visitor: UnitOfWork.OutputVisitor) {
        val sourcesOutputDir = getSourcesOutputDir(workspace)
        val classesOutputDir = getClassesOutputDir(workspace)
        visitor.visitOutputProperty(SOURCES_OUTPUT_PROPERTY, DIRECTORY, OutputFileValueSupplier.fromStatic(sourcesOutputDir, fileCollectionFactory.fixed(sourcesOutputDir)))
        visitor.visitOutputProperty(CLASSES_OUTPUT_PROPERTY, DIRECTORY, OutputFileValueSupplier.fromStatic(classesOutputDir, fileCollectionFactory.fixed(classesOutputDir)))
    }
}


private
fun getClassesOutputDir(workspace: File) = File(workspace, "classes")


private
fun getSourcesOutputDir(workspace: File): File = File(workspace, "sources")


data class AccessorsClassPath(val bin: ClassPath, val src: ClassPath) {

    companion object {
        val empty = AccessorsClassPath(ClassPath.EMPTY, ClassPath.EMPTY)
    }

    operator fun plus(other: AccessorsClassPath) =
        AccessorsClassPath(bin + other.bin, src + other.src)
}


fun IO.buildAccessorsFor(
    projectSchema: TypedProjectSchema,
    classPath: ClassPath,
    srcDir: File,
    binDir: File?,
    packageName: String = kotlinDslPackageName,
    format: AccessorFormat = AccessorFormats.default
) {
    val availableSchema = availableProjectSchemaFor(projectSchema, classPath)
    emitAccessorsFor(
        availableSchema,
        srcDir,
        binDir,
        OutputPackage(packageName),
        format
    )
}


typealias AccessorFormat = (String) -> String


object AccessorFormats {

    val default: AccessorFormat = { accessor ->
        accessor.trimIndent()
    }

    val `internal`: AccessorFormat = { accessor ->
        accessor
            .trimIndent()
            .let { valFunOrClass.matcher(it) }
            .replaceAll("internal\n$1 ")
    }

    private
    val valFunOrClass by lazy {
        "^(val|fun|class) ".toRegex(RegexOption.MULTILINE).toPattern()
    }
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
                }
            ),
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
        buffer.takeIf(StringBuilder::isNotEmpty)?.toString()?.let {
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
class HasTypeParameterClassVisitor : ClassVisitor(ASM_LEVEL) {

    var hasTypeParameters = false

    override fun visit(version: Int, access: Int, name: String, signature: String?, superName: String?, interfaces: Array<out String>?) {
        if (signature != null) {
            SignatureReader(signature).accept(object : SignatureVisitor(ASM_LEVEL) {
                override fun visitFormalTypeParameter(name: String) {
                    hasTypeParameters = true
                }
            })
        }
    }
}


private
class KotlinVisibilityClassVisitor : ClassVisitor(ASM_LEVEL) {

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
) : AnnotationVisitor(ASM_LEVEL) {

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
class AnnotationValueCollector<T>(val output: MutableList<T>) : AnnotationVisitor(ASM_LEVEL) {
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


fun hashCodeFor(schema: TypedProjectSchema): HashCode = Hashing.newHasher().run {
    putAll(schema.extensions)
    putAll(schema.conventions)
    putAll(schema.tasks)
    putAll(schema.containerElements)
    putAllSorted(schema.configurations.map { it.target })
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


internal
fun IO.writeAccessorsTo(
    outputFile: File,
    accessors: Iterable<String>,
    imports: List<String> = emptyList(),
    packageName: String = kotlinDslPackageName
) = io {
    outputFile.bufferedWriter().useToRun {
        appendReproducibleNewLine(fileHeaderWithImportsFor(packageName))
        if (imports.isNotEmpty()) {
            imports.forEach {
                appendReproducibleNewLine("import $it")
            }
            appendReproducibleNewLine()
        }
        accessors.forEach {
            appendReproducibleNewLine(it)
            appendReproducibleNewLine()
        }
    }
}


internal
fun fileHeaderWithImportsFor(accessorsPackage: String = kotlinDslPackageName) = """
${fileHeaderFor(accessorsPackage)}

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
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderConvertible
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider

import org.gradle.kotlin.dsl.*
import org.gradle.kotlin.dsl.accessors.runtime.*

"""


/**
 * Location of the discontinued project schema snapshot, relative to the root project.
 */
internal
const val projectSchemaResourcePath =
    "gradle/project-schema.json"


internal
const val projectSchemaResourceDiscontinuedWarning =
    "Support for $projectSchemaResourcePath was removed in Gradle 5.0. The file is no longer used and it can be safely deleted."


fun Project.warnAboutDiscontinuedJsonProjectSchema() {
    if (file(projectSchemaResourcePath).isFile) {
        logger.warn(projectSchemaResourceDiscontinuedWarning)
    }
}
