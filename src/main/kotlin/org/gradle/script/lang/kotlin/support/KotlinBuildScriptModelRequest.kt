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

internal
data class KotlinBuildScriptModelRequest(
    val projectDir: File,
    val gradleInstallation: File,
    val scriptFile: File? = null,
    val javaHome: File? = null,
    val jvmOptions: List<String> = emptyList())

internal
fun fetchKotlinBuildScriptModelFor(request: KotlinBuildScriptModelRequest): KotlinBuildScriptModel? =
    withConnectionFrom(connectorFor(request.projectDir, request.gradleInstallation)) {
        model(KotlinBuildScriptModel::class.java)?.run {
            setJavaHome(request.javaHome)
            setJvmArguments(request.jvmOptions + modelSpecificJvmOptions)
            request.scriptFile?.let {
                withArguments("-P$kotlinBuildScriptModelTarget=${it.canonicalPath}")
            }
            get()
        }
    }

private
val modelSpecificJvmOptions =
    listOf("-D${KotlinScriptPluginFactory.modeSystemPropertyName}=${KotlinScriptPluginFactory.classPathMode}")

internal
val kotlinBuildScriptModelTarget = "org.gradle.script.lang.kotlin.provider.script"

internal
fun connectorFor(projectDir: File, gradleInstallation: File): GradleConnector =
    GradleConnector.newConnector().forProjectDirectory(projectDir).useInstallation(gradleInstallation)

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
