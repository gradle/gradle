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

import org.gradle.kotlin.dsl.provider.KotlinDslProviderMode
import org.gradle.kotlin.dsl.support.KotlinScriptType
import org.gradle.kotlin.dsl.support.isParentOf
import org.gradle.kotlin.dsl.support.kotlinScriptTypeFor
import org.gradle.kotlin.dsl.tooling.models.KotlinBuildScriptModel

import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ModelBuilder
import org.gradle.tooling.ProjectConnection

import com.google.common.annotations.VisibleForTesting

import java.io.File


@VisibleForTesting
sealed class GradleInstallation {

    data class Local(val dir: java.io.File) : GradleInstallation()

    data class Remote(val uri: java.net.URI) : GradleInstallation()

    data class Version(val number: String) : GradleInstallation()

    object Wrapper : GradleInstallation()
}


@VisibleForTesting
data class KotlinBuildScriptModelRequest(
    val projectDir: java.io.File,
    val scriptFile: java.io.File? = null,
    val gradleInstallation: GradleInstallation = GradleInstallation.Wrapper,
    val gradleUserHome: java.io.File? = null,
    val javaHome: java.io.File? = null,
    val options: List<String> = emptyList(),
    val jvmOptions: List<String> = emptyList()
)


internal
typealias ModelBuilderCustomization = ModelBuilder<KotlinBuildScriptModel>.() -> Unit


@VisibleForTesting
fun fetchKotlinBuildScriptModelFor(
    request: KotlinBuildScriptModelRequest,
    modelBuilderCustomization: ModelBuilderCustomization = {}
): KotlinBuildScriptModel {

    val importedProjectDir = request.projectDir
    val scriptFile = request.scriptFile
        ?: return fetchKotlinBuildScriptModelFrom(importedProjectDir, request, modelBuilderCustomization)

    val effectiveProjectDir = buildSrcProjectDirOf(scriptFile, importedProjectDir)
        ?: importedProjectDir

    val scriptModel = fetchKotlinBuildScriptModelFrom(effectiveProjectDir, request, modelBuilderCustomization)
    if (scriptModel.enclosingScriptProjectDir == null && hasProjectDependentClassPath(scriptFile)) {
        val externalProjectRoot = projectRootOf(scriptFile, importedProjectDir)
        if (externalProjectRoot != importedProjectDir) {
            return fetchKotlinBuildScriptModelFrom(externalProjectRoot, request, modelBuilderCustomization)
        }
    }
    return scriptModel
}


private
fun hasProjectDependentClassPath(scriptFile: File): Boolean =
    when (kotlinScriptTypeFor(scriptFile)) {
        KotlinScriptType.INIT -> false
        else -> true
    }


private
fun fetchKotlinBuildScriptModelFrom(
    projectDir: File,
    request: KotlinBuildScriptModelRequest,
    modelBuilderCustomization: ModelBuilderCustomization
): KotlinBuildScriptModel =

    projectConnectionFor(request, projectDir).let { connection ->
        @Suppress("ConvertTryFinallyToUseCall")
        try {
            connection.modelBuilderFor(request).apply(modelBuilderCustomization).get()
        } finally {
            connection.close()
        }
    }


private
fun projectConnectionFor(request: KotlinBuildScriptModelRequest, projectDir: File): ProjectConnection =
    connectorFor(request, projectDir).connect()


private
fun ProjectConnection.modelBuilderFor(request: KotlinBuildScriptModelRequest) =
    model(KotlinBuildScriptModel::class.java).apply {
        setJavaHome(request.javaHome)
        setJvmArguments(request.jvmOptions + modelSpecificJvmOptions)
        request.scriptFile?.let {
            withArguments(request.options + "-P$kotlinBuildScriptModelTarget=${it.canonicalPath}")
        } ?: withArguments(request.options)
    }


private
val modelSpecificJvmOptions =
    listOf("-D${KotlinDslProviderMode.systemPropertyName}=${KotlinDslProviderMode.classPathMode}")


const val kotlinBuildScriptModelTarget = "org.gradle.kotlin.dsl.provider.script"


private
fun connectorFor(request: KotlinBuildScriptModelRequest, projectDir: File): GradleConnector =
    connectorFor(projectDir, request.gradleInstallation)
        .useGradleUserHomeDir(request.gradleUserHome)


private
fun buildSrcProjectDirOf(scriptFile: File, importedProjectDir: File): File? =
    importedProjectDir.resolve("buildSrc").takeIf { buildSrc ->
        buildSrc.isDirectory && buildSrc.isParentOf(scriptFile)
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
                val parentDir = dir.parentFile
                when (parentDir) {
                    null, dir, stopAt -> scriptFile.parentFile // external project
                    else -> test(parentDir)
                }
            }
        }

    return test(scriptFile.parentFile)
}


private
fun connectorFor(projectDir: File, gradleInstallation: GradleInstallation): GradleConnector =
    GradleConnector
        .newConnector()
        .forProjectDirectory(projectDir)
        .let { connector ->
            applyGradleInstallationTo(connector, gradleInstallation)
        }


private
fun applyGradleInstallationTo(connector: GradleConnector, gradleInstallation: GradleInstallation): GradleConnector =
    gradleInstallation.run {
        when (this) {
            is GradleInstallation.Local -> connector.useInstallation(dir)
            is GradleInstallation.Remote -> connector.useDistribution(uri)
            is GradleInstallation.Version -> connector.useGradleVersion(number)
            GradleInstallation.Wrapper -> connector.useBuildDistribution()
        }
    }
