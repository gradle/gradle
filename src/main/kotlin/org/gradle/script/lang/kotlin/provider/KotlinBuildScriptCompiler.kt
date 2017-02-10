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

package org.gradle.script.lang.kotlin.provider

import org.gradle.api.Project
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.initialization.ScriptHandlerInternal
import org.gradle.api.internal.plugins.PluginManagerInternal
import org.gradle.api.internal.project.ProjectInternal

import org.gradle.groovy.scripts.ScriptSource

import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath

import org.gradle.plugin.use.internal.PluginRequestApplicator
import org.gradle.plugin.use.internal.PluginRequestCollector
import org.gradle.plugin.use.internal.PluginRequests
import org.gradle.script.lang.kotlin.accessors.ProjectExtensionsBuildSrcConfigurationAction
import org.gradle.script.lang.kotlin.accessors.projectAccessorsFileFor

import org.jetbrains.kotlin.com.intellij.openapi.util.text.StringUtilRt.convertLineSeparators
import org.jetbrains.kotlin.utils.addToStdlib.singletonList

import java.io.File

import java.lang.Error
import java.lang.Exception
import java.lang.reflect.InvocationTargetException

import java.net.URLClassLoader
import java.util.*

internal
class KotlinBuildScriptCompiler(
    val kotlinCompiler: CachingKotlinCompiler,
    val scriptSource: ScriptSource,
    val topLevelScript: Boolean,
    val scriptHandler: ScriptHandlerInternal,
    val pluginRequestApplicator: PluginRequestApplicator,
    val baseScope: ClassLoaderScope,
    val targetScope: ClassLoaderScope,
    gradleApi: ClassPath,
    val gradleApiExtensions: ClassPath,
    gradleScriptKotlinJars: ClassPath) {

    val scriptResource = scriptSource.resource!!
    val scriptFile = scriptResource.file!!
    val script = convertLineSeparators(scriptResource.text!!)

    /**
     * buildSrc output directories.
     */
    val buildSrc: ClassPath = exportClassPathOf(baseScope)

    val buildscriptBlockCompilationClassPath: ClassPath = gradleApi + gradleScriptKotlinJars + buildSrc

    val pluginsBlockCompilationClassPath: ClassPath = buildscriptBlockCompilationClassPath

    val scriptClassPath: ClassPath by lazy {
        scriptHandler.scriptClassPath
    }

    val compilationClassPath: ClassPath by lazy {
        scriptClassPath + buildscriptBlockCompilationClassPath
    }

    fun compile(): (Project) -> Unit =
        when {
            topLevelScript -> compileTopLevelScript()
            else -> compileScriptPlugin()
        }

    fun compileForClassPath(): (Project) -> Unit = { target ->
        ignoringErrors { executeBuildscriptBlockOn(target) }
        ignoringErrors { executePluginsBlockOn(target) }
    }

    private fun compileTopLevelScript(): (Project) -> Unit {
        return { target ->
            withUnexpectedBlockHandling {
                executeBuildscriptBlockOn(target)
                executeScriptBodyOn(target)
            }
        }
    }

    private fun compileScriptPlugin(): (Project) -> Unit {
        return { target ->
            withUnexpectedBlockHandling {
                executeScriptBodyOn(target)
            }
        }
    }

    private fun executeScriptBodyOn(project: Project) {
        val scriptClassLoader = scriptBodyClassLoaderFor(project)
        val scriptClass = compileScriptFile(scriptClassLoader, additionalSourceFilesFor(project))
        executeScriptWithContextClassLoader(scriptClassLoader, scriptClass, project)
    }

    private fun executeBuildscriptBlockOn(target: Project) {
        extractBuildscriptBlockFrom(script)?.let { buildscriptRange ->
            val buildscriptClass = compileBuildscriptBlock(buildscriptRange, buildscriptClassLoader)
            executeScriptWithContextClassLoader(buildscriptClassLoader, buildscriptClass, target)
        }
    }

    private fun scriptBodyClassLoaderFor(target: Project): ClassLoader {
        targetScope.export(gradleApiExtensions)

        executePluginsBlockOn(target)

        return targetScope.localClassLoader
    }

    private fun executePluginsBlockOn(target: Project) {
        val pluginRequests = collectPluginRequestsFromPluginsBlock()
        applyPluginsTo(target, pluginRequests)
    }

    private fun collectPluginRequestsFromPluginsBlock(): PluginRequests {
        val pluginRequestCollector = PluginRequestCollector(scriptSource)
        executePluginsBlockOn(pluginRequestCollector)
        return pluginRequestCollector.pluginRequests
    }

    private fun executePluginsBlockOn(pluginRequestCollector: PluginRequestCollector) {
        extractPluginsBlockFrom(script)?.let { pluginsRange ->
            val compiledPluginsBlock = compilePluginsBlock(pluginsRange)
            executeCompiledPluginsBlockOn(pluginRequestCollector, compiledPluginsBlock)
        }
    }

    private fun executeCompiledPluginsBlockOn(
        pluginRequestCollector: PluginRequestCollector,
        compiledPluginsBlock: CachingKotlinCompiler.CompiledPluginsBlock) {

        val (lineNumber, pluginsBlockClass) = compiledPluginsBlock
        val pluginDependenciesSpec = pluginRequestCollector.createSpec(lineNumber)
        withContextClassLoader(pluginsBlockClassLoader) {
            try {
                instantiate(pluginsBlockClass, pluginDependenciesSpec)
            } catch(e: InvocationTargetException) {
                throw e.targetException
            }
        }
    }

    private fun extractPluginsBlockFrom(script: String) =
        extractTopLevelSectionFrom(script, "plugins")

    private fun applyPluginsTo(target: Project, pluginRequests: PluginRequests) {
        pluginRequestApplicator.applyPlugins(
            pluginRequests, scriptHandler, pluginManagerOf(target), targetScope)
    }

    private fun pluginManagerOf(target: Project): PluginManagerInternal =
        (target as ProjectInternal).pluginManager

    private val buildscriptClassLoader: ClassLoader by lazy {
        localClassLoaderFor(baseScope.createChild("buildscript").apply {
            export(gradleApiExtensions)
        })
    }

    private val pluginsBlockClassLoader: ClassLoader by lazy {
        localClassLoaderFor(baseScope.createChild("plugins"))
    }

    private fun localClassLoaderFor(scope: ClassLoaderScope) =
        scope.run {
            lock()
            localClassLoader
        }

    private fun compileBuildscriptBlock(buildscriptRange: IntRange, classLoader: ClassLoader) =
        kotlinCompiler.compileBuildscriptBlockOf(
            scriptFile,
            script.linePreservingSubstring(buildscriptRange),
            buildscriptBlockCompilationClassPath,
            classLoader)

    private fun compilePluginsBlock(pluginsRange: IntRange) =
        kotlinCompiler.compilePluginsBlockOf(
            scriptFile,
            script.linePreservingSubstring_(pluginsRange),
            pluginsBlockCompilationClassPath,
            pluginsBlockClassLoader)

    private fun compileScriptFile(classLoader: ClassLoader, additionalSourceFiles: List<File>): Class<*> =
        kotlinCompiler.compileBuildScript(
            scriptFile, compilationClassPath, classLoader, additionalSourceFiles)

    private fun additionalSourceFilesFor(project: Project): List<File> =
        when {
            topLevelScript ->
                with (projectAccessorsFileFor(project)) {
                    when {
                        exists() -> singletonList()
                        else -> emptyList()
                    }
                }
            else -> emptyList()
        }

    private fun projectAccessorsFileFor(project: Project) =
        projectAccessorsFileFor(project.path, accessorDirFor(project))

    private fun accessorDirFor(project: Project) =
        project.file("buildSrc/${ProjectExtensionsBuildSrcConfigurationAction.PROJECT_ACCESSORS_DIR}")

    private fun executeScriptWithContextClassLoader(classLoader: ClassLoader, scriptClass: Class<*>, target: Project) {
        withContextClassLoader(classLoader) {
            executeScriptOf(scriptClass, target)
        }
    }

    private fun executeScriptOf(scriptClass: Class<*>, target: Project) {
        try {
            instantiate(scriptClass, target)
        } catch(e: InvocationTargetException) {
            if (e.cause is Error) {
                tryToLogClassLoaderHierarchyOf(scriptClass, target)
            }
            throw e.targetException
        }
    }

    private inline fun <reified T : Any> instantiate(scriptClass: Class<*>, target: T) {
        scriptClass.getConstructor(T::class.java).newInstance(target)
    }

    private inline fun withUnexpectedBlockHandling(action: () -> Unit) {
        try {
            action()
        } catch (unexpectedBlock: UnexpectedBlock) {
            val (line, column) = script.lineAndColumnFromRange(unexpectedBlock.location)
            val message = "$scriptFile($line,$column): Unexpected `${unexpectedBlock.identifier}` block found. Only one `${unexpectedBlock.identifier}` block is allowed per script."
            throw IllegalStateException(message, unexpectedBlock)
        }
    }

    private fun tryToLogClassLoaderHierarchyOf(scriptClass: Class<*>, target: Project) {
        try {
            logClassLoaderHierarchyOf(scriptClass, target)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun logClassLoaderHierarchyOf(scriptClass: Class<*>, project: Project) {
        classLoaderHierarchyFileFor(project).writeText(
            classLoaderHierarchyJsonFor(scriptClass, targetScope, pathFormatterFor(project)))
    }

    private fun classLoaderHierarchyFileFor(project: Project) =
        File(project.buildDir, "ClassLoaderHierarchy.json").apply {
            parentFile.mkdirs()
        }

    private fun pathFormatterFor(project: Project): PathStringFormatter {
        val baseDirs = baseDirsOf(project)
        return { pathString ->
            var result = pathString
            baseDirs.forEach { baseDir ->
                result = result.replace(baseDir.second, baseDir.first)
            }
            result
        }
    }

    private fun baseDirsOf(project: Project) =
        arrayListOf<Pair<String, String>>().apply {
            withBaseDir("HOME", userHome())
            withBaseDir("PROJECT_ROOT", project.rootDir)
            withOptionalBaseDir("GRADLE", project.gradle.gradleHomeDir)
            withOptionalBaseDir("GRADLE_USER", project.gradle.gradleUserHomeDir)
        }

    private fun ArrayList<Pair<String, String>>.withOptionalBaseDir(key: String, dir: File?) {
        dir?.let { withBaseDir(key, it) }
    }

    private fun ArrayList<Pair<String, String>>.withBaseDir(key: String, dir: File) {
        val label = '$' + key
        add(label + '/' to dir.toURI().toURL().toString())
        add(label to dir.canonicalPath)
    }

    private fun userHome() = File(System.getProperty("user.home"))

    private fun exportClassPathOf(baseScope: ClassLoaderScope): ClassPath =
        DefaultClassPath.of(
            (baseScope.exportClassLoader as? URLClassLoader)?.urLs?.map { File(it.toURI()) })
}


private
inline fun ignoringErrors(block: () -> Unit) {
    try {
        block()
    } catch(e: Exception) {
        e.printStackTrace()
    }
}


private
inline fun withContextClassLoader(classLoader: ClassLoader, block: () -> Unit) {
    val currentThread = Thread.currentThread()
    val previous = currentThread.contextClassLoader
    try {
        currentThread.contextClassLoader = classLoader
        block()
    } finally {
        currentThread.contextClassLoader = previous
    }
}
