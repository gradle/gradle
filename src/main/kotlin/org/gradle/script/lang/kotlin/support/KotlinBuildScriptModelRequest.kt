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

package org.gradle.script.lang.kotlin.support

import org.gradle.script.lang.kotlin.provider.KotlinScriptPluginFactory

import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection

import java.io.File
import java.net.URI

internal
sealed class GradleInstallation {
    abstract fun apply(connector: GradleConnector): GradleConnector

    data class Local(val dir: File) : GradleInstallation() {
        override fun apply(connector: GradleConnector) = connector.useInstallation(dir)!!
    }
    data class Remote(val uri: URI) : GradleInstallation() {
        override fun apply(connector: GradleConnector) = connector.useDistribution(uri)!!
    }

    data class Version(val number: String): GradleInstallation() {
        override fun apply(connector: GradleConnector) = connector.useGradleVersion(number)!!
    }

    object Wrapper : GradleInstallation() {
        override fun apply(connector: GradleConnector) = connector.useBuildDistribution()!!
    }
}

internal
data class KotlinBuildScriptModelRequest(
    val projectDir: File,
    val gradleInstallation: GradleInstallation = GradleInstallation.Wrapper,
    val scriptFile: File? = null,
    val gradleUserHome: File? = null,
    val javaHome: File? = null,
    val options: List<String> = emptyList(),
    val jvmOptions: List<String> = emptyList())


internal
fun fetchKotlinBuildScriptModelFor(request: KotlinBuildScriptModelRequest): KotlinBuildScriptModel? =
    withConnectionFrom(connectorFor(request)) {
        model(KotlinBuildScriptModel::class.java)?.run {
            setJavaHome(request.javaHome)
            setJvmArguments(request.jvmOptions + modelSpecificJvmOptions)
            request.scriptFile?.let {
                withArguments(request.options + "-P$kotlinBuildScriptModelTarget=${it.canonicalPath}")
            } ?: withArguments(request.options)
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
    request.gradleInstallation.apply(GradleConnector.newConnector().forProjectDirectory(request.projectDir).useGradleUserHomeDir(request.gradleUserHome))


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
