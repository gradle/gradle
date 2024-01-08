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
import org.gradle.api.internal.project.ProjectHierarchyUtils
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.internal.classpath.ClassPath
import org.gradle.kotlin.dsl.accessors.AccessorsClassPath
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
import org.gradle.kotlin.dsl.tooling.builders.StandardKotlinDslScriptModel
import org.gradle.kotlin.dsl.tooling.builders.StandardKotlinDslScriptsModel
import org.gradle.kotlin.dsl.tooling.builders.accessorsClassPathOf
import org.gradle.kotlin.dsl.tooling.builders.addNotNull
import org.gradle.kotlin.dsl.tooling.builders.compilationClassPathForScriptPluginOf
import org.gradle.kotlin.dsl.tooling.builders.discoverBuildScript
import org.gradle.kotlin.dsl.tooling.builders.discoverInitScripts
import org.gradle.kotlin.dsl.tooling.builders.discoverPrecompiledScriptPluginScripts
import org.gradle.kotlin.dsl.tooling.builders.discoverSettingScript
import org.gradle.kotlin.dsl.tooling.builders.resolveCorrelationIdParameter
import org.gradle.kotlin.dsl.tooling.builders.runtimeFailuresLocatedIn
import org.gradle.kotlin.dsl.tooling.builders.scriptCompilationClassPath
import org.gradle.kotlin.dsl.tooling.builders.scriptHandlerFactoryOf
import org.gradle.kotlin.dsl.tooling.builders.settings
import org.gradle.kotlin.dsl.tooling.builders.sourcePathFor
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
        val nonProjectScriptModels = buildNonProjectScriptModels(rootProject, base)
        val projectHierarchyScriptModels = buildScriptModelsInHierarchy(rootProject, base, intermediateModelProvider)
        return StandardKotlinDslScriptsModel.from(nonProjectScriptModels + projectHierarchyScriptModels)
    }
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

    val classPathModeExceptions: List<Exception> by unsafeLazy {
        rootProject.serviceOf<ClassPathModeExceptionCollector>().exceptions
    }

    val implicitImports: List<String> by unsafeLazy {
        rootProject.serviceOf<ImplicitImports>().list
    }

    val nonProjectScriptPaths: ScriptClassPath by unsafeLazy {
        ScriptClassPath(
            bin = ClassPath.EMPTY, // Non-project script models currently resolve their base classpath themselves
            src = ClassPath.EMPTY + gradleSourceRoots
        )
    }

    val scriptPaths: ScriptClassPath by unsafeLazy {
        ScriptClassPath(
            bin = scriptClassPath,
            src = ClassPath.EMPTY + buildSrcSources + gradleSourceRoots
        )
    }
}


internal
data class ScriptClassPath(val bin: ClassPath, val src: ClassPath)


private
fun buildNonProjectScriptModels(
    rootProject: ProjectInternal,
    base: ScriptModelBase
): Map<File, StandardKotlinDslScriptModel> {

    val intermediateModels = buildList {
        addAll(initScriptModels(rootProject))
        addNotNull(settingsScriptModel(rootProject))
    }

    return intermediateModels.associateBy({ it.scriptFile }) {
        val classPath = base.nonProjectScriptPaths.bin + it.classPath
        val gradleKotlinDslJar = classPath.filter(::isGradleKotlinDslJar)
        val sourcePath = gradleKotlinDslJar + base.nonProjectScriptPaths.src + it.sourcePath
        buildOutputModel(it.scriptFile, classPath, sourcePath, base.implicitImports, base.classPathModeExceptions)
    }
}


private
fun buildScriptModelsInHierarchy(
    rootProject: ProjectInternal,
    base: ScriptModelBase,
    intermediateModelProvider: IntermediateToolingModelProvider
): Map<File, KotlinDslScriptModel> {

    val outputModels = mutableMapOf<File, KotlinDslScriptModel>()

    fun collect(models: IsolatedScriptsModel) {
        for (childScriptModel in models.models) {
            outputModels[childScriptModel.scriptFile] = buildOutputModel(base, childScriptModel)
        }
    }

    fun visit(project: Project) {
        val children = ProjectHierarchyUtils.getChildProjectsForInternalUse(project).toList()
        val childrenModels = intermediateModelProvider.getIsolatedModels(project, children)
        childrenModels.forEach { collect(it) }
        children.forEach { visit(it) }
    }

    collect(isolatedScriptsModelFor(rootProject))
    visit(rootProject)

    return outputModels
}


private
fun IntermediateToolingModelProvider.getIsolatedModels(requester: Project, targets: List<Project>): List<IsolatedScriptsModel> =
    getModels(requester, targets, IsolatedScriptsModel::class.java, null)


private
fun buildOutputModel(base: ScriptModelBase, model: IntermediateScriptModel): StandardKotlinDslScriptModel {
    val classPath = base.scriptPaths.bin + model.localClassPath
    val gradleKotlinDslJar = classPath.filter(::isGradleKotlinDslJar)
    val sourcePath = gradleKotlinDslJar + base.scriptPaths.src + model.localSourcePath
    val implicitImports = base.implicitImports + model.localImplicitImports
    return buildOutputModel(model.scriptFile, classPath, sourcePath, implicitImports, base.classPathModeExceptions)
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
    return NonProjectScriptModel(
        settingsScript,
        settings.scriptCompilationClassPath,
        sourcePathFor(listOf(settings.buildscript)),
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
)


internal
data class IsolatedScriptsModel(
    val models: List<IntermediateScriptModel>
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
    // TODO:isolated compute own classpaths
    val additionalClassPath = ClassPath.EMPTY
    val additionalSourcePath = ClassPath.EMPTY
    val models = buildList {
        addNotNull(buildScriptModelFor(project, additionalClassPath, additionalSourcePath))
        this.addAll(precompiledScriptModelsFor(project))
    }
    return IsolatedScriptsModel(models)
}


private
fun buildScriptModelFor(
    project: ProjectInternal,
    localClassPath: ClassPath,
    localSourcePath: ClassPath
): IntermediateScriptModel? {

    val buildScript = project.discoverBuildScript()
        ?: return null

    // TODO:isolated this relies on the hierarchy of classloaders
    val compilationClassPath = project.scriptCompilationClassPath

    val accessorsClassPath = project.serviceOf<ClassPathModeExceptionCollector>().runCatching {
        project.accessorsClassPathOf(compilationClassPath)
    } ?: AccessorsClassPath.empty

    return IntermediateScriptModel(
        scriptFile = buildScript,
        localClassPath = localClassPath + accessorsClassPath.bin,
        localSourcePath = localSourcePath + accessorsClassPath.src,
    )
}


private
fun precompiledScriptModelsFor(project: ProjectInternal): List<IntermediateScriptModel> {
    return project.discoverPrecompiledScriptPluginScripts().map {
        // TODO:isolated support precompiled scripts
        IntermediateScriptModel(it, ClassPath.EMPTY, ClassPath.EMPTY, emptyList())
    }
}


private
fun buildOutputModel(scriptFile: File, classPath: ClassPath, sourcePath: ClassPath, implicitImports: List<String>, exceptions: List<Exception>) =
    StandardKotlinDslScriptModel(
        classPath.asFiles,
        sourcePath.asFiles,
        implicitImports,
        editorReports = emptyList(), // TODO:isolated support editor reports
        exceptions = getExceptionsForFile(scriptFile, exceptions)
    )


private
fun getExceptionsForFile(scriptFile: File, exceptions: List<Exception>): List<String> =
    exceptions.asSequence().runtimeFailuresLocatedIn(scriptFile.path).map(::exceptionToString).toList()


private
fun exceptionToString(exception: Exception) = exception.stackTraceToString()
