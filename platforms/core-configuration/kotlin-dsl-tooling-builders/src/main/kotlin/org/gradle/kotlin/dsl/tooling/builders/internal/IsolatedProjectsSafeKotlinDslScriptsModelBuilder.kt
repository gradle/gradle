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

package org.gradle.kotlin.dsl.tooling.builders.internal

import org.gradle.api.Project
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectState
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.ClassPath.EMPTY
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.kotlin.dsl.accessors.AccessorsClassPath
import org.gradle.kotlin.dsl.accessors.Stage1BlocksAccessorClassPathGenerator
import org.gradle.kotlin.dsl.provider.ClassPathModeExceptionCollector
import org.gradle.kotlin.dsl.provider.KotlinScriptClassPathProvider
import org.gradle.kotlin.dsl.provider.runCatching
import org.gradle.kotlin.dsl.resolver.SourceDistributionResolver
import org.gradle.kotlin.dsl.resolver.SourcePathProvider
import org.gradle.kotlin.dsl.support.ImplicitImports
import org.gradle.kotlin.dsl.support.filter
import org.gradle.kotlin.dsl.support.isGradleKotlinDslJar
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.kotlin.dsl.tooling.builders.AbstractKotlinDslScriptsModelBuilder
import org.gradle.kotlin.dsl.tooling.builders.KotlinDslScriptsParameter
import org.gradle.kotlin.dsl.tooling.builders.PrecompiledScriptPluginsMetadataDir
import org.gradle.kotlin.dsl.tooling.builders.StandardKotlinDslScriptModel
import org.gradle.kotlin.dsl.tooling.builders.StandardKotlinDslScriptsModel
import org.gradle.kotlin.dsl.tooling.builders.accessorsClassPathOf
import org.gradle.kotlin.dsl.tooling.builders.addNotNull
import org.gradle.kotlin.dsl.tooling.builders.compilationClassPathForScriptPluginOf
import org.gradle.kotlin.dsl.tooling.builders.createStandardKotlinDslScriptsModel
import org.gradle.kotlin.dsl.tooling.builders.discoverBuildScript
import org.gradle.kotlin.dsl.tooling.builders.discoverInitScripts
import org.gradle.kotlin.dsl.tooling.builders.discoverPrecompiledScriptPluginScripts
import org.gradle.kotlin.dsl.tooling.builders.discoverSettingScript
import org.gradle.kotlin.dsl.tooling.builders.resolveCorrelationIdParameter
import org.gradle.kotlin.dsl.tooling.builders.buildEditorReportsFor
import org.gradle.kotlin.dsl.tooling.builders.mapEditorReports
import org.gradle.kotlin.dsl.tooling.builders.runtimeFailuresLocatedIn
import org.gradle.kotlin.dsl.tooling.builders.isLocationAwareEditorHintsEnabled
import org.gradle.kotlin.dsl.tooling.builders.scriptCompilationClassPath
import org.gradle.kotlin.dsl.tooling.builders.scriptHandlerFactoryOf
import org.gradle.kotlin.dsl.tooling.builders.settings
import org.gradle.kotlin.dsl.tooling.builders.sourcePathFor
import org.gradle.kotlin.dsl.tooling.builders.sourceSets
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptModel
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel
import org.gradle.tooling.provider.model.ToolingModelBuilder
import org.gradle.tooling.provider.model.internal.IntermediateToolingModelProvider
import java.io.File


internal
class IsolatedProjectsSafeKotlinDslScriptsModelBuilder(
    private val intermediateModelProvider: IntermediateToolingModelProvider
) : AbstractKotlinDslScriptsModelBuilder() {

    override fun prepareParameter(rootProject: Project): KotlinDslScriptsParameter {
        require(rootProject.findProperty(KotlinDslScriptsModel.SCRIPTS_GRADLE_PROPERTY_NAME) == null) {
            "Property ${KotlinDslScriptsModel.SCRIPTS_GRADLE_PROPERTY_NAME} is not supported with Isolated Projects"
        }

        return KotlinDslScriptsParameter(rootProject.resolveCorrelationIdParameter(), emptyList())
    }

    override fun buildFor(parameter: KotlinDslScriptsParameter, rootProject: Project): KotlinDslScriptsModel {
        return buildFor(rootProject as ProjectInternal)
    }

    private
    fun buildFor(rootProject: ProjectInternal): StandardKotlinDslScriptsModel {
        val base = ScriptModelBase(rootProject)

        val nonProjectIntermediate = nonProjectIntermediateModels(rootProject)
        val projectHierarchy = visitProjectHierarchy(rootProject, intermediateModelProvider)

        // Script evaluation failures are reported to a single build-scoped collector rather than travelling
        // back with each intermediate model, so we read them here once every script (init, settings, and
        // every project's build script) has been evaluated above.
        // TODO:isolated reconsider this central-collector approach for the IP-first model builders.
        val exceptions = rootProject.serviceOf<ClassPathModeExceptionCollector>().exceptions

        val nonProjectScriptModels = buildOutputsForNonProject(nonProjectIntermediate, base, exceptions)
        val projectHierarchyScriptModels = buildOutputsForHierarchy(projectHierarchy, base, exceptions)
        return createStandardKotlinDslScriptsModel(nonProjectScriptModels + projectHierarchyScriptModels)
    }
}


private fun buildOutputsForNonProject(
    nonProjectIntermediate: List<NonProjectScriptModel>,
    base: ScriptModelBase,
    exceptions: List<Exception>
): Map<File, StandardKotlinDslScriptModel> = nonProjectIntermediate.associateBy({ it.scriptFile }) {
    val classPath = base.nonProjectScriptPaths.bin + it.classPath
    val gradleKotlinDslJar = classPath.filter(::isGradleKotlinDslJar)
    val sourcePath = gradleKotlinDslJar + base.nonProjectScriptPaths.src + it.sourcePath
    buildOutputModel(it.scriptFile, classPath, sourcePath, base.implicitImports, exceptions, base.locationAwareEditorHints)
}


internal
class ScriptModelBase(
    private val rootProject: ProjectInternal
) {

    private
    val scriptClassPath: ClassPath by unsafeLazy {
        rootProject.gradle.baseScriptClassPath()
    }

    private
    val buildSrcSources: Collection<File> by unsafeLazy {
        SourcePathProvider.buildSrcRootsOf(rootProject.rootDir)
    }

    private
    val gradleSourceRoots: Collection<File> by unsafeLazy {
        rootProject.gradleSourceRoots()
    }

    val implicitImports: List<String> by unsafeLazy {
        rootProject.serviceOf<ImplicitImports>().list
    }

    val locationAwareEditorHints: Boolean by unsafeLazy {
        rootProject.isLocationAwareEditorHintsEnabled
    }

    val nonProjectScriptPaths: ScriptClassPath by unsafeLazy {
        ScriptClassPath(
            bin = EMPTY, // Non-project script models currently resolve their base classpath themselves
            src = EMPTY + gradleSourceRoots
        )
    }

    val scriptPaths: ScriptClassPath by unsafeLazy {
        ScriptClassPath(
            bin = scriptClassPath,
            src = EMPTY + buildSrcSources + gradleSourceRoots
        )
    }
}


internal
data class ScriptClassPath(val bin: ClassPath, val src: ClassPath)


private
fun nonProjectIntermediateModels(rootProject: ProjectInternal): List<NonProjectScriptModel> =
    buildList {
        addAll(initScriptModels(rootProject))
        addNotNull(settingsScriptModel(rootProject))
    }


private
data class ProjectModelWithParentSource(val model: IsolatedScriptsModel, val parentSourcePath: ClassPath)


/**
 * Builds Kotlin DSL script models for the whole project hierarchy.<p>
 *
 * Models returned are "best-effort", meaning that if configuration for a project is not complete we prefer to return "something" over "nothing",
 * since then IDE can still show code highlighting for at least some parts of Kotlin DSL script, even if project configuration fails.
 */
private
fun visitProjectHierarchy(
    rootProject: ProjectInternal,
    intermediateModelProvider: IntermediateToolingModelProvider
): List<ProjectModelWithParentSource> {
    val visited = mutableListOf<ProjectModelWithParentSource>()
    val classPathModeExceptionCollector = rootProject.serviceOf<ClassPathModeExceptionCollector>()

    fun prepareForParallelAccess() {
        // Avoid deadlock on stage1BlocksAccessorClassPath on root.fromMutableState call.
        // It's wrapped in runCatching the same way the per-project and settings accessorsClassPathOf calls,
        // so a broken root build degrades to empty accessors instead of aborting the model build.
        classPathModeExceptionCollector.runCatching {
            rootProject.serviceOf<Stage1BlocksAccessorClassPathGenerator>().prepareForParallelAccess()
        }
    }

    fun visitChildren(projectState: ProjectState, parentSourcePath: ClassPath) {
        val children = projectState.childProjects.toList()
        val childrenResults = intermediateModelProvider.getModelsAllowingFailures(projectState, children, IsolatedScriptsModel::class.java, null)
        childrenResults.zip(children).forEach { (result, child) ->
            for (failure in result.failures) {
                val original = failure.original
                classPathModeExceptionCollector.collect(original as? Exception ?: RuntimeException(original))
            }
            result.model?.let {
                visited.add(ProjectModelWithParentSource(it, parentSourcePath))
                visitChildren(child, parentSourcePath + it.buildScriptSourcePath)
            }
        }
    }

    prepareForParallelAccess()
    val rootModel = isolatedScriptsModelFor(rootProject)
    visited.add(ProjectModelWithParentSource(rootModel, EMPTY))
    visitChildren(rootProject.owner, rootModel.buildScriptSourcePath)
    return visited
}


private
fun buildOutputsForHierarchy(
    visitedProjects: List<ProjectModelWithParentSource>,
    base: ScriptModelBase,
    exceptions: List<Exception>
): Map<File, KotlinDslScriptModel> {
    val outputModels = mutableMapOf<File, KotlinDslScriptModel>()
    visitedProjects.forEach { (model, parentSourcePath) ->
        for (childScriptModel in model.models) {
            val classPath = childScriptModel.localClassPath
            val gradleKotlinDslJar = classPath.filter(::isGradleKotlinDslJar)
            val effectiveParentSourcePath = if (childScriptModel.includeParentSourcePath) parentSourcePath else EMPTY
            val sourcePath = gradleKotlinDslJar + base.scriptPaths.src + effectiveParentSourcePath + childScriptModel.localSourcePath
            val implicitImports = base.implicitImports + childScriptModel.localImplicitImports
            // Use the owning project's locationAwareEditorHints — a subproject's gradle.properties
            // override is only visible inside that project's IsolatedScriptsModel build.
            outputModels[childScriptModel.scriptFile] = buildOutputModel(
                childScriptModel.scriptFile,
                classPath,
                sourcePath,
                implicitImports,
                exceptions,
                model.locationAwareEditorHints
            )
        }
    }
    return outputModels
}


private
fun GradleInternal.baseScriptClassPath(): ClassPath {
    return serviceOf<KotlinScriptClassPathProvider>()
        .compilationClassPathOf(baseProjectClassLoaderScope())
}


private
fun ProjectInternal.gradleSourceRoots() =
    gradle.gradleHomeDir?.let { SourcePathProvider.sourceRootsOf(it, SourceDistributionResolver(this)) } ?: emptyList()


private
fun initScriptModels(rootProject: ProjectInternal): List<NonProjectScriptModel> {
    return rootProject.discoverInitScripts().map {
        buildInitScriptModel(it, rootProject)
    }
}


private
fun settingsScriptModel(rootProject: ProjectInternal): NonProjectScriptModel? {
    return rootProject.discoverSettingScript()?.let {
        buildSettingsScriptModel(it, rootProject)
    }
}


private
fun buildInitScriptModel(initScript: File, rootProject: ProjectInternal): NonProjectScriptModel {
    val gradle = rootProject.gradle

    val (scriptHandler, scriptClassPath) = compilationClassPathForScriptPluginOf(
        target = gradle,
        scriptFile = initScript,
        baseScope = gradle.classLoaderScope,
        scriptHandlerFactory = scriptHandlerFactoryOf(gradle),
        project = rootProject,
        resourceDescription = "initialization script"
    )

    return NonProjectScriptModel(
        initScript,
        scriptClassPath,
        sourcePathFor(listOf(scriptHandler)),
    )
}


private
fun buildSettingsScriptModel(settingsScript: File, rootProject: Project): NonProjectScriptModel {
    val settings = rootProject.settings
    val scriptCompilationClassPath = settings.scriptCompilationClassPath
    val accessorsClassPath = rootProject.serviceOf<ClassPathModeExceptionCollector>().runCatching {
        settings.accessorsClassPathOf(scriptCompilationClassPath)
    } ?: AccessorsClassPath.empty

    return NonProjectScriptModel(
        settingsScript,
        scriptCompilationClassPath + accessorsClassPath.bin,
        sourcePathFor(listOf(settings.buildscript)) + accessorsClassPath.src,
    )
}


internal
data class NonProjectScriptModel(
    val scriptFile: File,
    val classPath: ClassPath,
    val sourcePath: ClassPath
)


internal
data class IntermediateScriptModel(
    val scriptFile: File,
    val localClassPath: ClassPath,
    val localSourcePath: ClassPath,
    val localImplicitImports: List<String> = emptyList(),
    val includeParentSourcePath: Boolean = true,
)


internal
data class IsolatedScriptsModel(
    val models: List<IntermediateScriptModel>,
    val buildScriptSourcePath: ClassPath,
    val locationAwareEditorHints: Boolean
)


internal
object IsolatedScriptsModelBuilder : ToolingModelBuilder {

    override fun canBuild(modelName: String): Boolean =
        modelName == "org.gradle.kotlin.dsl.tooling.builders.internal.IsolatedScriptsModel"

    override fun buildAll(modelName: String, project: Project): IsolatedScriptsModel {
        return isolatedScriptsModelFor(project as ProjectInternal)
    }
}


private
fun isolatedScriptsModelFor(project: ProjectInternal): IsolatedScriptsModel {
    val buildScriptModel = buildScriptModelFor(project)
    val models = mutableListOf<IntermediateScriptModel>().apply {
        addNotNull(buildScriptModel)
        addAll(precompiledScriptModelsFor(project))
    }
    val buildScriptSourcePath =
        if (buildScriptModel != null) sourcePathFor(listOf(project.buildscript))
        else EMPTY
    val locationAwareEditorHints = project.isLocationAwareEditorHintsEnabled
    return IsolatedScriptsModel(models, buildScriptSourcePath, locationAwareEditorHints)
}


private
fun buildScriptModelFor(project: ProjectInternal): IntermediateScriptModel? {
    val buildScript = project.discoverBuildScript()
        ?: return null

    // TODO:isolated this relies on the hierarchy of classloaders
    val compilationClassPath = project.scriptCompilationClassPath

    val accessorsClassPath = project.serviceOf<ClassPathModeExceptionCollector>().runCatching {
        project.accessorsClassPathOf(compilationClassPath)
    } ?: AccessorsClassPath.empty

    val classpathSources = sourcePathFor(listOf(project.buildscript))

    return IntermediateScriptModel(
        scriptFile = buildScript,
        localClassPath = compilationClassPath + accessorsClassPath.bin,
        localSourcePath = classpathSources + accessorsClassPath.src,
    )
}


private
fun precompiledScriptModelsFor(project: ProjectInternal): List<IntermediateScriptModel> {
    val scripts = project.discoverPrecompiledScriptPluginScripts()
    if (scripts.isEmpty()) return emptyList()

    val sourceSets = project.sourceSets ?: return emptyList()
    val metadataDir = PrecompiledScriptPluginsMetadataDir.of(project)

    val classPathBySourceSet = mutableMapOf<String, ClassPath>()
    val pluginSpecImports = metadataDir.implicitPluginSpecBuildersImports

    return scripts.mapNotNull { scriptFile ->
        val sourceSet = sourceSets.find { scriptFile in it.allSource } ?: return@mapNotNull null
        val classPath = classPathBySourceSet.getOrPut(sourceSet.name) { DefaultClassPath.of(sourceSet.compileClasspath) }
        val accessorImports = metadataDir.implicitAccessorsImports(scriptFile)
        IntermediateScriptModel(
            scriptFile,
            classPath,
            EMPTY,
            accessorImports + pluginSpecImports,
            includeParentSourcePath = false
        )
    }
}

private
fun buildOutputModel(
    scriptFile: File,
    classPath: ClassPath,
    sourcePath: ClassPath,
    implicitImports: List<String>,
    exceptions: List<Exception>,
    locationAwareEditorHints: Boolean
) = StandardKotlinDslScriptModel(
    classPath.asFiles,
    sourcePath.asFiles,
    implicitImports,
    editorReports = mapEditorReports(buildEditorReportsFor(scriptFile, exceptions, locationAwareEditorHints)),
    exceptions = getExceptionsForFile(scriptFile, exceptions)
)


private
fun getExceptionsForFile(scriptFile: File, exceptions: List<Exception>): List<String> =
    exceptions.asSequence().runtimeFailuresLocatedIn(scriptFile.path).map(::exceptionToString).toList()


private
fun exceptionToString(exception: Exception) = exception.stackTraceToString()
