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

import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection

import org.gradle.internal.classpath.ClassPath

import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.script.KotlinScriptExternalDependencies
import org.jetbrains.kotlin.script.ScriptDependenciesResolver

import java.io.File

import java.util.Properties

class GradleKotlinScriptDependenciesResolver : ScriptDependenciesResolver {

    override fun resolve(projectRoot: File?, scriptFile: File?,
                         annotations: Iterable<KtAnnotationEntry>, context: Any?): KotlinScriptExternalDependencies? =
        when (context) {
            is ClassPath -> makeDependencies(context.asFiles) // Gradle compilation path
            is Map<*, *> -> retrieveDependenciesFromProject(projectRoot!!, gradleHomeOf(context)) // IDEA content assist path
            else -> null
        }

    private fun retrieveDependenciesFromProject(projectRoot: File,
                                                gradleInstallation: File): KotlinScriptExternalDependencies {
        return withConnectionFrom(connectorFor(gradleInstallation, projectRoot)) {
            model(KotlinBuildScriptModel::class.java)
                .setJavaHome(javaHomeForDaemonOf(projectRoot))
                .get()
                .classPath
                .let {
                    val gradleScriptKotlinJar = it.filter { it.name.startsWith("gradle-script-kotlin-") }
                    makeDependencies(
                        classPath = it,
                        sources = gradleScriptKotlinJar + sourceRootsOf(gradleInstallation))
                }
        }
    }

    private fun sourceRootsOf(gradleInstallation: File): Collection<File> =
        File(gradleInstallation, "src").let { srcDir ->
            if (srcDir.exists())
                srcDir.listFiles().filter { it.isDirectory }
            else
                emptyList()
        }

    private fun gradleHomeOf(context: Map<*, *>) =
        context["gradleHome"] as File

    private fun makeDependencies(classPath: Iterable<File>, sources: Iterable<File> = emptyList()): KotlinScriptExternalDependencies =
        object : KotlinScriptExternalDependencies {
            override val classpath = classPath
            override val imports = implicitImports
            override val sources = sources
        }

    companion object {
        val implicitImports = listOf(
            "org.gradle.api.plugins.*",
            "org.gradle.script.lang.kotlin.*")
    }
}

private fun javaHomeForDaemonOf(projectRoot: File): File =
    File(daemonPropertiesOf(projectRoot)["java.home"] as String)

private fun daemonPropertiesOf(projectRoot: File): Properties =
    daemonPropertiesFileOf(projectRoot).inputStream().use { input ->
        Properties().apply {
            load(input)
        }
    }

private fun connectorFor(gradleInstallation: File, projectRoot: File) =
    GradleConnector.newConnector().forProjectDirectory(projectRoot).useInstallation(gradleInstallation)

inline fun <T> withConnectionFrom(connector: GradleConnector, block: ProjectConnection.() -> T): T =
    connector.connect().use(block)

inline fun <T> ProjectConnection.use(block: (ProjectConnection) -> T): T {
    try {
        return block(this)
    } finally {
        close()
    }
}
