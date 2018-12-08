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
import org.gradle.kotlin.dsl.support.isParentOf
import org.gradle.kotlin.dsl.tooling.models.KotlinBuildScriptModel

import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ModelBuilder
import org.gradle.tooling.ProjectConnection

import java.io.File


internal
sealed class GradleInstallation {

    data class Local(val dir: java.io.File) : GradleInstallation()

    data class Remote(val uri: java.net.URI) : GradleInstallation()

    data class Version(val number: String) : GradleInstallation()

    object Wrapper : GradleInstallation()
}


internal
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


internal
fun fetchKotlinBuildScriptModelFor(
    request: KotlinBuildScriptModelRequest,
    modelBuilderCustomization: ModelBuilderCustomization = {}
): KotlinBuildScriptModel =

    projectConnectionFor(request).let { connection ->
        @Suppress("ConvertTryFinallyToUseCall")
        try {
            connection.modelBuilderFor(request).apply(modelBuilderCustomization).get()
        } finally {
            connection.close()
        }
    }


private
fun projectConnectionFor(request: KotlinBuildScriptModelRequest): ProjectConnection =
    connectorFor(request).connect()


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
fun connectorFor(request: KotlinBuildScriptModelRequest): GradleConnector =
    connectorFor(projectRootFor(request.scriptFile, request.projectDir), request.gradleInstallation)
        .useGradleUserHomeDir(request.gradleUserHome)


private
fun projectRootFor(scriptFile: File?, projectDir: File): File = scriptFile?.let {
    projectDir.resolve("buildSrc").takeIf { buildSrc ->
        buildSrc.isDirectory && buildSrc.isParentOf(scriptFile)
    }
} ?: projectDir


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
