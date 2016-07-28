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

import org.gradle.internal.classpath.ClassPath
import org.gradle.script.lang.kotlin.provider.KotlinScriptPluginFactory
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.jetbrains.kotlin.script.KotlinScriptExternalDependencies
import org.jetbrains.kotlin.script.ScriptContents
import org.jetbrains.kotlin.script.ScriptDependenciesResolverEx
import org.jetbrains.kotlin.script.asFuture
import java.io.File
import java.util.concurrent.Future

class GradleKotlinScriptDependenciesResolver : ScriptDependenciesResolverEx {

    override fun resolve(script: ScriptContents, environment: Map<String, Any?>?, report: (ScriptDependenciesResolverEx.ReportSeverity, String, ScriptContents.Position?) -> Unit, previousDependencies: KotlinScriptExternalDependencies?): Future<KotlinScriptExternalDependencies?> {
        if (environment == null)
            return previousDependencies.asFuture()

        // Gradle compilation path
        val classPath = environment["classPath"] as? ClassPath
        if (classPath != null)
            return makeDependencies(classPath.asFiles).asFuture()

        // IDEA content assist path
        val gradleJavaHome = (environment["gradleJavaHome"] as? String)?.let { File(it) }
        val gradleJvmOptions = environment["gradleJvmOptions"] as? List<String>
        val gradleWithConnection = environment["gradleWithConnection"] as? ((ProjectConnection) -> Unit) -> Unit
        val gradleHome = environment["gradleHome"] as? File
        val projectRoot = environment["projectRoot"] as? File

        return when {
            projectRoot != null && gradleHome != null && gradleJavaHome != null ->
                retrieveKotlinBuildScriptModelFrom(projectRoot, gradleHome, gradleJavaHome)?.getDependencies(gradleHome)?.asFuture()
            gradleWithConnection != null && gradleHome != null ->
                retrieveKotlinBuildScriptModelFrom(gradleWithConnection, gradleJavaHome, gradleJvmOptions)?.getDependencies(gradleHome)?.asFuture()
            else -> null
        } ?: previousDependencies.asFuture()
    }

    private fun KotlinBuildScriptModel.getDependencies(gradleInstallation: File): KotlinScriptExternalDependencies {
        val gradleScriptKotlinJar = classPath.filter { it.name.startsWith("gradle-script-kotlin-") }
        return makeDependencies(
                classPath = classPath,
                sources = gradleScriptKotlinJar + sourceRootsOf(gradleInstallation))
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

fun retrieveKotlinBuildScriptModelFrom(projectDir: File, gradleInstallation: File, javaHome: File? = null, jvmOptions: List<String>? = null): KotlinBuildScriptModel? =
    withConnectionFrom(connectorFor(projectDir, gradleInstallation)) {
        kotlinBuildScriptModel(javaHome, jvmOptions)
    }

fun retrieveKotlinBuildScriptModelFrom(projectActionExecutor: ((ProjectConnection) -> Unit) -> Unit, javaHome: File?, jvmOptions: List<String>?): KotlinBuildScriptModel? {
    var model: KotlinBuildScriptModel? = null
    projectActionExecutor {
        model = it.kotlinBuildScriptModel(javaHome, jvmOptions)
    }
    return model
}

private val modelSpecificJvmOptions = listOf("-D${KotlinScriptPluginFactory.modeSystemPropertyName}=${KotlinScriptPluginFactory.classPathMode}")

private fun ProjectConnection.kotlinBuildScriptModel(javaHome: File?, jvmOptions: List<String>?): KotlinBuildScriptModel? =
        model(KotlinBuildScriptModel::class.java)?.run {
            javaHome?.let { setJavaHome(it) }
            setJvmArguments((jvmOptions ?: emptyList()) + modelSpecificJvmOptions)
            get()
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
