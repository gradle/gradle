/*
 * Copyright 2018 the original author or authors.
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

import kotlinx.metadata.Flag
import kotlinx.metadata.KmTypeVisitor
import kotlinx.metadata.flagsOf
import kotlinx.metadata.jvm.JvmMethodSignature
import org.gradle.api.Project
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.internal.execution.ExecutionEngine
import org.gradle.internal.execution.UnitOfWork
import org.gradle.internal.execution.fingerprint.InputFingerprinter
import org.gradle.internal.execution.fingerprint.InputFingerprinter.InputVisitor
import org.gradle.internal.file.TreeType.DIRECTORY
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint
import org.gradle.internal.hash.ClassLoaderHierarchyHasher
import org.gradle.internal.hash.HashCode
import org.gradle.internal.snapshot.ValueSnapshot
import org.gradle.kotlin.dsl.cache.KotlinDslWorkspaceProvider
import org.gradle.kotlin.dsl.codegen.fileHeader
import org.gradle.kotlin.dsl.codegen.fileHeaderFor
import org.gradle.kotlin.dsl.codegen.kotlinDslPackagePath
import org.gradle.kotlin.dsl.codegen.pluginEntriesFrom
import org.gradle.kotlin.dsl.codegen.sourceNameOfBinaryName
import org.gradle.kotlin.dsl.concurrent.IO
import org.gradle.kotlin.dsl.concurrent.withAsynchronousIO
import org.gradle.kotlin.dsl.concurrent.withSynchronousIO
import org.gradle.kotlin.dsl.concurrent.writeFile
import org.gradle.kotlin.dsl.provider.kotlinScriptClassPathProviderOf
import org.gradle.kotlin.dsl.support.appendReproducibleNewLine
import org.gradle.kotlin.dsl.support.bytecode.ALOAD
import org.gradle.kotlin.dsl.support.bytecode.ARETURN
import org.gradle.kotlin.dsl.support.bytecode.DUP
import org.gradle.kotlin.dsl.support.bytecode.GETFIELD
import org.gradle.kotlin.dsl.support.bytecode.INVOKEINTERFACE
import org.gradle.kotlin.dsl.support.bytecode.INVOKESPECIAL
import org.gradle.kotlin.dsl.support.bytecode.InternalName
import org.gradle.kotlin.dsl.support.bytecode.InternalNameOf
import org.gradle.kotlin.dsl.support.bytecode.KmTypeBuilder
import org.gradle.kotlin.dsl.support.bytecode.LDC
import org.gradle.kotlin.dsl.support.bytecode.NEW
import org.gradle.kotlin.dsl.support.bytecode.PUTFIELD
import org.gradle.kotlin.dsl.support.bytecode.RETURN
import org.gradle.kotlin.dsl.support.bytecode.internalName
import org.gradle.kotlin.dsl.support.bytecode.jvmGetterSignatureFor
import org.gradle.kotlin.dsl.support.bytecode.moduleFileFor
import org.gradle.kotlin.dsl.support.bytecode.moduleMetadataBytesFor
import org.gradle.kotlin.dsl.support.bytecode.publicClass
import org.gradle.kotlin.dsl.support.bytecode.publicKotlinClass
import org.gradle.kotlin.dsl.support.bytecode.publicMethod
import org.gradle.kotlin.dsl.support.bytecode.publicStaticMethod
import org.gradle.kotlin.dsl.support.bytecode.writeFileFacadeClassHeader
import org.gradle.kotlin.dsl.support.bytecode.writePropertyOf
import org.gradle.kotlin.dsl.support.useToRun
import org.gradle.plugin.use.PluginDependenciesSpec
import org.gradle.plugin.use.PluginDependencySpec
import org.jetbrains.org.objectweb.asm.ClassWriter
import org.jetbrains.org.objectweb.asm.MethodVisitor
import java.io.BufferedWriter
import java.io.File
import javax.inject.Inject


/**
 * Produces an [AccessorsClassPath] with type-safe accessors for all plugin ids found in the
 * `buildSrc` classpath of the given [project].
 *
 * The accessors provide content-assist for plugin ids and quick navigation to the plugin source code.
 */
class PluginAccessorClassPathGenerator @Inject constructor(
    private val classLoaderHierarchyHasher: ClassLoaderHierarchyHasher,
    private val fileCollectionFactory: FileCollectionFactory,
    private val executionEngine: ExecutionEngine,
    private val inputFingerprinter: InputFingerprinter,
    private val workspaceProvider: KotlinDslWorkspaceProvider
) {
    fun pluginSpecBuildersClassPath(project: Project): AccessorsClassPath = project.rootProject.let { rootProject ->

        rootProject.getOrCreateProperty("gradleKotlinDsl.pluginAccessorsClassPath") {
            val buildSrcClassLoaderScope = baseClassLoaderScopeOf(rootProject)
            val classLoaderHash = requireNotNull(classLoaderHierarchyHasher.getClassLoaderHash(buildSrcClassLoaderScope.exportClassLoader))
            val work = GeneratePluginAccessors(
                rootProject,
                buildSrcClassLoaderScope,
                classLoaderHash,
                fileCollectionFactory,
                inputFingerprinter,
                workspaceProvider
            )
            val result = executionEngine.createRequest(work).execute()
            result.executionResult.get().output as AccessorsClassPath
        }
    }
}


class GeneratePluginAccessors(
    private val rootProject: Project,
    private val buildSrcClassLoaderScope: ClassLoaderScope,
    private val classLoaderHash: HashCode,
    private val fileCollectionFactory: FileCollectionFactory,
    private val inputFingerprinter: InputFingerprinter,
    private val workspaceProvider: KotlinDslWorkspaceProvider
) : UnitOfWork {

    companion object {
        const val BUILD_SRC_CLASSLOADER_INPUT_PROPERTY = "buildSrcClassLoader"
        const val SOURCES_OUTPUT_PROPERTY = "sources"
        const val CLASSES_OUTPUT_PROPERTY = "classes"
    }

    override fun execute(executionRequest: UnitOfWork.ExecutionRequest): UnitOfWork.WorkOutput {
        val workspace = executionRequest.workspace
        kotlinScriptClassPathProviderOf(rootProject).run {
            withAsynchronousIO(rootProject) {
                buildPluginAccessorsFor(
                    pluginDescriptorsClassPath = exportClassPathFromHierarchyOf(buildSrcClassLoaderScope),
                    srcDir = getSourcesOutputDir(workspace),
                    binDir = getClassesOutputDir(workspace)
                )
            }
        }
        return object : UnitOfWork.WorkOutput {
            override fun getDidWork() = UnitOfWork.WorkResult.DID_WORK

            override fun getOutput() = loadRestoredOutput(workspace)
        }
    }

    override fun loadRestoredOutput(workspace: File) = AccessorsClassPath(
        DefaultClassPath.of(getClassesOutputDir(workspace)),
        DefaultClassPath.of(getSourcesOutputDir(workspace))
    )

    override fun identify(identityInputs: MutableMap<String, ValueSnapshot>, identityFileInputs: MutableMap<String, CurrentFileCollectionFingerprint>) = UnitOfWork.Identity { classLoaderHash.toString() }

    override fun getWorkspaceProvider() = workspaceProvider.accessors

    override fun getInputFingerprinter() = inputFingerprinter

    override fun getDisplayName(): String = "Kotlin DSL plugin accessors for classpath '$classLoaderHash'"

    override fun visitIdentityInputs(visitor: InputVisitor) {
        visitor.visitInputProperty(BUILD_SRC_CLASSLOADER_INPUT_PROPERTY) { classLoaderHash }
    }

    override fun visitOutputs(workspace: File, visitor: UnitOfWork.OutputVisitor) {
        val sourcesOutputDir = getSourcesOutputDir(workspace)
        val classesOutputDir = getClassesOutputDir(workspace)
        visitor.visitOutputProperty(SOURCES_OUTPUT_PROPERTY, DIRECTORY, sourcesOutputDir, fileCollectionFactory.fixed(sourcesOutputDir))
        visitor.visitOutputProperty(CLASSES_OUTPUT_PROPERTY, DIRECTORY, classesOutputDir, fileCollectionFactory.fixed(classesOutputDir))
    }
}


private
fun getClassesOutputDir(workspace: File) = File(workspace, "classes")


private
fun getSourcesOutputDir(workspace: File): File = File(workspace, "sources")


fun writeSourceCodeForPluginSpecBuildersFor(
    pluginDescriptorsClassPath: ClassPath,
    sourceFile: File,
    packageName: String
) {
    withSynchronousIO {
        writePluginAccessorsSourceCodeTo(
            sourceFile,
            pluginAccessorsFor(pluginDescriptorsClassPath),
            format = AccessorFormats.internal,
            header = fileHeaderFor(packageName)
        )
    }
}


private
fun pluginAccessorsFor(pluginDescriptorsClassPath: ClassPath): List<PluginAccessor> =
    pluginAccessorsFor(pluginTreesFrom(pluginDescriptorsClassPath)).toList()


internal
fun IO.buildPluginAccessorsFor(
    pluginDescriptorsClassPath: ClassPath,
    srcDir: File,
    binDir: File
) {
    makeAccessorOutputDirs(srcDir, binDir, kotlinDslPackagePath)

    val pluginTrees = pluginTreesFrom(pluginDescriptorsClassPath)

    val baseFileName = "$kotlinDslPackagePath/PluginAccessors"
    val sourceFile = srcDir.resolve("$baseFileName.kt")

    val accessorList = pluginAccessorsFor(pluginTrees).toList()
    writePluginAccessorsSourceCodeTo(sourceFile, accessorList)

    val fileFacadeClassName = InternalName(baseFileName + "Kt")
    val moduleName = "kotlin-dsl-plugin-spec-builders"
    val moduleMetadata = moduleMetadataBytesFor(listOf(fileFacadeClassName))
    writeFile(
        moduleFileFor(binDir, moduleName),
        moduleMetadata
    )

    val properties = ArrayList<Pair<PluginAccessor, JvmMethodSignature>>(accessorList.size)
    val header = writeFileFacadeClassHeader(moduleName) {
        accessorList.forEach { accessor ->

            if (accessor is PluginAccessor.ForGroup) {
                val (internalClassName, classBytes) = emitClassForGroup(accessor)
                writeClassFileTo(binDir, internalClassName, classBytes)
            }

            val extensionSpec = accessor.extension
            val propertyName = extensionSpec.name
            val receiverType = extensionSpec.receiverType
            val returnType = extensionSpec.returnType
            val getterSignature = jvmGetterSignatureFor(propertyName, "(L${receiverType.internalName};)L${returnType.internalName};")
            writePropertyOf(
                receiverType = receiverType.builder,
                returnType = returnType.builder,
                propertyName = propertyName,
                getterSignature = getterSignature,
                getterFlags = nonInlineGetterFlags
            )
            properties.add(accessor to getterSignature)
        }
    }

    val classBytes = publicKotlinClass(fileFacadeClassName, header) {
        properties.forEach { (accessor, signature) ->
            emitAccessorMethodFor(accessor, signature)
        }
    }

    writeClassFileTo(binDir, fileFacadeClassName, classBytes)
}


internal
fun pluginTreesFrom(pluginDescriptorsClassPath: ClassPath): Map<String, PluginTree> =
    PluginTree.of(pluginSpecsFrom(pluginDescriptorsClassPath))


private
fun ClassWriter.emitAccessorMethodFor(accessor: PluginAccessor, signature: JvmMethodSignature) {
    val extension = accessor.extension
    val receiverType = extension.receiverType
    publicStaticMethod(signature) {
        when (accessor) {
            is PluginAccessor.ForGroup -> {
                val returnType = extension.returnType
                NEW(returnType.internalName)
                DUP()
                GETPLUGINS(receiverType)
                INVOKESPECIAL(returnType.internalName, "<init>", groupTypeConstructorSignature)
                ARETURN()
            }
            is PluginAccessor.ForPlugin -> {
                GETPLUGINS(receiverType)
                LDC(accessor.id)
                INVOKEINTERFACE(pluginDependenciesSpecInternalName, "id", pluginDependenciesSpecIdMethodDesc)
                ARETURN()
            }
        }
    }
}


private
fun IO.writePluginAccessorsSourceCodeTo(
    sourceFile: File,
    accessors: List<PluginAccessor>,
    format: AccessorFormat = AccessorFormats.default,
    header: String = fileHeader
) = io {
    sourceFile.bufferedWriter().useToRun {
        appendReproducibleNewLine(header)
        appendSourceCodeForPluginAccessors(accessors, format)
    }
}


private
fun BufferedWriter.appendSourceCodeForPluginAccessors(
    accessors: List<PluginAccessor>,
    format: AccessorFormat
) {

    appendReproducibleNewLine(
        """
        import ${PluginDependenciesSpec::class.qualifiedName}
        import ${PluginDependencySpec::class.qualifiedName}
        """.trimIndent()
    )

    defaultPackageTypesIn(accessors).forEach {
        appendReproducibleNewLine("import $it")
    }

    accessors.runEach {

        // Keep accessors separated by an empty line
        write("\n\n")

        val extendedType = extension.receiverType.sourceName
        val pluginsRef = pluginDependenciesSpecOf(extendedType)
        when (this) {
            is PluginAccessor.ForPlugin -> {
                appendReproducibleNewLine(
                    format(
                        """
                        /**
                         * The `$id` plugin implemented by [${sourceNameOfBinaryName(implementationClass)}].
                         */
                        val `$extendedType`.`${extension.name}`: PluginDependencySpec
                            get() = $pluginsRef.id("$id")
                        """
                    )
                )
            }
            is PluginAccessor.ForGroup -> {
                val groupType = extension.returnType.sourceName
                appendReproducibleNewLine(
                    format(
                        """
                        /**
                         * The `$id` plugin group.
                         */
                        @org.gradle.api.Generated
                        class `$groupType`(internal val plugins: PluginDependenciesSpec)


                        /**
                         * Plugin ids starting with `$id`.
                         */
                        val `$extendedType`.`${extension.name}`: `$groupType`
                            get() = `$groupType`($pluginsRef)
                        """
                    )
                )
            }
        }
    }
}


private
fun defaultPackageTypesIn(pluginAccessors: List<PluginAccessor>) =
    defaultPackageTypesIn(
        pluginImplementationClassesExposedBy(pluginAccessors)
    )


private
fun pluginImplementationClassesExposedBy(pluginAccessors: List<PluginAccessor>) =
    pluginAccessors
        .filterIsInstance<PluginAccessor.ForPlugin>()
        .map { it.implementationClass }


private
const val pluginsFieldName = "plugins"


private
fun pluginDependenciesSpecOf(extendedType: String): String = when (extendedType) {
    "PluginDependenciesSpec" -> "this"
    else -> pluginsFieldName
}


private
inline fun <T> Iterable<T>.runEach(f: T.() -> Unit) {
    forEach { it.run(f) }
}


internal
data class TypeSpec(val sourceName: String, val internalName: InternalName) {

    val builder: KmTypeBuilder
        get() = { visitClass(internalName) }
}


internal
fun KmTypeVisitor.visitClass(internalName: InternalName) {
    visitClass(internalName.value)
}


private
val pluginDependencySpecInternalName = PluginDependencySpec::class.internalName


private
val pluginDependenciesSpecInternalName = PluginDependenciesSpec::class.internalName


internal
val pluginDependenciesSpecTypeSpec = TypeSpec("PluginDependenciesSpec", pluginDependenciesSpecInternalName)


internal
val pluginDependencySpecTypeSpec = TypeSpec("PluginDependencySpec", pluginDependencySpecInternalName)


private
val pluginDependenciesSpecTypeDesc = "L$pluginDependenciesSpecInternalName;"


private
val groupTypeConstructorSignature = "($pluginDependenciesSpecTypeDesc)V"


private
val pluginDependenciesSpecIdMethodDesc = "(Ljava/lang/String;)L$pluginDependencySpecInternalName;"


internal
fun pluginAccessorsFor(pluginTrees: Map<String, PluginTree>, extendedType: TypeSpec = pluginDependenciesSpecTypeSpec): Sequence<PluginAccessor> = sequence {

    for ((extensionName, pluginTree) in pluginTrees) {
        when (pluginTree) {
            is PluginTree.PluginGroup -> {
                val groupId = pluginTree.path.joinToString(".")
                val groupType = pluginGroupTypeName(pluginTree.path)
                val groupTypeSpec = typeSpecForPluginGroupType(groupType)
                yield(
                    PluginAccessor.ForGroup(
                        groupId,
                        ExtensionSpec(extensionName, extendedType, groupTypeSpec)
                    )
                )
                yieldAll(pluginAccessorsFor(pluginTree.plugins, groupTypeSpec))
            }
            is PluginTree.PluginSpec -> {
                yield(
                    PluginAccessor.ForPlugin(
                        pluginTree.id,
                        pluginTree.implementationClass,
                        ExtensionSpec(extensionName, extendedType, pluginDependencySpecTypeSpec)
                    )
                )
            }
        }
    }
}


internal
fun typeSpecForPluginGroupType(groupType: String) =
    TypeSpec(groupType, InternalName("$kotlinDslPackagePath/$groupType"))


internal
sealed class PluginAccessor {

    abstract val extension: ExtensionSpec

    data class ForPlugin(
        val id: String,
        val implementationClass: String,
        override val extension: ExtensionSpec
    ) : PluginAccessor()

    data class ForGroup(
        val id: String,
        override val extension: ExtensionSpec
    ) : PluginAccessor()
}


internal
data class ExtensionSpec(
    val name: String,
    val receiverType: TypeSpec,
    val returnType: TypeSpec
)


private
fun pluginSpecsFrom(pluginDescriptorsClassPath: ClassPath): Sequence<PluginTree.PluginSpec> =
    pluginDescriptorsClassPath
        .asFiles
        .asSequence()
        .filter { it.isFile && it.extension.equals("jar", true) }
        .flatMap { pluginEntriesFrom(it).asSequence() }
        .map { PluginTree.PluginSpec(it.pluginId, it.implementationClass) }


private
fun pluginGroupTypeName(path: List<String>) =
    path.joinToString(separator = "") { it.capitalize() } + "PluginGroup"


private
fun IO.writeClassFileTo(binDir: File, internalClassName: InternalName, classBytes: ByteArray) {
    val classFile = binDir.resolve("$internalClassName.class")
    writeFile(classFile, classBytes)
}


private
val nonInlineGetterFlags = flagsOf(Flag.IS_PUBLIC, Flag.PropertyAccessor.IS_NOT_DEFAULT)


private
fun MethodVisitor.GETPLUGINS(receiverType: TypeSpec) {
    ALOAD(0)
    if (receiverType !== pluginDependenciesSpecTypeSpec) {
        GETFIELD(receiverType.internalName, pluginsFieldName, pluginDependenciesSpecTypeDesc)
    }
}


private
fun emitClassForGroup(group: PluginAccessor.ForGroup): Pair<InternalName, ByteArray> = group.run {

    val className = extension.returnType.internalName
    val classBytes = publicClass(className) {
        packagePrivateField(pluginsFieldName, pluginDependenciesSpecTypeDesc)
        publicMethod("<init>", groupTypeConstructorSignature) {
            ALOAD(0)
            INVOKESPECIAL(InternalNameOf.javaLangObject, "<init>", "()V")
            ALOAD(0)
            ALOAD(1)
            PUTFIELD(className, pluginsFieldName, pluginDependenciesSpecTypeDesc)
            RETURN()
        }
    }

    className to classBytes
}


private
fun ClassWriter.packagePrivateField(name: String, desc: String) {
    visitField(0, name, desc, null, null).run {
        visitEnd()
    }
}


private
fun baseClassLoaderScopeOf(rootProject: Project) =
    (rootProject as ProjectInternal).baseClassLoaderScope
