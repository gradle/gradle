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
import org.gradle.api.internal.catalog.ExternalModuleDependencyFactory
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.plugins.ExtensionsSchema
import org.gradle.api.reflect.TypeOf
import org.gradle.initialization.DependenciesAccessors.IN_PLUGINS_BLOCK_FACTORIES_SUFFIX
import org.gradle.internal.execution.ExecutionOutput
import org.gradle.internal.execution.ExecutionRequest
import org.gradle.internal.execution.InputFingerprinter
import org.gradle.internal.execution.WorkResult
import org.gradle.internal.hash.HashCode
import org.gradle.kotlin.dsl.*
import org.gradle.kotlin.dsl.cache.KotlinDslWorkspaceProvider
import org.gradle.kotlin.dsl.concurrent.IO
import org.gradle.kotlin.dsl.concurrent.withAsynchronousIO
import org.gradle.kotlin.dsl.concurrent.writeFile
import org.gradle.kotlin.dsl.internal.sharedruntime.codegen.fileHeader
import org.gradle.kotlin.dsl.internal.sharedruntime.codegen.kotlinDslPackagePath
import org.gradle.kotlin.dsl.internal.sharedruntime.support.appendReproducibleNewLine
import org.gradle.kotlin.dsl.provider.kotlinScriptClassPathProviderOf
import org.gradle.kotlin.dsl.support.PluginDependenciesSpecScopeInternal
import org.gradle.kotlin.dsl.support.ScriptHandlerScopeInternal
import org.gradle.kotlin.dsl.support.bytecode.ALOAD
import org.gradle.kotlin.dsl.support.bytecode.ARETURN
import org.gradle.kotlin.dsl.support.bytecode.CHECKCAST
import org.gradle.kotlin.dsl.support.bytecode.INVOKEVIRTUAL
import org.gradle.kotlin.dsl.support.bytecode.InternalName
import org.gradle.kotlin.dsl.support.bytecode.LDC
import org.gradle.kotlin.dsl.support.bytecode.addKmProperty
import org.gradle.kotlin.dsl.support.bytecode.internalName
import org.gradle.kotlin.dsl.support.bytecode.jvmGetterSignatureFor
import org.gradle.kotlin.dsl.support.bytecode.moduleFileFor
import org.gradle.kotlin.dsl.support.bytecode.moduleMetadataBytesFor
import org.gradle.kotlin.dsl.support.bytecode.publicKotlinClass
import org.gradle.kotlin.dsl.support.bytecode.publicStaticMethod
import org.gradle.kotlin.dsl.support.bytecode.writeFileFacadeClassHeader
import org.gradle.kotlin.dsl.support.useToRun
import org.jetbrains.org.objectweb.asm.ClassWriter
import java.io.BufferedWriter
import java.io.File
import kotlin.reflect.KClass


internal
class GenerateVersionCatalogAccessors(
    private val versionCatalogExtensionSchemas: List<ExtensionsSchema.ExtensionSchema>,
    rootProject: Project,
    buildSrcClassLoaderScope: ClassLoaderScope,
    classLoaderHash: HashCode,
    fileCollectionFactory: FileCollectionFactory,
    inputFingerprinter: InputFingerprinter,
    workspaceProvider: KotlinDslWorkspaceProvider,
) : AbstractStage1BlockAccessorsUnitOfWork(
    rootProject, buildSrcClassLoaderScope, classLoaderHash, fileCollectionFactory, inputFingerprinter, workspaceProvider
) {

    override fun getDisplayName(): String = "Kotlin DSL version catalog plugin accessors for classpath '$classLoaderHash'"

    override val identitySuffix: String = "VC"

    override fun execute(executionRequest: ExecutionRequest): ExecutionOutput {
        val workspace = executionRequest.workspace
        kotlinScriptClassPathProviderOf(rootProject).run {
            withAsynchronousIO(rootProject) {
                buildVersionCatalogAccessorsFor(
                    versionCatalogs = versionCatalogAccessorFrom(versionCatalogExtensionSchemas),
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
data class VersionCatalogAccessor(
    val name: String,
    val publicType: TypeOf<*>,
    val buildscriptExtension: ExtensionSpec,
    val pluginsExtension: ExtensionSpec,
)


private
fun versionCatalogAccessorFrom(versionCatalogExtensionSchemas: List<ExtensionsSchema.ExtensionSchema>): List<VersionCatalogAccessor> =
    versionCatalogExtensionSchemas.map {
        VersionCatalogAccessor(
            it.name,
            it.publicType,
            ExtensionSpec(it.name, scriptHandlerScopeTypeSpec, TypeSpec(it.publicType.simpleName, it.publicType.concreteClass.internalName)),
            ExtensionSpec(it.name, pluginDependenciesSpecScopeTypeSpec, TypeSpec(pluginsBlockFactorySourceNameFor(it.publicType), pluginsBlockFactoryInternalNameFor(it.publicType))),
        )
    }


private
fun pluginsBlockFactorySourceNameFor(publicType: TypeOf<*>): String =
    "${publicType.simpleName}$IN_PLUGINS_BLOCK_FACTORIES_SUFFIX"


private
fun pluginsBlockFactoryInternalNameFor(publicType: TypeOf<*>): InternalName =
    InternalName.from("${publicType.concreteClass.internalName.value}$IN_PLUGINS_BLOCK_FACTORIES_SUFFIX")


internal
fun IO.buildVersionCatalogAccessorsFor(
    versionCatalogs: List<VersionCatalogAccessor>,
    srcDir: File,
    binDir: File
) {
    makeAccessorOutputDirs(srcDir, binDir, kotlinDslPackagePath)

    val baseFileName = "$kotlinDslPackagePath/VersionCatalogAccessors"
    val sourceFile = srcDir.resolve("$baseFileName.kt")

    writeVersionCatalogAccessorsSourceCodeTo(sourceFile, versionCatalogs)

    val fileFacadeClassName = InternalName(baseFileName + "Kt")
    val moduleName = "kotlin-dsl-version-catalog-accessors"
    val moduleMetadata = moduleMetadataBytesFor(listOf(fileFacadeClassName))
    writeFile(
        moduleFileFor(binDir, moduleName),
        moduleMetadata
    )

    val buildscriptAccessorSignatures = ArrayList<Pair<VersionCatalogAccessor, JvmMethodSignature>>(versionCatalogs.size)
    val pluginsProperties = ArrayList<Pair<VersionCatalogAccessor, JvmMethodSignature>>(versionCatalogs.size)
    val header = writeFileFacadeClassHeader(moduleName) {
        versionCatalogs.forEach { catalog ->
            val buildscriptAccessorSignature = jvmGetterSignatureFor(catalog.buildscriptExtension)
            addKmProperty(catalog.buildscriptExtension, buildscriptAccessorSignature)
            buildscriptAccessorSignatures.add(catalog to buildscriptAccessorSignature)

            val pluginsAccessorSignature = jvmGetterSignatureFor(catalog.pluginsExtension)
            addKmProperty(catalog.pluginsExtension, pluginsAccessorSignature)
            pluginsProperties.add(catalog to pluginsAccessorSignature)
        }
    }

    val classBytes = publicKotlinClass(fileFacadeClassName, header) {
        buildscriptAccessorSignatures.forEach { (versionCatalogAccessor, signature) ->
            emitVersionCatalogAccessorMethodFor(
                versionCatalogAccessor.buildscriptExtension,
                signature,
                scriptHandlerScopeInternalInternalName,
                scriptHandlerScopeInternalVersionCatalogExtensionMethodName,
                scriptHandlerScopeInternalVersionCatalogExtensionMethodDesc,
            )
        }
        pluginsProperties.forEach { (versionCatalogAccessor, signature) ->
            emitVersionCatalogAccessorMethodFor(
                versionCatalogAccessor.pluginsExtension,
                signature,
                pluginDependenciesSpecScopeInternalInternalName,
                pluginDependenciesSpecScopeInternalVersionCatalogForPluginsBlockMethodName,
                pluginDependenciesSpecScopeInternalVersionCatalogForPluginsBlockMethodDesc,
            )
        }
    }

    writeClassFileTo(binDir, fileFacadeClassName, classBytes)
}


private
fun IO.writeVersionCatalogAccessorsSourceCodeTo(
    sourceFile: File,
    versionCatalogs: List<VersionCatalogAccessor>,
    format: AccessorFormat = AccessorFormats.default,
    header: String = fileHeader,
) = io {
    sourceFile.bufferedWriter().useToRun {
        appendReproducibleNewLine(header)
        appendSourceCodeForVersionCatalogAccessors(versionCatalogs, format)
    }
}


private
fun BufferedWriter.appendSourceCodeForVersionCatalogAccessors(
    versionCatalogs: List<VersionCatalogAccessor>,
    format: AccessorFormat
) {
    appendReproducibleNewLine(
        """
        import ${ScriptHandlerScopeInternal::class.qualifiedName}
        import ${PluginDependenciesSpecScopeInternal::class.qualifiedName}
        """.trimIndent()
    )

    versionCatalogs.flatMap {
        listOf(it.buildscriptExtension.returnType, it.buildscriptExtension.receiverType, it.pluginsExtension.returnType, it.pluginsExtension.receiverType)
    }.forEach {
        appendReproducibleNewLine("import ${it.sourceName}")
    }

    fun appendCatalogExtension(extSpec: ExtensionSpec, receiverInternalType: KClass<*>, internalMethodName: String) {
        write("\n\n")
        appendReproducibleNewLine(
            format("""
                /**
                 * The `${extSpec.name}` version catalog.
                 */
                val ${extSpec.receiverType.sourceName}.`${extSpec.name}`: ${extSpec.returnType.sourceName}
                    get() = (this as ${receiverInternalType.simpleName}).$internalMethodName("${extSpec.name}") as ${extSpec.returnType.sourceName}
            """)
        )
    }

    versionCatalogs.forEach { catalog ->
        appendCatalogExtension(catalog.buildscriptExtension, ScriptHandlerScopeInternal::class, scriptHandlerScopeInternalVersionCatalogExtensionMethodName)
        appendCatalogExtension(catalog.pluginsExtension, PluginDependenciesSpecScopeInternal::class, pluginDependenciesSpecScopeInternalVersionCatalogForPluginsBlockMethodName)
    }
}


private
fun ClassWriter.emitVersionCatalogAccessorMethodFor(
    extensionSpec: ExtensionSpec,
    signature: JvmMethodSignature,
    receiverInternalTypeInternalName: InternalName,
    internalMethodName: String,
    receiverVersionCatalogExtensionMethodDesc: String,
) {
    publicStaticMethod(signature) {
        ALOAD(0)
        CHECKCAST(receiverInternalTypeInternalName)
        LDC(extensionSpec.name)
        INVOKEVIRTUAL(receiverInternalTypeInternalName, internalMethodName, receiverVersionCatalogExtensionMethodDesc)
        CHECKCAST(extensionSpec.returnType.internalName)
        ARETURN()
    }
}


private
val scriptHandlerScopeTypeSpec = TypeSpec("ScriptHandlerScope", ScriptHandlerScope::class.internalName)


private
val scriptHandlerScopeInternalInternalName = ScriptHandlerScopeInternal::class.internalName


private
const val scriptHandlerScopeInternalVersionCatalogExtensionMethodName = "versionCatalogExtension"


private
val scriptHandlerScopeInternalVersionCatalogExtensionMethodDesc = "(Ljava/lang/String;)L${ExternalModuleDependencyFactory::class.internalName};"


private
val pluginDependenciesSpecScopeInternalInternalName = PluginDependenciesSpecScopeInternal::class.internalName


private
val pluginDependenciesSpecScopeTypeSpec = TypeSpec("PluginDependenciesSpecScope", PluginDependenciesSpecScope::class.internalName)


private
const val pluginDependenciesSpecScopeInternalVersionCatalogForPluginsBlockMethodName = "versionCatalogForPluginsBlock"


private
val pluginDependenciesSpecScopeInternalVersionCatalogForPluginsBlockMethodDesc = "(Ljava/lang/String;)L${ExternalModuleDependencyFactory::class.internalName};"
