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
import org.gradle.kotlin.dsl.precompile.PrecompiledScriptDependenciesResolver
import org.gradle.kotlin.dsl.provider.ClassPathModeExceptionCollector
import org.gradle.kotlin.dsl.provider.KotlinScriptClassPathProvider
import org.gradle.kotlin.dsl.provider.KotlinScriptEvaluator
import org.gradle.kotlin.dsl.provider.ignoringErrors
import org.gradle.kotlin.dsl.resolver.EditorReports
import org.gradle.kotlin.dsl.resolver.SourceDistributionResolver
import org.gradle.kotlin.dsl.resolver.SourcePathProvider
import org.gradle.kotlin.dsl.support.ImplicitImports
import org.gradle.kotlin.dsl.support.KotlinScriptType
import org.gradle.kotlin.dsl.support.kotlinScriptTypeFor
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.kotlin.dsl.tooling.models.EditorReport
import org.gradle.kotlin.dsl.tooling.models.KotlinBuildScriptModel
import org.gradle.tooling.model.kotlin.dsl.KotlinDslModelsParameters
import org.gradle.tooling.provider.model.ToolingModelBuilder
import java.io.File
import java.io.PrintWriter
import java.io.Serializable
import java.io.StringWriter
import java.util.EnumSet


internal
data class KotlinBuildScriptModelParameter(
    val scriptFile: File?,
    val correlationId: String?
)


internal
data class StandardKotlinBuildScriptModel(
    private val classPath: List<File>,
    private val sourcePath: List<File>,
    private val implicitImports: List<String>,
    private val editorReports: List<EditorReport>,
    private val exceptions: List<String>,
    private val enclosingScriptProjectDir: File?
) : KotlinBuildScriptModel, Serializable {

    override fun getClassPath() = classPath

    override fun getSourcePath() = sourcePath

    override fun getImplicitImports() = implicitImports

    override fun getEditorReports() = editorReports

    override fun getExceptions() = exceptions

    override fun getEnclosingScriptProjectDir() = enclosingScriptProjectDir
}


internal
object KotlinBuildScriptModelBuilder : ToolingModelBuilder {

    override fun canBuild(modelName: String): Boolean =
        modelName == "org.gradle.kotlin.dsl.tooling.models.KotlinBuildScriptModel"

    override fun buildAll(modelName: String, modelRequestProject: Project): KotlinBuildScriptModel {
        val timer = startTimer()
        val parameter = requestParameterOf(modelRequestProject)
        try {
            return kotlinBuildScriptModelFor(modelRequestProject, parameter).also {
                log("$parameter => $it")
            }
        } catch (e: Exception) {
            log("$parameter => $e")
            throw e
        } finally {
            log("MODEL built in ${timer.elapsed}.")
        }
    }

    internal
    fun kotlinBuildScriptModelFor(modelRequestProject: Project, parameter: KotlinBuildScriptModelParameter) =
        scriptModelBuilderFor(modelRequestProject as ProjectInternal, parameter).buildModel()

    private
    fun scriptModelBuilderFor(
        modelRequestProject: ProjectInternal,
        parameter: KotlinBuildScriptModelParameter
    ): KotlinScriptTargetModelBuilder {

        val scriptFile = parameter.scriptFile
            ?: return projectScriptModelBuilder(null, modelRequestProject)

        modelRequestProject.findProjectWithBuildFile(scriptFile)?.let { buildFileProject ->
            return projectScriptModelBuilder(scriptFile, buildFileProject as ProjectInternal)
        }

        modelRequestProject.enclosingSourceSetOf(scriptFile)?.let { enclosingSourceSet ->
            return precompiledScriptPluginModelBuilder(scriptFile, enclosingSourceSet, modelRequestProject)
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
            System.getProperty(KotlinDslModelsParameters.CORRELATION_ID_SYSTEM_PROPERTY_NAME)
        )
}


internal
fun log(message: String) {
    if (System.getProperty("org.gradle.kotlin.dsl.logging.tapi") == "true") {
        println(message)
    }
}


private
fun Project.findProjectWithBuildFile(file: File) =
    allprojects.find { it.buildFile == file }


private
fun Project.enclosingSourceSetOf(file: File): EnclosingSourceSet? =
    findSourceSetOf(file)
        ?: findSourceSetOfFileIn(subprojects, file)


private
data class EnclosingSourceSet(val project: Project, val sourceSet: SourceSet)


private
fun findSourceSetOfFileIn(projects: Iterable<Project>, file: File): EnclosingSourceSet? =
    projects
        .asSequence()
        .mapNotNull { it.findSourceSetOf(file) }
        .firstOrNull()


private
fun Project.findSourceSetOf(file: File): EnclosingSourceSet? =
    sourceSets?.find { file in it.allSource }?.let {
        EnclosingSourceSet(this, it)
    }


private
val Project.sourceSets
    get() = extensions.findByType(typeOf<SourceSetContainer>())


private
fun precompiledScriptPluginModelBuilder(
    scriptFile: File,
    enclosingSourceSet: EnclosingSourceSet,
    modelRequestProject: Project
) = KotlinScriptTargetModelBuilder(
    scriptFile = scriptFile,
    project = modelRequestProject,
    scriptClassPath = DefaultClassPath.of(enclosingSourceSet.sourceSet.compileClasspath),
    enclosingScriptProjectDir = enclosingSourceSet.project.projectDir,
    additionalImports = {
        enclosingSourceSet.project.precompiledScriptPluginsMetadataDir.run {
            implicitImportsFrom(
                resolve("accessors").resolve(hashOf(scriptFile))
            ) + implicitImportsFrom(
                resolve("plugin-spec-builders").resolve("implicit-imports")
            )
        }
    }
)


private
val Project.precompiledScriptPluginsMetadataDir: File
    get() = layout.buildDirectory.dir("kotlin-dsl/precompiled-script-plugins-metadata").get().asFile


private
fun implicitImportsFrom(file: File): List<String> =
    file.takeIf { it.isFile }?.readLines() ?: emptyList()


private
fun hashOf(scriptFile: File) =
    PrecompiledScriptDependenciesResolver.hashOf(scriptFile.readText())


private
fun projectScriptModelBuilder(
    scriptFile: File?,
    project: ProjectInternal
) = KotlinScriptTargetModelBuilder(
    scriptFile = scriptFile,
    project = project,
    scriptClassPath = project.scriptCompilationClassPath,
    accessorsClassPath = { classPath ->
        val stage1BlocksAccessorClassPathGenerator = project.serviceOf<Stage1BlocksAccessorClassPathGenerator>()
        val projectAccessorClassPathGenerator = project.serviceOf<ProjectAccessorsClassPathGenerator>()
        val dependenciesAccessors = project.serviceOf<DependenciesAccessors>()
        projectAccessorClassPathGenerator.projectAccessorsClassPath(project, classPath) + stage1BlocksAccessorClassPathGenerator.stage1BlocksAccessorClassPath(project) + AccessorsClassPath(dependenciesAccessors.classes, dependenciesAccessors.sources)
    },
    sourceLookupScriptHandlers = sourceLookupScriptHandlersFor(project),
    enclosingScriptProjectDir = project.projectDir
)


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
        scriptClassPath = scriptClassPath,
        sourceLookupScriptHandlers = listOf(scriptHandler)
    )
}


private
fun settingsScriptModelBuilder(scriptFile: File, project: Project) = project.run {

    KotlinScriptTargetModelBuilder(
        scriptFile = scriptFile,
        project = project,
        scriptClassPath = settings.scriptCompilationClassPath,
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
        scriptClassPath = scriptClassPath,
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
        scriptClassPath = scriptClassPath,
        sourceLookupScriptHandlers = listOf(scriptHandler, buildscript)
    )
}


private
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


private
fun scriptHandlerFactoryOf(gradle: Gradle) =
    gradle.serviceOf<ScriptHandlerFactory>()


private
fun textResourceScriptSource(description: String, scriptFile: File, resourceLoader: TextFileResourceLoader) =
    TextResourceScriptSource(resourceLoader.loadFile(description, scriptFile))


private
fun sourceLookupScriptHandlersFor(project: Project) =
    project.hierarchy.map { it.buildscript }.toList()


private
data class KotlinScriptTargetModelBuilder(
    val scriptFile: File?,
    val project: Project,
    val scriptClassPath: ClassPath,
    val accessorsClassPath: (ClassPath) -> AccessorsClassPath = { AccessorsClassPath.empty },
    val sourceLookupScriptHandlers: List<ScriptHandler> = emptyList(),
    val enclosingScriptProjectDir: File? = null,
    val additionalImports: () -> List<String> = { emptyList() }
) {

    fun buildModel(): KotlinBuildScriptModel {
        val classpathSources = sourcePathFor(sourceLookupScriptHandlers)
        val classPathModeExceptionCollector = project.serviceOf<ClassPathModeExceptionCollector>()
        val accessorsClassPath =
            classPathModeExceptionCollector.ignoringErrors {
                accessorsClassPath(scriptClassPath)
            } ?: AccessorsClassPath.empty

        val additionalImports =
            classPathModeExceptionCollector.ignoringErrors {
                additionalImports()
            } ?: emptyList()

        val exceptions = classPathModeExceptionCollector.exceptions
        return StandardKotlinBuildScriptModel(
            (scriptClassPath + accessorsClassPath.bin).asFiles,
            (gradleSource() + classpathSources + accessorsClassPath.src).asFiles,
            implicitImports + additionalImports,
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
            scriptClassPath,
            scriptFile,
            rootDir,
            gradleHomeDir,
            SourceDistributionResolver(project)
        )

    val gradleHomeDir
        get() = project.gradle.gradleHomeDir

    val rootDir
        get() = project.rootDir

    val implicitImports
        get() = project.scriptImplicitImports

    private
    fun buildEditorReportsFor(exceptions: List<Exception>) =
        buildEditorReportsFor(
            scriptFile,
            exceptions,
            project.isLocationAwareEditorHintsEnabled
        )

    private
    fun exceptionToString(exception: Exception) =
        StringWriter().also { exception.printStackTrace(PrintWriter(it)) }.toString()
}


private
val Settings.scriptCompilationClassPath
    get() = serviceOf<KotlinScriptClassPathProvider>().safeCompilationClassPathOf(classLoaderScope, false) {
        (this as SettingsInternal).gradle
    }


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


private
val Project.scriptImplicitImports
    get() = serviceOf<ImplicitImports>().list


private
val Project.hierarchy: Sequence<Project>
    get() = sequence {
        var project = this@hierarchy
        yield(project)
        while (project != project.rootProject) {
            project = project.parent!!
            yield(project)
        }
    }


private
val Project.isLocationAwareEditorHintsEnabled: Boolean
    get() = findProperty(EditorReports.locationAwareEditorHintsPropertyName) == "true"


internal
fun canonicalFile(path: String): File =
    File(path).canonicalFile
