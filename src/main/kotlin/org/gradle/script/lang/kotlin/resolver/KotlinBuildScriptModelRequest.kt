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

package org.gradle.script.lang.kotlin.resolver

import org.gradle.script.lang.kotlin.concurrent.tapi
import org.gradle.script.lang.kotlin.provider.KotlinScriptPluginFactory

import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ModelBuilder
import org.gradle.tooling.ProjectConnection

import java.io.File
import java.net.URI

import kotlin.concurrent.thread


internal
sealed class GradleInstallation {

    data class Local(val dir: File) : GradleInstallation()

    data class Remote(val uri: URI) : GradleInstallation()

    data class Version(val number: String) : GradleInstallation()

    object Wrapper : GradleInstallation()
}


internal
data class KotlinBuildScriptModelRequest(
    val projectDir: File,
    val scriptFile: File? = null,
    val gradleInstallation: GradleInstallation = GradleInstallation.Wrapper,
    val gradleUserHome: File? = null,
    val javaHome: File? = null,
    val options: List<String> = emptyList(),
    val jvmOptions: List<String> = emptyList())


typealias ModelBuilderCustomization = ModelBuilder<KotlinBuildScriptModel>.() -> Unit


internal
suspend fun fetchKotlinBuildScriptModelFor(
    request: KotlinBuildScriptModelRequest,
    modelBuilderCustomization: ModelBuilderCustomization = {}): KotlinBuildScriptModel {

    val connection = projectConnectionFor(request)
    try {
        return tapi { connection.modelBuilderFor(request).apply(modelBuilderCustomization).get(it) }
    } finally {
        // Run close on a separate thread as TAPI doesn't allow closing the connection from an executor thread
        thread { connection.close() }
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
    listOf("-D${KotlinScriptPluginFactory.modeSystemPropertyName}=${KotlinScriptPluginFactory.classPathMode}")


internal
val kotlinBuildScriptModelTarget = "org.gradle.script.lang.kotlin.provider.script"


internal
fun connectorFor(request: KotlinBuildScriptModelRequest): GradleConnector =
    GradleConnector
        .newConnector()
        .forProjectDirectory(request.projectDir)
        .useGradleUserHomeDir(request.gradleUserHome)
        .let { connector ->
            applyGradleInstallationTo(connector, request)
        }


private
fun applyGradleInstallationTo(connector: GradleConnector, request: KotlinBuildScriptModelRequest): GradleConnector =
    request.gradleInstallation.run {
        when (this) {
            is GradleInstallation.Local   -> connector.useInstallation(dir)
            is GradleInstallation.Remote  -> connector.useDistribution(uri)
            is GradleInstallation.Version -> connector.useGradleVersion(number)
            GradleInstallation.Wrapper    -> connector.useBuildDistribution()
        }
    }

