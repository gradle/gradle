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
import org.gradle.api.internal.project.ProjectOrderingUtil
import org.gradle.api.internal.project.ProjectState
import org.gradle.api.invocation.Gradle
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.groovy.scripts.TextResourceScriptSource
import org.gradle.initialization.DependenciesAccessors
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.internal.resource.TextFileResourceLoader
import org.gradle.internal.time.Time.startTimer
import org.gradle.kotlin.dsl.*
import org.gradle.kotlin.dsl.accessors.AccessorsClassPath
import org.gradle.kotlin.dsl.accessors.ProjectAccessorsClassPathGenerator
import org.gradle.kotlin.dsl.accessors.Stage1BlocksAccessorClassPathGenerator
import org.gradle.kotlin.dsl.execution.EvalOption
import org.gradle.kotlin.dsl.provider.ClassPathModeExceptionCollector
import org.gradle.kotlin.dsl.provider.KotlinScriptClassPathProvider
import org.gradle.kotlin.dsl.provider.KotlinScriptEvaluator
import org.gradle.kotlin.dsl.provider.runCatching
import org.gradle.kotlin.dsl.resolver.SourceDistributionResolver
import org.gradle.kotlin.dsl.resolver.SourcePathProvider
import org.gradle.kotlin.dsl.support.ImplicitImports
import org.gradle.kotlin.dsl.support.KotlinScriptType
import org.gradle.kotlin.dsl.support.kotlinScriptTypeFor
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.kotlin.dsl.tooling.models.KotlinBuildScriptModel
import org.gradle.tooling.provider.model.ToolingModelBuilder
import org.gradle.tooling.provider.model.internal.ToolingModelBuilderResultInternal
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.EnumSet


internal
data class KotlinBuildScriptModelParameter(
    val scriptFile: File?,
    val correlationId: String?
)

internal
object KotlinBuildScriptModelBuilder : ToolingModelBuilder {

    override fun canBuild(modelName: String): Boolean =
        modelName == "org.gradle.kotlin.dsl.tooling.models.KotlinBuildScriptModel"

    override fun buildAll(modelName: String, modelRequestProject: Project): ToolingModelBuilderResultInternal {
        val timer = startTimer()
        val parameter = requestParameterOf(modelRequestProject)
        try {
            val result = kotlinBuildScriptModelFor(modelRequestProject, parameter, SourceSetClassPathResolver())
            log("$parameter => ${result.model}")
            return result.toToolingModelResult(modelRequestProject.serviceOf())
        } catch (e: Exception) {
            log("$parameter => $e")
            throw e
        } finally {
            log("MODEL built in ${timer.elapsed}.")
        }
    }

    internal
    fun kotlinBuildScriptModelFor(
        modelRequestProject: Project,
        parameter: KotlinBuildScriptModelParameter,
        classPathResolver: SourceSetClassPathResolver
    ): ScriptModelResult<KotlinBuildScriptModel> =
        scriptModelBuilderFor(modelRequestProject as ProjectInternal, parameter, classPathResolver).buildModel()

    private
    fun scriptModelBuilderFor(
        modelRequestProject: ProjectInternal,
        parameter: KotlinBuildScriptModelParameter,
        classPathResolver: SourceSetClassPathResolver
    ): KotlinScriptTargetModelBuilder {

        val scriptFile = parameter.scriptFile
            ?: return projectScriptModelBuilder(null, modelRequestProject)

        modelRequestProject.findProjectWithBuildFile(scriptFile)?.let { buildFileProject ->
            return projectScriptModelBuilder(scriptFile, buildFileProject)
        }

        modelRequestProject.enclosingSourceSetOf(scriptFile)?.let { enclosingSourceSet ->
            return precompiledScriptPluginModelBuilder(scriptFile, enclosingSourceSet, modelRequestProject, classPathResolver)
        }

        if (isSettingsFileOf(modelRequestProject, scriptFile)) {
            return settingsScriptModelBuilder(scriptFile, modelRequestProject)
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
            (modelRequestProject.findProperty(KotlinBuildScriptModel.SCRIPT_GRADLE_PROPERTY_NAME) as? String)?.let(::canonicalFile),
            modelRequestProject.resolveCorrelationIdParameter()
        )
}


internal
fun log(message: String) {
    if (System.getProperty("org.gradle.kotlin.dsl.logging.tapi") == "true") {
        println(message)
    }
}


private
fun ProjectInternal.findProjectWithBuildFile(file: File) =
    ProjectOrderingUtil.orderedAllProjectsOf(owner)
        .asSequence()
        .map { it.mutableModelEvenAfterFailure }
        .find { it.buildFile == file }


private
fun ProjectInternal.enclosingSourceSetOf(file: File): EnclosingSourceSet? =
    findSourceSetOf(file)
        ?: findSourceSetOfFileInSubprojects(file)


private
data class EnclosingSourceSet(val project: Project, val sourceSet: SourceSet)


private
fun ProjectInternal.findSourceSetOfFileInSubprojects(file: File): EnclosingSourceSet? =
    ProjectOrderingUtil.orderedSubprojectsOf(owner)
        .asSequence()
        .mapNotNull { it.mutableModelEvenAfterFailure.findSourceSetOf(file) }
        .firstOrNull()


private
fun Project.findSourceSetOf(file: File): EnclosingSourceSet? =
    sourceSets?.find { file in it.allSource }?.let {
        EnclosingSourceSet(this, it)
    }


internal
val Project.sourceSets
    get() = extensions.findByType(typeOf<SourceSetContainer>())

private
fun precompiledScriptPluginModelBuilder(
    scriptFile: File,
    enclosingSourceSet: EnclosingSourceSet,
    modelRequestProject: Project,
    classPathResolver: SourceSetClassPathResolver
): KotlinScriptTargetModelBuilder {
    return KotlinScriptTargetModelBuilder(
        scriptFile = scriptFile,
        project = modelRequestProject,
        scriptClassPath = classPathResolver.resolveCompileClassPathOf(enclosingSourceSet.project, enclosingSourceSet.sourceSet),
        enclosingScriptProjectDir = enclosingSourceSet.project.projectDir,
        additionalImports = {
            PrecompiledScriptPluginsMetadataDir.of(enclosingSourceSet.project).run {
                implicitAccessorsImports(scriptFile) + implicitPluginSpecBuildersImports
            }
        }
    )
}

private
fun projectScriptModelBuilder(
    scriptFile: File?,
    project: ProjectInternal
) = KotlinScriptTargetModelBuilder(
    scriptFile = scriptFile,
    project = project,
    scriptClassPath = ResolvedClassPath(project.scriptCompilationClassPath),
    accessorsClassPath = { project.accessorsClassPathOf(it) },
    sourceLookupScriptHandlers = sourceLookupScriptHandlersFor(project),
    enclosingScriptProjectDir = project.projectDir
)


/**
 * Resolves the compile classpath of source sets, e.g. the one containing precompiled script plugins, once per
 * model request, so that all the scripts of a source set share one classpath, and one failure when it could
 * not be resolved.
 *
 * Resolution can fail, e.g. when the project did not configure completely because its build script body failed
 * to compile before the `repositories {}` block ran, or when a dependency cannot be resolved. This never throws:
 * the failure is returned together with the base script classpath, so a resilient model request still gets a
 * model for the scripts of that source set, and for the other scripts of the build.
 */
internal
class SourceSetClassPathResolver {

    private
    val resolved = mutableMapOf<SourceSet, ResolvedClassPath>()

    fun resolveCompileClassPathOf(project: Project, sourceSet: SourceSet): ResolvedClassPath =
        resolved.getOrPut(sourceSet) { resolve(project, sourceSet) }

    /**
     * The failures of the source sets whose compile classpath could not be resolved, one per source set.
     */
    val failures: List<Throwable>
        get() = resolved.values.mapNotNull { it.failure }

    private
    fun resolve(project: Project, sourceSet: SourceSet): ResolvedClassPath =
        try {
            ResolvedClassPath(DefaultClassPath.of(sourceSet.compileClasspath))
        } catch (e: Exception) {
            ResolvedClassPath(baseScriptClassPathAfter(project, e), e)
        }

    private
    fun baseScriptClassPathAfter(project: Project, resolutionFailure: Exception): ClassPath =
        try {
            (project as ProjectInternal).gradle.baseScriptClassPath()
        } catch (fallbackFailure: Exception) {
            // Without the base script classpath no script of the build gets a classpath, so lets give up
            fallbackFailure.addSuppressed(resolutionFailure)
            throw fallbackFailure
        }
}


internal
fun GradleInternal.baseScriptClassPath(): ClassPath =
    serviceOf<KotlinScriptClassPathProvider>().compilationClassPathOf(baseProjectClassLoaderScope())


/**
 * The accessors classpath of a project script, made of the project accessors, the stage 1 blocks accessors
 * and the dependencies accessors.
 *
 * Each part is computed on its own, so a failure of one part, e.g. the stage 1 blocks accessors when the root
 * project failed to configure, still leaves the other parts, and in particular the project accessors, in the model.
 */
internal
fun ProjectInternal.accessorsClassPathOf(classPath: ClassPath): AccessorsClassPath {
    val exceptionCollector = serviceOf<ClassPathModeExceptionCollector>()
    val projectAccessors = exceptionCollector.runCatching {
        serviceOf<ProjectAccessorsClassPathGenerator>().projectAccessorsClassPath(this, classPath)
    } ?: AccessorsClassPath.empty
    val stage1BlocksAccessors = exceptionCollector.runCatching {
        serviceOf<Stage1BlocksAccessorClassPathGenerator>().stage1BlocksAccessorClassPath(this)
    } ?: AccessorsClassPath.empty
    val dependenciesAccessors = exceptionCollector.runCatching {
        serviceOf<DependenciesAccessors>().let { AccessorsClassPath(it.classes, it.sources) }
    } ?: AccessorsClassPath.empty
    return projectAccessors + stage1BlocksAccessors + dependenciesAccessors
}


internal
fun SettingsInternal.accessorsClassPathOf(classPath: ClassPath): AccessorsClassPath =
    serviceOf<ClassPathModeExceptionCollector>().runCatching {
        serviceOf<ProjectAccessorsClassPathGenerator>().projectAccessorsClassPath(this, classPath)
    } ?: AccessorsClassPath.empty


private
fun initScriptModelBuilder(scriptFile: File, project: ProjectInternal) = project.run {

    val (scriptHandler, scriptClassPath) = compilationClassPathForScriptPluginOf(
        target = gradle,
        scriptFile = scriptFile,
        baseScope = gradle.classLoaderScope,
        scriptHandlerFactory = scriptHandlerFactoryOf(gradle),
        project = project,
        resourceDescription = "initialization script"
    )

    KotlinScriptTargetModelBuilder(
        scriptFile = scriptFile,
        project = project,
        scriptClassPath = ResolvedClassPath(scriptClassPath),
        sourceLookupScriptHandlers = listOf(scriptHandler)
    )
}


private
fun settingsScriptModelBuilder(scriptFile: File, project: Project) = project.run {

    KotlinScriptTargetModelBuilder(
        scriptFile = scriptFile,
        project = project,
        scriptClassPath = ResolvedClassPath(settings.scriptCompilationClassPath),
        accessorsClassPath = { settings.accessorsClassPathOf(it) },
        sourceLookupScriptHandlers = listOf(settings.buildscript),
        enclosingScriptProjectDir = rootDir
    )
}


private
fun settingsScriptPluginModelBuilder(scriptFile: File, project: ProjectInternal) = project.run {

    val (scriptHandler, scriptClassPath) = compilationClassPathForScriptPluginOf(
        target = settings,
        scriptFile = scriptFile,
        baseScope = settings.baseClassLoaderScope,
        scriptHandlerFactory = scriptHandlerFactoryOf(gradle),
        project = project,
        resourceDescription = "settings file"
    )

    KotlinScriptTargetModelBuilder(
        scriptFile = scriptFile,
        project = project,
        scriptClassPath = ResolvedClassPath(scriptClassPath),
        sourceLookupScriptHandlers = listOf(scriptHandler, settings.buildscript)
    )
}


private
fun projectScriptPluginModelBuilder(scriptFile: File, project: ProjectInternal) = project.run {

    val (scriptHandler, scriptClassPath) = compilationClassPathForScriptPluginOf(
        target = project,
        scriptFile = scriptFile,
        baseScope = rootProject.baseClassLoaderScope,
        scriptHandlerFactory = scriptHandlerFactoryOf(project),
        project = project,
        resourceDescription = "build file"
    )

    KotlinScriptTargetModelBuilder(
        scriptFile = scriptFile,
        project = project,
        scriptClassPath = ResolvedClassPath(scriptClassPath),
        sourceLookupScriptHandlers = listOf(scriptHandler, buildscript)
    )
}


internal
fun compilationClassPathForScriptPluginOf(
    target: Any,
    scriptFile: File,
    baseScope: ClassLoaderScope,
    scriptHandlerFactory: ScriptHandlerFactory,
    project: ProjectInternal,
    resourceDescription: String
): Pair<ScriptHandlerInternal, ClassPath> {

    val scriptSource = textResourceScriptSource(resourceDescription, scriptFile, project.serviceOf())
    val scriptScope = baseScope.createChild("model-${scriptFile.toURI()}", null)
    val scriptHandler = scriptHandlerFactory.create(scriptSource, scriptScope)

    kotlinScriptFactoryOf(project).evaluate(
        target = target,
        scriptSource = scriptSource,
        scriptHandler = scriptHandler,
        targetScope = scriptScope,
        baseScope = baseScope,
        topLevelScript = false,
        options = EnumSet.of(EvalOption.IgnoreErrors, EvalOption.SkipBody)
    )

    return scriptHandler to project.compilationClassPathOf(scriptScope)
}


private
fun kotlinScriptFactoryOf(project: ProjectInternal) =
    project.serviceOf<KotlinScriptEvaluator>()


private
fun scriptHandlerFactoryOf(project: ProjectInternal) =
    project.serviceOf<ScriptHandlerFactory>()


internal
fun scriptHandlerFactoryOf(gradle: Gradle) =
    gradle.serviceOf<ScriptHandlerFactory>()


private
fun textResourceScriptSource(description: String, scriptFile: File, resourceLoader: TextFileResourceLoader) =
    TextResourceScriptSource(resourceLoader.loadFile(description, scriptFile))


private
fun sourceLookupScriptHandlersFor(project: ProjectInternal) =
    buildList {
        var current: ProjectState? = project.owner
        while (current != null) {
            add(current.mutableModelEvenAfterFailure.buildscript)
            current = current.parent
        }
    }


private
data class KotlinScriptTargetModelBuilder(
    val scriptFile: File?,
    val project: Project,
    val scriptClassPath: ResolvedClassPath,
    val accessorsClassPath: (ClassPath) -> AccessorsClassPath = { AccessorsClassPath.empty },
    val sourceLookupScriptHandlers: List<ScriptHandler> = emptyList(),
    val enclosingScriptProjectDir: File? = null,
    val additionalImports: () -> List<String> = { emptyList() }
) {

    fun buildModel(): ScriptModelResult<KotlinBuildScriptModel> =
        ScriptModelResult(buildScriptModel(), listOfNotNull(scriptClassPath.failure))

    private
    fun buildScriptModel(): KotlinBuildScriptModel {
        val classpathSources = sourcePathFor(sourceLookupScriptHandlers)
        val classPathModeExceptionCollector = project.serviceOf<ClassPathModeExceptionCollector>()
        val accessorsClassPath = accessorsClassPath(scriptClassPath.classPath)

        val additionalImports =
            classPathModeExceptionCollector.runCatching {
                additionalImports()
            } ?: emptyList()

        val exceptions = classPathModeExceptionCollector.exceptions
        return StandardKotlinBuildScriptModel(
            (scriptClassPath.classPath + accessorsClassPath.bin).asFiles,
            (gradleSource() + classpathSources + accessorsClassPath.src).asFiles,
            project.scriptImplicitImports + additionalImports,
            buildEditorReportsFor(exceptions),
            getExceptionsForFile(exceptions, this.scriptFile),
            enclosingScriptProjectDir
        )
    }


    private
    fun getExceptionsForFile(exceptions: List<Exception>, scriptFile: File?): List<String> {
        return if (scriptFile == null)
            emptyList()
        else
            exceptions.asSequence().runtimeFailuresLocatedIn(scriptFile.path).map(::exceptionToString).toList()
    }

    private
    fun gradleSource() =
        SourcePathProvider.sourcePathFor(
            scriptClassPath.classPath,
            scriptFile,
            project.rootDir,
            project.gradle.gradleHomeDir,
            SourceDistributionResolver(project)
        )

    private
    fun buildEditorReportsFor(exceptions: List<Exception>) =
        buildEditorReportsFor(
            scriptFile,
            exceptions
        )

    private
    fun exceptionToString(exception: Exception) =
        StringWriter().also { exception.printStackTrace(PrintWriter(it)) }.toString()
}


internal
val Settings.scriptCompilationClassPath
    get() = serviceOf<KotlinScriptClassPathProvider>().safeCompilationClassPathOf(classLoaderScope, false) {
        (this as SettingsInternal).gradle
    }


private
val Settings.classLoaderScope
    get() = (this as SettingsInternal).classLoaderScope


internal
val Project.settings
    get() = (gradle as GradleInternal).settings


internal
val Project.scriptCompilationClassPath
    get() = compilationClassPathOf((this as ProjectInternal).classLoaderScope)


private
fun Project.compilationClassPathOf(classLoaderScope: ClassLoaderScope) =
    serviceOf<KotlinScriptClassPathProvider>().safeCompilationClassPathOf(classLoaderScope, true) {
        (this as ProjectInternal).gradle
    }


private
inline fun KotlinScriptClassPathProvider.safeCompilationClassPathOf(
    classLoaderScope: ClassLoaderScope,
    projectScript: Boolean,
    getGradle: () -> GradleInternal
): ClassPath = try {
    compilationClassPathOf(classLoaderScope)
} catch (error: Exception) {
    getGradle().run {
        serviceOf<ClassPathModeExceptionCollector>().collect(error)
        compilationClassPathOf(if (projectScript) baseProjectClassLoaderScope() else this.classLoaderScope)
    }
}


internal
val Project.scriptImplicitImports
    get() = serviceOf<ImplicitImports>().list


internal
fun canonicalFile(path: String): File =
    File(path).canonicalFile
