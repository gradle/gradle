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
import org.gradle.api.Project
import org.gradle.api.internal.catalog.ExternalModuleDependencyFactory
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.internal.execution.ExecutionEngine
import org.gradle.internal.execution.InputFingerprinter
import org.gradle.internal.execution.UnitOfWork
import org.gradle.internal.execution.UnitOfWork.InputVisitor
import org.gradle.internal.execution.UnitOfWork.OutputFileValueSupplier
import org.gradle.internal.file.TreeType.DIRECTORY
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint
import org.gradle.internal.hash.ClassLoaderHierarchyHasher
import org.gradle.internal.hash.HashCode
import org.gradle.internal.snapshot.ValueSnapshot
import org.gradle.kotlin.dsl.*
import org.gradle.kotlin.dsl.cache.KotlinDslWorkspaceProvider
import org.gradle.kotlin.dsl.concurrent.IO
import org.gradle.kotlin.dsl.concurrent.writeFile
import org.gradle.kotlin.dsl.support.bytecode.InternalName
import org.gradle.kotlin.dsl.support.bytecode.KmTypeBuilder
import java.io.File
import javax.inject.Inject


/**
 * Produces an [AccessorsClassPath] with type-safe accessors for Stage 1 blocks such as
 * `buildscript {}` and `plugins {}`.
 *
 * Generates accessors for:
 * - dependency version catalogs found in this build,
 * - plugin spec builders for all plugin ids found in the `buildSrc` classpath.
 */
class Stage1BlocksAccessorClassPathGenerator @Inject internal constructor(
    private val classLoaderHierarchyHasher: ClassLoaderHierarchyHasher,
    private val fileCollectionFactory: FileCollectionFactory,
    private val executionEngine: ExecutionEngine,
    private val inputFingerprinter: InputFingerprinter,
    private val workspaceProvider: KotlinDslWorkspaceProvider
) {
    fun stage1BlocksAccessorClassPath(project: ProjectInternal): AccessorsClassPath =
        project.owner.owner.projects.rootProject.mutableModel.let { rootProject ->
            rootProject.getOrCreateProperty("gradleKotlinDsl.stage1AccessorsClassPath") {
                val buildSrcClassLoaderScope = baseClassLoaderScopeOf(rootProject)
                val classLoaderHash = requireNotNull(classLoaderHierarchyHasher.getClassLoaderHash(buildSrcClassLoaderScope.exportClassLoader))
                val versionCatalogAccessors = generateVersionCatalogAccessors(rootProject, buildSrcClassLoaderScope, classLoaderHash)
                val pluginSpecBuildersAccessors = generatePluginSpecBuildersAccessors(rootProject, buildSrcClassLoaderScope, classLoaderHash)
                versionCatalogAccessors + pluginSpecBuildersAccessors
            }
        }

    private
    fun baseClassLoaderScopeOf(rootProject: Project) =
        (rootProject as ProjectInternal).baseClassLoaderScope

    private
    fun generateVersionCatalogAccessors(
        rootProject: Project,
        buildSrcClassLoaderScope: ClassLoaderScope,
        classLoaderHash: HashCode,
    ): AccessorsClassPath =
        rootProject.extensions.extensionsSchema
            .filter { catalogExtensionBaseType.isAssignableFrom(it.publicType) }
            .takeIf { it.isNotEmpty() }
            ?.let { versionCatalogExtensionSchemas ->

                val work = GenerateVersionCatalogAccessors(
                    versionCatalogExtensionSchemas,
                    rootProject,
                    buildSrcClassLoaderScope,
                    classLoaderHash,
                    fileCollectionFactory,
                    inputFingerprinter,
                    workspaceProvider
                )
                val result = executionEngine.createRequest(work).execute()
                result.execution.get().output as AccessorsClassPath
            }
            ?: AccessorsClassPath.empty

    private
    val catalogExtensionBaseType = typeOf<ExternalModuleDependencyFactory>()

    private
    fun generatePluginSpecBuildersAccessors(
        rootProject: Project,
        buildSrcClassLoaderScope: ClassLoaderScope,
        classLoaderHash: HashCode,
    ): AccessorsClassPath {
        val work = GeneratePluginSpecBuilderAccessors(
            rootProject,
            buildSrcClassLoaderScope,
            classLoaderHash,
            fileCollectionFactory,
            inputFingerprinter,
            workspaceProvider
        )
        val result = executionEngine.createRequest(work).execute()
        return result.execution.get().output as AccessorsClassPath
    }
}


internal
abstract class AbstractStage1BlockAccessorsUnitOfWork(
    protected val rootProject: Project,
    protected val buildSrcClassLoaderScope: ClassLoaderScope,
    protected val classLoaderHash: HashCode,
    private val fileCollectionFactory: FileCollectionFactory,
    private val inputFingerprinter: InputFingerprinter,
    private val workspaceProvider: KotlinDslWorkspaceProvider,
) : UnitOfWork {

    companion object {
        const val BUILD_SRC_CLASSLOADER_INPUT_PROPERTY = "buildSrcClassLoader"
        const val SOURCES_OUTPUT_PROPERTY = "sources"
        const val CLASSES_OUTPUT_PROPERTY = "classes"
    }

    override fun identify(identityInputs: MutableMap<String, ValueSnapshot>, identityFileInputs: MutableMap<String, CurrentFileCollectionFingerprint>) =
        UnitOfWork.Identity { "$classLoaderHash-$identitySuffix" }

    protected
    abstract val identitySuffix: String

    override fun loadAlreadyProducedOutput(workspace: File) = AccessorsClassPath(
        DefaultClassPath.of(getClassesOutputDir(workspace)),
        DefaultClassPath.of(getSourcesOutputDir(workspace))
    )

    override fun getWorkspaceProvider() = workspaceProvider.accessors

    override fun getInputFingerprinter() = inputFingerprinter

    override fun visitIdentityInputs(visitor: InputVisitor) {
        visitor.visitInputProperty(BUILD_SRC_CLASSLOADER_INPUT_PROPERTY) { classLoaderHash }
    }

    override fun visitOutputs(workspace: File, visitor: UnitOfWork.OutputVisitor) {
        val sourcesOutputDir = getSourcesOutputDir(workspace)
        val classesOutputDir = getClassesOutputDir(workspace)
        visitor.visitOutputProperty(SOURCES_OUTPUT_PROPERTY, DIRECTORY, OutputFileValueSupplier.fromStatic(sourcesOutputDir, fileCollectionFactory.fixed(sourcesOutputDir)))
        visitor.visitOutputProperty(CLASSES_OUTPUT_PROPERTY, DIRECTORY, OutputFileValueSupplier.fromStatic(classesOutputDir, fileCollectionFactory.fixed(classesOutputDir)))
    }

    protected
    fun getClassesOutputDir(workspace: File) = File(workspace, "classes")

    protected
    fun getSourcesOutputDir(workspace: File): File = File(workspace, "sources")
}


internal
data class ExtensionSpec(
    val name: String,
    val receiverType: TypeSpec,
    val returnType: TypeSpec
)


internal
data class TypeSpec(val sourceName: String, val internalName: InternalName) {

    val builder: KmTypeBuilder
        get() = { visitClass(internalName) }

    private
    fun KmTypeVisitor.visitClass(internalName: InternalName) {
        visitClass(internalName.value)
    }
}


internal
fun IO.writeClassFileTo(binDir: File, internalClassName: InternalName, classBytes: ByteArray) {
    val classFile = binDir.resolve("$internalClassName.class")
    writeFile(classFile, classBytes)
}


internal
val nonInlineGetterFlags = flagsOf(Flag.IS_PUBLIC, Flag.PropertyAccessor.IS_NOT_DEFAULT)
