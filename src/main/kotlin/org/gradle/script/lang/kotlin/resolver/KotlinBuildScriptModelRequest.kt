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

import org.gradle.script.lang.kotlin.provider.KotlinScriptPluginFactory

import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ModelBuilder
import org.gradle.tooling.ProjectConnection

import java.io.File
import java.net.URI


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


internal
fun fetchKotlinBuildScriptModelFor(
    request: KotlinBuildScriptModelRequest,
    modelBuilderCustomization: ModelBuilder<KotlinBuildScriptModel>.() -> Unit = {}): KotlinBuildScriptModel? =

    withConnectionFrom(connectorFor(request)) {
        model(KotlinBuildScriptModel::class.java)?.run {
            setJavaHome(request.javaHome)
            setJvmArguments(request.jvmOptions + modelSpecificJvmOptions)
            request.scriptFile?.let {
                withArguments(request.options + "-P$kotlinBuildScriptModelTarget=${it.canonicalPath}")
            } ?: withArguments(request.options)
            modelBuilderCustomization()
            get()
        }
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


internal
inline fun <T> withConnectionFrom(connector: GradleConnector, block: ProjectConnection.() -> T): T =
    connector.connect().use(block)


internal
inline fun <T> ProjectConnection.use(block: (ProjectConnection) -> T): T {
    try {
        return block(this)
    } finally {
        close()
    }
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

