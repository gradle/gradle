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
import org.jetbrains.kotlin.script.ScriptDependenciesResolver
import org.jetbrains.kotlin.script.ScriptDependenciesResolverEx
import org.jetbrains.kotlin.script.asFuture

import java.io.File

import java.security.MessageDigest

import java.util.Arrays.equals
import java.util.concurrent.Future

typealias Environment = Map<String, Any?>

interface KotlinBuildScriptModelProvider {
    fun modelFor(environment: Environment): KotlinBuildScriptModel?
}

interface SourcePathProvider {
    fun sourcePathFor(model: KotlinBuildScriptModel, environment: Environment): Collection<File>
}

typealias ScriptSectionTokensProvider = (CharSequence, String) -> Sequence<CharSequence>

class GradleKotlinScriptDependenciesResolver : ScriptDependenciesResolverEx, ScriptDependenciesResolver {

    var modelProvider: KotlinBuildScriptModelProvider = DefaultKotlinBuildScriptModelProvider

    var sourcePathProvider: SourcePathProvider = DefaultSourcePathProvider

    class DepsWithBuildscriptSectionHash(
        override val classpath: Iterable<File>,
        override val imports: Iterable<String>,
        override val sources: Iterable<File>,
        val hash: ByteArray?) : KotlinScriptExternalDependencies

    @Deprecated("drop it as soon as compatibility with kotlin 1.1-M01 is no longer needed")
    override fun resolve(script: ScriptContents,
                         environment: Environment?,
                         previousDependencies: KotlinScriptExternalDependencies?): KotlinScriptExternalDependencies? =
        resolveImpl(script, environment, previousDependencies)

    override fun resolve(script: ScriptContents,
                         environment: Environment?,
                         report: (ScriptDependenciesResolver.ReportSeverity, String, ScriptContents.Position?) -> Unit,
                         previousDependencies: KotlinScriptExternalDependencies?): Future<KotlinScriptExternalDependencies?> =
        resolveImpl(script, environment, previousDependencies).asFuture()

    private fun resolveImpl(script: ScriptContents,
                            environment: Environment?,
                            previousDependencies: KotlinScriptExternalDependencies?): KotlinScriptExternalDependencies? {
        if (environment == null)
            return previousDependencies

        // Gradle compilation path
        val classPath = environment["classPath"] as? ClassPath
        if (classPath != null)
            return makeDependencies(classPath.asFiles)

        // IDEA content assist path
        val hash = getBuildscriptSectionHash(script, environment)
        return when {
            hash != null && sameBuildScriptSectionHashAs(previousDependencies, hash) ->
                null
            else ->
                modelFor(environment)?.getDependencies(environment, hash)
        } ?: previousDependencies
    }

    private fun modelFor(environment: Environment) =
        modelProvider.modelFor(environment)

    private fun sameBuildScriptSectionHashAs(previousDependencies: KotlinScriptExternalDependencies?, hash: ByteArray) =
        buildScriptSectionHashOf(previousDependencies)?.let { equals(it, hash) } ?: false

    private fun buildScriptSectionHashOf(previousDependencies: KotlinScriptExternalDependencies?) =
        (previousDependencies as? DepsWithBuildscriptSectionHash)?.hash

    private fun KotlinBuildScriptModel.getDependencies(environment: Environment, hash: ByteArray?): KotlinScriptExternalDependencies =
        makeDependencies(
            classPath = classPath,
            sources = sourcePathFor(environment),
            hash = hash)

    private fun KotlinBuildScriptModel.sourcePathFor(environment: Environment) =
        sourcePathProvider.sourcePathFor(this, environment)

    private fun makeDependencies(classPath: Iterable<File>, sources: Iterable<File> = emptyList(), hash: ByteArray? = null) =
        DepsWithBuildscriptSectionHash(classPath, implicitImports, sources, hash)

    private fun getBuildscriptSectionHash(script: ScriptContents, environment: Environment): ByteArray? {
        @Suppress("unchecked_cast")
        val getScriptSectionTokens = environment["getScriptSectionTokens"] as? ScriptSectionTokensProvider
        return when {
            getScriptSectionTokens != null ->
                with(MessageDigest.getInstance("MD5")) {
                    val text = script.text ?: script.file?.readText()
                    text?.let { text ->
                        getScriptSectionTokens(text, "buildscript").forEach {
                            update(it.toString().toByteArray())
                        }
                    }
                    digest()
                }
            else -> null
        }
    }

    companion object {
        val implicitImports = listOf(
            "org.gradle.api.plugins.*",
            "org.gradle.script.lang.kotlin.*")
    }
}

object DefaultKotlinBuildScriptModelProvider : KotlinBuildScriptModelProvider {

    override fun modelFor(environment: Environment): KotlinBuildScriptModel? {

        val projectRoot = environment.projectRoot
        val gradleHome = environment.gradleHome
        val gradleJavaHome = (environment["gradleJavaHome"] as? String)?.let(::File)
        if (projectRoot != null && gradleHome != null && gradleJavaHome != null)
            return retrieveKotlinBuildScriptModelFrom(projectRoot, gradleHome, gradleJavaHome)

        @Suppress("unchecked_cast")
        val gradleJvmOptions = environment["gradleJvmOptions"] as? List<String>
        @Suppress("unchecked_cast")
        val gradleWithConnection = environment["gradleWithConnection"] as? ((ProjectConnection) -> Unit) -> Unit
        return when {
            gradleWithConnection != null && gradleHome != null ->
                retrieveKotlinBuildScriptModelFrom(gradleWithConnection, gradleJavaHome, gradleJvmOptions)
            else ->
                null
        }
    }
}

object DefaultSourcePathProvider : SourcePathProvider {

    override fun sourcePathFor(model: KotlinBuildScriptModel, environment: Environment): Collection<File> {
        val gradleScriptKotlinJar = model.classPath.filter { it.name.startsWith("gradle-script-kotlin-") }
        val projectBuildSrcRoots = buildSrcRootsOf(environment.projectRoot!!)
        val gradleSourceRoots = sourceRootsOf(environment.gradleHome!!)
        return gradleScriptKotlinJar + projectBuildSrcRoots + gradleSourceRoots
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
}

private val Environment.projectRoot: File?
    get() = this["projectRoot"] as? File

private val Environment.gradleHome: File?
    get() = this["gradleHome"] as? File

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

private val modelSpecificJvmOptions =
    listOf("-D${KotlinScriptPluginFactory.modeSystemPropertyName}=${KotlinScriptPluginFactory.classPathMode}")

private fun ProjectConnection.kotlinBuildScriptModel(javaHome: File?, jvmOptions: List<String>?): KotlinBuildScriptModel? =
    model(KotlinBuildScriptModel::class.java)?.run {
        setJavaHome(javaHome)
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
