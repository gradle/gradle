/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.kotlin.dsl.tooling.builders

import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.SettingsInternal
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.initialization.ScriptHandlerFactory
import org.gradle.api.internal.initialization.ScriptHandlerInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.invocation.Gradle
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSet

import org.gradle.groovy.scripts.TextResourceScriptSource

import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.internal.resource.BasicTextResourceLoader

import org.gradle.kotlin.dsl.accessors.AccessorsClassPath
import org.gradle.kotlin.dsl.accessors.accessorsClassPathFor
import org.gradle.kotlin.dsl.findPlugin

import org.gradle.kotlin.dsl.provider.ClassPathModeExceptionCollector
import org.gradle.kotlin.dsl.provider.KotlinScriptClassPathProvider
import org.gradle.kotlin.dsl.provider.KotlinScriptFactory
import org.gradle.kotlin.dsl.provider.KotlinScriptOption

import org.gradle.kotlin.dsl.resolver.SourceDistributionResolver
import org.gradle.kotlin.dsl.resolver.SourcePathProvider
import org.gradle.kotlin.dsl.resolver.kotlinBuildScriptModelTarget

import org.gradle.kotlin.dsl.support.ImplicitImports
import org.gradle.kotlin.dsl.support.KotlinScriptType
import org.gradle.kotlin.dsl.support.kotlinScriptTypeFor
import org.gradle.kotlin.dsl.support.serviceOf

import org.gradle.kotlin.dsl.tooling.models.KotlinBuildScriptModel

import org.gradle.tooling.provider.model.ToolingModelBuilder

import java.io.File
import java.io.Serializable
import java.util.*

import kotlin.coroutines.experimental.buildSequence


private
class KotlinBuildScriptModelParameter(val scriptPath: String?)


private
data class StandardKotlinBuildScriptModel(
    override val classPath: List<File>,
    override val sourcePath: List<File>,
    override val implicitImports: List<String>,
    override val exceptions: List<Exception>
) : KotlinBuildScriptModel, Serializable


internal
object KotlinBuildScriptModelBuilder : ToolingModelBuilder {

    override fun canBuild(modelName: String): Boolean =
        modelName == "org.gradle.kotlin.dsl.tooling.models.KotlinBuildScriptModel"

    override fun buildAll(modelName: String, modelRequestProject: Project): KotlinBuildScriptModel =
        scriptModelBuilderFor(modelRequestProject as ProjectInternal, requestParameterOf(modelRequestProject))
            .buildModel()

    private
    fun scriptModelBuilderFor(modelRequestProject: ProjectInternal, parameter: KotlinBuildScriptModelParameter): KotlinScriptTargetModelBuilder {

        val scriptFile = parameter.scriptFile
            ?: return projectScriptModelBuilder(modelRequestProject)

        modelRequestProject.findProjectWithBuildFile(scriptFile)?.let { buildFileProject ->
            return projectScriptModelBuilder(buildFileProject)
        }

        modelRequestProject.enclosingSourceSetOf(scriptFile)?.let { enclosingSourceSet ->
            return precompiledScriptPluginModelBuilder(enclosingSourceSet, modelRequestProject)
        }

        if (isSettingsFileOf(modelRequestProject, scriptFile)) {
            return settingsScriptModelBuilder(modelRequestProject)
        }

        return when (kotlinScriptTypeFor(scriptFile)) {
            KotlinScriptType.INIT -> initScriptModelBuilder(scriptFile, modelRequestProject)
            KotlinScriptType.SETTINGS -> settingsScriptPluginModelBuilder(scriptFile, modelRequestProject)
            else -> projectScriptPluginModelBuilder(scriptFile, modelRequestProject)
        }
    }

    private
    fun isSettingsFileOf(project: Project, scriptFile: File): Boolean =
        project.settings.settingsScript.resource.file?.canonicalFile == scriptFile

    private
    fun requestParameterOf(modelRequestProject: Project) =
        KotlinBuildScriptModelParameter(
            modelRequestProject.findProperty(kotlinBuildScriptModelTarget) as? String)
}


private
fun Project.findProjectWithBuildFile(file: File) =
    allprojects.find { it.buildFile == file }


private
fun Project.enclosingSourceSetOf(file: File): SourceSet? =
    findSourceSetOf(file)
        ?: findSourceSetOfFileIn(subprojects, file)


private
fun findSourceSetOfFileIn(projects: Iterable<Project>, file: File): SourceSet? =
    projects
        .asSequence()
        .mapNotNull { it.findSourceSetOf(file) }
        .firstOrNull()


private
fun Project.findSourceSetOf(file: File): SourceSet? =
    sourceSets?.find { file in it.allSource }


private
val Project.sourceSets
    get() = convention.findPlugin<JavaPluginConvention>()?.sourceSets


private
fun precompiledScriptPluginModelBuilder(enclosingSourceSet: SourceSet, modelRequestProject: Project): KotlinScriptTargetModelBuilder =
    KotlinScriptTargetModelBuilder(
        project = modelRequestProject,
        scriptClassPath = DefaultClassPath.of(enclosingSourceSet.compileClasspath))


private
fun projectScriptModelBuilder(project: Project) =
    KotlinScriptTargetModelBuilder(
        project = project,
        scriptClassPath = project.scriptCompilationClassPath,
        accessorsClassPath = { classPath -> accessorsClassPathFor(project, classPath) },
        sourceLookupScriptHandlers = sourceLookupScriptHandlersFor(project))


private
fun initScriptModelBuilder(scriptFile: File, project: ProjectInternal) = project.run {

    val (scriptHandler, scriptClassPath) = compilationClassPathForScriptPluginOf(
        target = gradle,
        scriptFile = scriptFile,
        baseScope = gradle.classLoaderScope,
        scriptHandlerFactory = scriptHandlerFactoryOf(gradle),
        project = project
    )

    KotlinScriptTargetModelBuilder(
        project = project,
        scriptClassPath = scriptClassPath,
        sourceLookupScriptHandlers = listOf(scriptHandler))
}


private
fun settingsScriptModelBuilder(project: Project) = project.run {

    KotlinScriptTargetModelBuilder(
        project = project,
        scriptClassPath = settings.scriptCompilationClassPath,
        sourceLookupScriptHandlers = listOf(settings.buildscript))
}


private
fun settingsScriptPluginModelBuilder(scriptFile: File, project: ProjectInternal) = project.run {

    val (scriptHandler, scriptClassPath) = compilationClassPathForScriptPluginOf(
        target = settings,
        scriptFile = scriptFile,
        baseScope = settings.rootClassLoaderScope,
        scriptHandlerFactory = scriptHandlerFactoryOf(gradle),
        project = project
    )

    KotlinScriptTargetModelBuilder(
        project = project,
        scriptClassPath = scriptClassPath,
        sourceLookupScriptHandlers = listOf(scriptHandler, settings.buildscript))
}


private
fun projectScriptPluginModelBuilder(scriptFile: File, project: ProjectInternal) = project.run {

    val (scriptHandler, scriptClassPath) = compilationClassPathForScriptPluginOf(
        target = project,
        scriptFile = scriptFile,
        baseScope = rootProject.baseClassLoaderScope,
        scriptHandlerFactory = scriptHandlerFactoryOf(project),
        project = project
    )

    KotlinScriptTargetModelBuilder(
        project = project,
        scriptClassPath = scriptClassPath,
        sourceLookupScriptHandlers = listOf(scriptHandler, buildscript))
}


private
fun compilationClassPathForScriptPluginOf(
    target: Any,
    scriptFile: File,
    baseScope: ClassLoaderScope,
    scriptHandlerFactory: ScriptHandlerFactory,
    project: ProjectInternal
): Pair<ScriptHandlerInternal, ClassPath> {

    val scriptSource = textResourceScriptSource("Kotlin script plugin", scriptFile)
    val scriptScope = baseScope.createChild("model:${scriptFile.toURI()}")
    val scriptHandler = scriptHandlerFactory.create(scriptSource, scriptScope)

    kotlinScriptFactoryOf(project)
        .kotlinScriptFor(
            target = target,
            scriptSource = scriptSource,
            scriptHandler = scriptHandler,
            targetScope = scriptScope,
            baseScope = baseScope,
            topLevelScript = false,
            options = EnumSet.of(KotlinScriptOption.IgnoreErrors, KotlinScriptOption.SkipBody))
        .invoke()

    return scriptHandler to project.compilationClassPathOf(scriptScope)
}


private
fun kotlinScriptFactoryOf(project: ProjectInternal) =
    project.serviceOf<KotlinScriptFactory>()


private
fun scriptHandlerFactoryOf(project: ProjectInternal) =
    project.serviceOf<ScriptHandlerFactory>()


private
fun scriptHandlerFactoryOf(gradle: Gradle) =
    gradle.serviceOf<ScriptHandlerFactory>()


private
fun textResourceScriptSource(description: String, scriptFile: File) =
    TextResourceScriptSource(BasicTextResourceLoader().loadFile(description, scriptFile))


private
fun sourceLookupScriptHandlersFor(project: Project) =
    project.hierarchy.map { it.buildscript }.toList()


private
data class KotlinScriptTargetModelBuilder(
    val project: Project,
    val scriptClassPath: ClassPath,
    val accessorsClassPath: (ClassPath) -> AccessorsClassPath = { AccessorsClassPath.empty },
    val sourceLookupScriptHandlers: List<ScriptHandler> = emptyList()
) {

    fun buildModel(): KotlinBuildScriptModel {
        val accessorsClassPath = accessorsClassPath(scriptClassPath)
        val classpathSources = sourcePathFor(sourceLookupScriptHandlers)
        val classPathModeExceptionCollector = project.serviceOf<ClassPathModeExceptionCollector>()
        return StandardKotlinBuildScriptModel(
            (scriptClassPath + accessorsClassPath.bin).asFiles,
            (gradleSource() + classpathSources + accessorsClassPath.src).asFiles,
            implicitImports,
            classPathModeExceptionCollector.exceptions)
    }

    private
    fun gradleSource() =
        SourcePathProvider.sourcePathFor(
            scriptClassPath, rootDir, gradleHomeDir, SourceDistributionResolver(project))

    val gradleHomeDir
        get() = project.gradle.gradleHomeDir

    val rootDir
        get() = project.rootDir

    val implicitImports
        get() = project.scriptImplicitImports
}


private
val KotlinBuildScriptModelParameter.scriptFile
    get() = scriptPath?.let { canonicalFile(it) }


private
val Settings.scriptCompilationClassPath
    get() = serviceOf<KotlinScriptClassPathProvider>().compilationClassPathOf(classLoaderScope)


private
val Settings.classLoaderScope
    get() = (this as SettingsInternal).classLoaderScope


private
val Project.settings
    get() = (gradle as GradleInternal).settings


private
val Project.scriptCompilationClassPath
    get() = compilationClassPathOf((this as ProjectInternal).classLoaderScope)


private
fun Project.compilationClassPathOf(classLoaderScope: ClassLoaderScope) =
    serviceOf<KotlinScriptClassPathProvider>().compilationClassPathOf(classLoaderScope)


private
val Project.scriptImplicitImports
    get() = serviceOf<ImplicitImports>().list


private
val Project.hierarchy: Sequence<Project>
    get() = buildSequence {
        var project = this@hierarchy
        yield(project)
        while (project != project.rootProject) {
            project = project.parent!!
            yield(project)
        }
    }


private
fun canonicalFile(path: String): File =
    File(path).canonicalFile
