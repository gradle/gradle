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

package org.gradle.kotlin.dsl.resolver

import com.google.common.annotations.VisibleForTesting
import org.gradle.kotlin.dsl.support.KotlinScriptType
import org.gradle.kotlin.dsl.support.isParentOf
import org.gradle.kotlin.dsl.support.kotlinScriptTypeFor
import org.gradle.kotlin.dsl.tooling.models.KotlinBuildScriptModel

import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ModelBuilder
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.internal.consumer.DefaultGradleConnector
import org.gradle.tooling.model.kotlin.dsl.KotlinDslModelsParameters

import java.io.File
import java.util.function.Function


@VisibleForTesting
sealed class GradleInstallation {

    data class Local(val dir: File) : GradleInstallation()

    data class Remote(val uri: java.net.URI) : GradleInstallation()

    data class Version(val number: String) : GradleInstallation()

    object Wrapper : GradleInstallation()

    object Embedded : GradleInstallation()
}


@VisibleForTesting
data class KotlinBuildScriptModelRequest(
    val projectDir: File,
    val scriptFile: File? = null,
    val gradleInstallation: GradleInstallation = GradleInstallation.Wrapper,
    val gradleUserHome: File? = null,
    val javaHome: File? = null,
    val options: List<String> = emptyList(),
    val jvmOptions: List<String> = emptyList(),
    val environmentVariables: Map<String, String> = emptyMap(),
    val correlationId: String = newCorrelationId()
)


internal
fun newCorrelationId() = System.nanoTime().toString()


internal
typealias ModelBuilderCustomization = ModelBuilder<KotlinBuildScriptModel>.() -> Unit


internal
fun fetchKotlinBuildScriptModelFor(request: KotlinBuildScriptModelRequest): KotlinBuildScriptModel =
    fetchKotlinBuildScriptModelFor(
        request.toFetchParametersWith {
            setJavaHome(request.javaHome)
        }
    )


@VisibleForTesting
fun fetchKotlinBuildScriptModelFor(
    importedProjectDir: File,
    scriptFile: File?,
    connectorForProject: Function<File, GradleConnector>
): KotlinBuildScriptModel =

    fetchKotlinBuildScriptModelFor(FetchParameters(importedProjectDir, scriptFile, connectorForProject))


private
data class FetchParameters(
    val importedProjectDir: File,
    val scriptFile: File?,
    val connectorForProject: Function<File, GradleConnector>,
    val options: List<String> = emptyList(),
    val jvmOptions: List<String> = emptyList(),
    val environmentVariables: Map<String, String> = emptyMap(),
    val correlationId: String = newCorrelationId(),
    val modelBuilderCustomization: ModelBuilderCustomization = {}
)


private
fun KotlinBuildScriptModelRequest.toFetchParametersWith(modelBuilderCustomization: ModelBuilderCustomization) =
    FetchParameters(
        projectDir,
        scriptFile,
        Function { projectDir -> connectorFor(this).forProjectDirectory(projectDir) },
        options,
        jvmOptions,
        environmentVariables,
        correlationId,
        modelBuilderCustomization
    )


private
fun connectorFor(request: KotlinBuildScriptModelRequest): GradleConnector =
    GradleConnector.newConnector()
        .useGradleFrom(request.gradleInstallation)
        .useGradleUserHomeDir(request.gradleUserHome)


private
fun GradleConnector.useGradleFrom(gradleInstallation: GradleInstallation): GradleConnector =
    gradleInstallation.run {
        when (this) {
            is GradleInstallation.Local -> useInstallation(dir)
            is GradleInstallation.Remote -> useDistribution(uri)
            is GradleInstallation.Version -> useGradleVersion(number)
            is GradleInstallation.Embedded -> (this@useGradleFrom as DefaultGradleConnector).apply {
                embedded(true)
                useClasspathDistribution()
            }
            GradleInstallation.Wrapper -> useBuildDistribution()
        }
    }


private
fun fetchKotlinBuildScriptModelFor(parameters: FetchParameters): KotlinBuildScriptModel {

    val projectDir = parameters.importedProjectDir
    val scriptFile = parameters.scriptFile
        ?: return fetchKotlinBuildScriptModelFrom(projectDir, parameters)

    val effectiveProjectDir = buildSrcProjectDirOf(scriptFile, projectDir)
        ?: projectDir

    val scriptModel = fetchKotlinBuildScriptModelFrom(effectiveProjectDir, parameters)
    if (scriptModel.enclosingScriptProjectDir == null && hasProjectDependentClassPath(scriptFile)) {
        val externalProjectRoot = projectRootOf(scriptFile, projectDir)
        if (externalProjectRoot != projectDir) {
            return fetchKotlinBuildScriptModelFrom(externalProjectRoot, parameters)
        }
    }
    return scriptModel
}


private
fun fetchKotlinBuildScriptModelFrom(
    projectDir: File,
    parameters: FetchParameters
): KotlinBuildScriptModel =

    connectionForProjectDir(projectDir, parameters).let { connection ->
        @Suppress("ConvertTryFinallyToUseCall")
        try {
            connection.modelBuilderFor(parameters).apply(parameters.modelBuilderCustomization).get()
        } finally {
            connection.close()
        }
    }


private
fun connectionForProjectDir(projectDir: File, parameters: FetchParameters): ProjectConnection =
    parameters.connectorForProject.apply(projectDir).connect()


private
fun ProjectConnection.modelBuilderFor(parameters: FetchParameters) =
    model(KotlinBuildScriptModel::class.java).apply {
        setEnvironmentVariables(parameters.environmentVariables.takeIf { it.isNotEmpty() })
        setJvmArguments(parameters.jvmOptions + KotlinDslModelsParameters.CLASSPATH_MODE_SYSTEM_PROPERTY_DECLARATION)
        forTasks(KotlinDslModelsParameters.PREPARATION_TASK_NAME)

        val arguments = parameters.options.toMutableList()
        arguments += "-P${KotlinDslModelsParameters.CORRELATION_ID_GRADLE_PROPERTY_NAME}=${parameters.correlationId}"

        parameters.scriptFile?.let {
            arguments += "-P${KotlinBuildScriptModel.SCRIPT_GRADLE_PROPERTY_NAME}=${it.canonicalPath}"
        }

        withArguments(arguments)
    }


private
fun buildSrcProjectDirOf(scriptFile: File, importedProjectDir: File): File? =
    importedProjectDir.resolve("buildSrc").takeIf { buildSrc ->
        buildSrc.isDirectory && buildSrc.isParentOf(scriptFile)
    }


private
fun hasProjectDependentClassPath(scriptFile: File): Boolean =
    when (kotlinScriptTypeFor(scriptFile)) {
        KotlinScriptType.INIT -> false
        else -> true
    }


internal
fun projectRootOf(scriptFile: File, importedProjectRoot: File, stopAt: File? = null): File {

    // TODO remove hardcoded reference to settings.gradle once there's a public TAPI client api for that
    fun isProjectRoot(dir: File) =
        File(dir, "settings.gradle.kts").isFile
            || File(dir, "settings.gradle").isFile
            || dir.name == "buildSrc"

    tailrec fun test(dir: File): File =
        when {
            dir == importedProjectRoot -> importedProjectRoot
            isProjectRoot(dir) -> dir
            else -> {
                when (val parentDir = dir.parentFile) {
                    null, dir, stopAt -> scriptFile.parentFile // external project
                    else -> test(parentDir)
                }
            }
        }

    return test(scriptFile.parentFile)
}
