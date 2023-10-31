/*
 * Copyright 2023 the original author or authors.
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

import kotlinx.metadata.jvm.JvmMethodSignature
import org.gradle.api.Project
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.execution.ExecutionOutput
import org.gradle.internal.execution.ExecutionRequest
import org.gradle.internal.execution.InputFingerprinter
import org.gradle.internal.execution.WorkResult
import org.gradle.internal.hash.HashCode
import org.gradle.kotlin.dsl.cache.KotlinDslWorkspaceProvider
import org.gradle.kotlin.dsl.concurrent.IO
import org.gradle.kotlin.dsl.concurrent.withAsynchronousIO
import org.gradle.kotlin.dsl.concurrent.withSynchronousIO
import org.gradle.kotlin.dsl.concurrent.writeFile
import org.gradle.kotlin.dsl.internal.sharedruntime.codegen.fileHeader
import org.gradle.kotlin.dsl.internal.sharedruntime.codegen.fileHeaderFor
import org.gradle.kotlin.dsl.internal.sharedruntime.codegen.kotlinDslPackagePath
import org.gradle.kotlin.dsl.internal.sharedruntime.codegen.pluginEntriesFrom
import org.gradle.kotlin.dsl.internal.sharedruntime.codegen.sourceNameOfBinaryName
import org.gradle.kotlin.dsl.internal.sharedruntime.support.appendReproducibleNewLine
import org.gradle.kotlin.dsl.provider.kotlinScriptClassPathProviderOf
import org.gradle.kotlin.dsl.support.bytecode.ALOAD
import org.gradle.kotlin.dsl.support.bytecode.ARETURN
import org.gradle.kotlin.dsl.support.bytecode.DUP
import org.gradle.kotlin.dsl.support.bytecode.GETFIELD
import org.gradle.kotlin.dsl.support.bytecode.INVOKEINTERFACE
import org.gradle.kotlin.dsl.support.bytecode.INVOKESPECIAL
import org.gradle.kotlin.dsl.support.bytecode.InternalName
import org.gradle.kotlin.dsl.support.bytecode.InternalNameOf
import org.gradle.kotlin.dsl.support.bytecode.LDC
import org.gradle.kotlin.dsl.support.bytecode.NEW
import org.gradle.kotlin.dsl.support.bytecode.PUTFIELD
import org.gradle.kotlin.dsl.support.bytecode.RETURN
import org.gradle.kotlin.dsl.support.bytecode.addKmProperty
import org.gradle.kotlin.dsl.support.bytecode.internalName
import org.gradle.kotlin.dsl.support.bytecode.jvmGetterSignatureFor
import org.gradle.kotlin.dsl.support.bytecode.moduleFileFor
import org.gradle.kotlin.dsl.support.bytecode.moduleMetadataBytesFor
import org.gradle.kotlin.dsl.support.bytecode.publicClass
import org.gradle.kotlin.dsl.support.bytecode.publicKotlinClass
import org.gradle.kotlin.dsl.support.bytecode.publicMethod
import org.gradle.kotlin.dsl.support.bytecode.publicStaticMethod
import org.gradle.kotlin.dsl.support.bytecode.writeFileFacadeClassHeader
import org.gradle.kotlin.dsl.support.uppercaseFirstChar
import org.gradle.kotlin.dsl.support.useToRun
import org.gradle.plugin.use.PluginDependenciesSpec
import org.gradle.plugin.use.PluginDependencySpec
import org.jetbrains.org.objectweb.asm.ClassWriter
import org.jetbrains.org.objectweb.asm.MethodVisitor
import java.io.BufferedWriter
import java.io.File


/**
 * Writes the source code of accessors to plugin spec builders.
 *
 * This is public in order to be usable by precompiled script plugins support.
 */
fun writeSourceCodeForPluginSpecBuildersFor(
    pluginDescriptorsClassPath: ClassPath,
    sourceFile: File,
    packageName: String
) {
    withSynchronousIO {
        writePluginDependencySpecAccessorsSourceCodeTo(
            sourceFile,
            pluginDependencySpecAccessorsFor(pluginDescriptorsClassPath),
            format = AccessorFormats.internal,
            header = fileHeaderFor(packageName)
        )
    }
}


internal
class GeneratePluginSpecBuilderAccessors(
    rootProject: Project,
    buildSrcClassLoaderScope: ClassLoaderScope,
    classLoaderHash: HashCode,
    fileCollectionFactory: FileCollectionFactory,
    inputFingerprinter: InputFingerprinter,
    workspaceProvider: KotlinDslWorkspaceProvider,
) : AbstractStage1BlockAccessorsUnitOfWork(
    rootProject, buildSrcClassLoaderScope, classLoaderHash, fileCollectionFactory, inputFingerprinter, workspaceProvider
) {

    override fun getDisplayName(): String = "Kotlin DSL plugin specs accessors for classpath '$classLoaderHash'"

    override val identitySuffix: String = "PS"

    override fun execute(executionRequest: ExecutionRequest): ExecutionOutput {
        val workspace = executionRequest.workspace
        kotlinScriptClassPathProviderOf(rootProject).run {
            withAsynchronousIO(rootProject) {
                buildPluginDependencySpecAccessorsFor(
                    pluginDescriptorsClassPath = exportClassPathFromHierarchyOf(buildSrcClassLoaderScope),
                    srcDir = getSourcesOutputDir(workspace),
                    binDir = getClassesOutputDir(workspace),
                )
            }
        }
        return object : ExecutionOutput {
            override fun getDidWork() = WorkResult.DID_WORK

            override fun getOutput() = loadAlreadyProducedOutput(workspace)
        }
    }
}


internal
sealed class PluginDependencySpecAccessor {

    abstract val extension: ExtensionSpec

    data class ForPlugin(
        val id: String,
        val implementationClass: String,
        override val extension: ExtensionSpec
    ) : PluginDependencySpecAccessor()

    data class ForGroup(
        val id: String,
        override val extension: ExtensionSpec
    ) : PluginDependencySpecAccessor()
}


private
fun pluginDependencySpecAccessorsFor(pluginDescriptorsClassPath: ClassPath): List<PluginDependencySpecAccessor> =
    pluginDependencySpecAccessorsFor(pluginTreesFrom(pluginDescriptorsClassPath)).toList()


internal
fun IO.buildPluginDependencySpecAccessorsFor(
    pluginDescriptorsClassPath: ClassPath,
    srcDir: File,
    binDir: File
) {
    makeAccessorOutputDirs(srcDir, binDir, kotlinDslPackagePath)

    val pluginTrees = pluginTreesFrom(pluginDescriptorsClassPath)

    val baseFileName = "$kotlinDslPackagePath/PluginDependencySpecAccessors"
    val sourceFile = srcDir.resolve("$baseFileName.kt")

    val accessorList = pluginDependencySpecAccessorsFor(pluginTrees).toList()
    writePluginDependencySpecAccessorsSourceCodeTo(sourceFile, accessorList)

    val fileFacadeClassName = InternalName(baseFileName + "Kt")
    val moduleName = "kotlin-dsl-plugin-spec-accessors"
    val moduleMetadata = moduleMetadataBytesFor(listOf(fileFacadeClassName))
    writeFile(
        moduleFileFor(binDir, moduleName),
        moduleMetadata
    )

    val accessorSignatures = ArrayList<Pair<PluginDependencySpecAccessor, JvmMethodSignature>>(accessorList.size)
    val header = writeFileFacadeClassHeader(moduleName) {
        accessorList.forEach { accessor ->

            if (accessor is PluginDependencySpecAccessor.ForGroup) {
                val (internalClassName, classBytes) = emitClassForGroup(accessor)
                writeClassFileTo(binDir, internalClassName, classBytes)
            }

            val extensionSpec = accessor.extension
            val getterSignature = jvmGetterSignatureFor(extensionSpec)
            addKmProperty(extensionSpec, getterSignature)
            accessorSignatures.add(accessor to getterSignature)
        }
    }

    val classBytes = publicKotlinClass(fileFacadeClassName, header) {
        accessorSignatures.forEach { (accessor, signature) ->
            emitPluginDependencySpecAccessorMethodFor(accessor, signature)
        }
    }

    writeClassFileTo(binDir, fileFacadeClassName, classBytes)
}


private
fun ClassWriter.emitPluginDependencySpecAccessorMethodFor(accessor: PluginDependencySpecAccessor, signature: JvmMethodSignature) {
    val extension = accessor.extension
    val receiverType = extension.receiverType
    publicStaticMethod(signature) {
        when (accessor) {
            is PluginDependencySpecAccessor.ForGroup -> {
                val returnType = extension.returnType
                NEW(returnType.internalName)
                DUP()
                GETPLUGINS(receiverType)
                INVOKESPECIAL(returnType.internalName, "<init>", groupTypeConstructorSignature)
                ARETURN()
            }

            is PluginDependencySpecAccessor.ForPlugin -> {
                GETPLUGINS(receiverType)
                LDC(accessor.id)
                INVOKEINTERFACE(pluginDependenciesSpecInternalName, "id", pluginDependenciesSpecIdMethodDesc)
                ARETURN()
            }
        }
    }
}


private
fun IO.writePluginDependencySpecAccessorsSourceCodeTo(
    sourceFile: File,
    accessors: List<PluginDependencySpecAccessor>,
    format: AccessorFormat = AccessorFormats.default,
    header: String = fileHeader
) = io {
    sourceFile.bufferedWriter().useToRun {
        appendReproducibleNewLine(header)
        appendSourceCodeForPluginDependencySpecAccessors(accessors, format)
    }
}


private
fun BufferedWriter.appendSourceCodeForPluginDependencySpecAccessors(
    accessors: List<PluginDependencySpecAccessor>,
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
            is PluginDependencySpecAccessor.ForPlugin -> {
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

            is PluginDependencySpecAccessor.ForGroup -> {
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
fun defaultPackageTypesIn(pluginDependencySpecAccessors: List<PluginDependencySpecAccessor>) =
    defaultPackageTypesIn(
        pluginImplementationClassesExposedBy(pluginDependencySpecAccessors)
    )


private
fun pluginImplementationClassesExposedBy(pluginDependencySpecAccessors: List<PluginDependencySpecAccessor>) =
    pluginDependencySpecAccessors
        .filterIsInstance<PluginDependencySpecAccessor.ForPlugin>()
        .map { it.implementationClass }


private
fun pluginDependenciesSpecOf(extendedType: String): String = when (extendedType) {
    "PluginDependenciesSpec" -> "this"
    else -> pluginsFieldName
}


internal
fun pluginDependencySpecAccessorsFor(pluginTrees: Map<String, PluginTree>, extendedType: TypeSpec = pluginDependenciesSpecTypeSpec): Sequence<PluginDependencySpecAccessor> = sequence {

    for ((extensionName, pluginTree) in pluginTrees) {
        when (pluginTree) {
            is PluginTree.PluginGroup -> {
                val groupId = pluginTree.path.joinToString(".")
                val groupType = pluginGroupTypeName(pluginTree.path)
                val groupTypeSpec = typeSpecForPluginGroupType(groupType)
                yield(
                    PluginDependencySpecAccessor.ForGroup(
                        groupId,
                        ExtensionSpec(extensionName, extendedType, groupTypeSpec)
                    )
                )
                yieldAll(pluginDependencySpecAccessorsFor(pluginTree.plugins, groupTypeSpec))
            }

            is PluginTree.PluginSpec -> {
                yield(
                    PluginDependencySpecAccessor.ForPlugin(
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
fun pluginTreesFrom(pluginDescriptorsClassPath: ClassPath): Map<String, PluginTree> =
    PluginTree.of(pluginSpecsFrom(pluginDescriptorsClassPath))


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
    path.joinToString(separator = "") { it.uppercaseFirstChar() } + "PluginGroup"


private
fun MethodVisitor.GETPLUGINS(receiverType: TypeSpec) {
    ALOAD(0)
    if (receiverType !== pluginDependenciesSpecTypeSpec) {
        GETFIELD(receiverType.internalName, pluginsFieldName, pluginDependenciesSpecTypeDesc)
    }
}


private
fun emitClassForGroup(group: PluginDependencySpecAccessor.ForGroup): Pair<InternalName, ByteArray> = group.run {

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
const val pluginsFieldName = "plugins"


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


private
inline fun <T> Iterable<T>.runEach(f: T.() -> Unit) {
    forEach { it.run(f) }
}
