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

import org.gradle.internal.classpath.ClassPath

import org.jetbrains.kotlin.script.KotlinScriptExternalDependencies
import org.jetbrains.kotlin.script.ScriptContents
import org.jetbrains.kotlin.script.ScriptDependenciesResolverEx

import java.io.File

class GradleKotlinScriptDependenciesResolver : ScriptDependenciesResolverEx {

    override fun resolve(script: ScriptContents, environment: Map<String, Any?>?, previousDependencies: KotlinScriptExternalDependencies?): KotlinScriptExternalDependencies? {
        if (environment == null)
            return previousDependencies

        // Gradle compilation path
        val classPath = environment["classPath"] as? ClassPath
        if (classPath != null)
            return makeDependencies(classPath.asFiles)

        // IDEA content assist path
        val projectRoot = environment["projectRoot"] as? File
        val gradleHome = environment["gradleHome"] as? File
        val gradleJavaHome = environment["gradleJavaHome"] as? String
        if (gradleHome != null && projectRoot != null && gradleJavaHome != null)
            return retrieveDependenciesFromProject(projectRoot, gradleHome, File(gradleJavaHome))

        return previousDependencies
    }

    private fun retrieveDependenciesFromProject(projectRoot: File, gradleInstallation: File, javaHome: File) =
        retrieveKotlinBuildScriptModelFrom(projectRoot, gradleInstallation, javaHome)
            .classPath
            .let {
                val gradleScriptKotlinJar = it.filter { it.name.startsWith("gradle-script-kotlin-") }
                makeDependencies(
                    classPath = it,
                    sources = gradleScriptKotlinJar + buildSrcRootsOf(projectRoot) + sourceRootsOf(gradleInstallation))
            }

    /**
     * Returns all conventional source directories under buildSrc if any.
     *
     * This won't work for buildSrc projects with a custom source directory layout
     * but should account for the majority of cases and it's cheap.
     */
    private fun buildSrcRootsOf(projectRoot: File): Collection<File> =
        subDirsOf(File(projectRoot, "buildSrc/src/main"))

    private fun sourceRootsOf(gradleInstallation: File): Collection<File> =
        subDirsOf(File(gradleInstallation, "src"))

    private fun subDirsOf(dir: File): Collection<File> =
        if (dir.isDirectory)
            dir.listFiles().filter { it.isDirectory }
        else
            emptyList()

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

fun retrieveKotlinBuildScriptModelFrom(projectDir: File, gradleInstallation: File, javaHome: File? = null): KotlinBuildScriptModel =
    withConnectionFrom(connectorFor(projectDir, gradleInstallation)) {
        model(KotlinBuildScriptModel::class.java)
            .setJavaHome(javaHome)
            .setJvmArguments("-D${KotlinScriptPluginFactory.modeSystemPropertyName}=${KotlinScriptPluginFactory.classPathMode}")
            .get()
    }

fun connectorFor(projectDir: File, gradleInstallation: File): GradleConnector =
    GradleConnector.newConnector().forProjectDirectory(projectDir).useInstallation(gradleInstallation)

inline fun <T> withConnectionFrom(connector: GradleConnector, block: ProjectConnection.() -> T): T =
    connector.connect().use(block)

inline fun <T> ProjectConnection.use(block: (ProjectConnection) -> T): T {
    try {
        return block(this)
    } finally {
        close()
    }
}
