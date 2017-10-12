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

package org.gradle.kotlin.dsl.provider

import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.initialization.ScriptHandlerInternal
import org.gradle.api.internal.plugins.PluginAwareInternal

import org.gradle.groovy.scripts.ScriptSource

import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath

import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.support.EmbeddedKotlinProvider
import org.gradle.kotlin.dsl.support.compilerMessageFor
import org.gradle.kotlin.dsl.support.userHome

import org.gradle.plugin.management.internal.PluginRequests
import org.gradle.plugin.use.PluginDependenciesSpec
import org.gradle.plugin.use.internal.PluginRequestCollector

import org.jetbrains.kotlin.com.intellij.openapi.util.text.StringUtilRt.convertLineSeparators

import java.io.File

import java.lang.reflect.InvocationTargetException

import java.util.*

import kotlin.reflect.KClass


internal
class KotlinBuildScriptCompiler(
    val kotlinCompiler: CachingKotlinCompiler,
    val scriptSource: ScriptSource,
    val topLevelScript: Boolean,
    val scriptHandler: ScriptHandlerInternal,
    val pluginRequestsHandler: PluginRequestsHandler,
    val baseScope: ClassLoaderScope,
    val targetScope: ClassLoaderScope,
    val classPathProvider: KotlinScriptClassPathProvider,
    val embeddedKotlinProvider: EmbeddedKotlinProvider) {

    val scriptResource = scriptSource.resource!!
    val scriptPath = scriptSource.fileName!!
    val script = convertLineSeparators(scriptResource.text!!)

    val buildscriptBlockCompilationClassPath: ClassPath = classPathProvider.compilationClassPathOf(targetScope.parent)

    val pluginsBlockCompilationClassPath: ClassPath = buildscriptBlockCompilationClassPath

    val compilationClassPath: ClassPath by lazy {
        buildscriptBlockCompilationClassPath + scriptHandler.scriptClassPath
    }

    fun compile(): (Any) -> Unit =
        when {
            topLevelScript -> compileTopLevelScript()
            else           -> compileScriptPlugin()
        }

    fun asKotlinScriptPluginTarget(configuration: (KotlinScriptPluginTarget<*>) -> Unit): (Any) -> Unit = { anyTarget ->
        configuration(kotlinScriptPluginTargetFor(anyTarget))
    }

    fun compileForClassPath(): (Any) -> Unit =
        asKotlinScriptPluginTarget { target ->
            ignoringErrors { executeBuildscriptBlockOn(target) }
            ignoringErrors { prepareTargetClassLoaderScopeOf(target) }
            ignoringErrors { executeScriptBodyOn(target) }
        }

    private
    fun compileTopLevelScript(): (Any) -> Unit =
        asKotlinScriptPluginTarget { target ->
            withUnexpectedBlockHandling {
                executeBuildscriptBlockOn(target)
                prepareAndExecuteScriptBodyOn(target)
            }
        }

    private
    fun compileScriptPlugin(): (Any) -> Unit =
        asKotlinScriptPluginTarget { target ->
            withUnexpectedBlockHandling {
                prepareAndExecuteScriptBodyOn(target)
            }
        }

    private
    fun prepareAndExecuteScriptBodyOn(target: KotlinScriptPluginTarget<*>) {
        prepareTargetClassLoaderScopeOf(target)
        executeScriptBodyOn(target)
    }

    private
    fun executeScriptBodyOn(target: KotlinScriptPluginTarget<*>) {
        val accessorsClassPath = accessorsClassPathFor(target)
        val compiledScript = compileScriptFileFor(target, compilationClassPath + accessorsClassPath)
        val scriptScope = scriptClassLoaderScopeWith(accessorsClassPath)
        executeCompiledScript(compiledScript, scriptScope, target)
    }

    private
    fun accessorsClassPathFor(target: KotlinScriptPluginTarget<*>): ClassPath =
        if (topLevelScript && target.providesAccessors)
            target.accessorsClassPath(compilationClassPath).bin
        else
            ClassPath.EMPTY

    private
    fun scriptClassLoaderScopeWith(accessorsClassPath: ClassPath) =
        targetScope.createChild("script").apply { local(accessorsClassPath) }

    private
    fun executeBuildscriptBlockOn(target: KotlinScriptPluginTarget<*>) {
        setupEmbeddedKotlinForBuildscript()
        if (target.supportsBuildscriptBlock) {
            extractBuildscriptBlockFrom(script)?.let { buildscriptRange ->
                executeBuildscriptBlockOn(target, buildscriptRange)
            }
        }
    }

    private
    fun executeBuildscriptBlockOn(target: KotlinScriptPluginTarget<*>, buildscriptRange: IntRange) {
        val compiledScript = compileBuildscriptBlock(buildscriptRange)
        executeCompiledScript(compiledScript, buildscriptBlockClassLoaderScope(), target)
    }

    private
    fun buildscriptBlockClassLoaderScope() =
        baseScope.createChild("buildscript")

    private
    fun setupEmbeddedKotlinForBuildscript() {
        embeddedKotlinProvider.run {
            addRepositoryTo(scriptHandler.repositories)
            pinDependenciesOn(
                scriptHandler.configurations["classpath"],
                "stdlib", "reflect")
        }
    }

    private
    fun executeCompiledScript(
        compiledScript: CachingKotlinCompiler.CompiledScript,
        scope: ClassLoaderScope,
        target: KotlinScriptPluginTarget<*>) {

        val scriptClass = classFrom(compiledScript, scope)
        executeScriptWithContextClassLoader(scriptClass, target)
    }

    private
    fun prepareTargetClassLoaderScopeOf(target: KotlinScriptPluginTarget<*>) {
        targetScope.export(classPathProvider.gradleApiExtensions)
        if (target.supportsPluginsBlock) {
            executePluginsBlockOn(target)
        }
    }

    private
    fun executePluginsBlockOn(target: KotlinScriptPluginTarget<*>) {
        val pluginRequests = collectPluginRequestsFromPluginsBlock()
        applyPluginsTo(target, pluginRequests)
    }

    private
    fun collectPluginRequestsFromPluginsBlock(): PluginRequests {
        val pluginRequestCollector = PluginRequestCollector(scriptSource)
        executePluginsBlockOn(pluginRequestCollector)
        return pluginRequestCollector.pluginRequests
    }

    private
    fun executePluginsBlockOn(pluginRequestCollector: PluginRequestCollector) {
        extractPluginsBlockFrom(script)?.let { pluginsRange ->
            val compiledPluginsBlock = compilePluginsBlock(pluginsRange)
            executeCompiledPluginsBlockOn(pluginRequestCollector, compiledPluginsBlock)
        }
    }

    private
    fun executeCompiledPluginsBlockOn(
        pluginRequestCollector: PluginRequestCollector,
        compiledPluginsBlock: CachingKotlinCompiler.CompiledPluginsBlock) {

        val (lineNumber, compiledScript) = compiledPluginsBlock
        val pluginsBlockClass = classFrom(compiledScript, baseScope.createChild("plugins"))
        val pluginDependenciesSpec = pluginRequestCollector.createSpec(lineNumber)
        withContextClassLoader(pluginsBlockClass.classLoader) {
            try {
                instantiate(pluginsBlockClass, PluginDependenciesSpec::class, pluginDependenciesSpec)
            } catch (e: InvocationTargetException) {
                throw e.targetException
            }
        }
    }

    private
    fun extractPluginsBlockFrom(script: String) =
        extractTopLevelSectionFrom(script, "plugins")

    private
    fun applyPluginsTo(target: KotlinScriptPluginTarget<*>, pluginRequests: PluginRequests) {
        pluginRequestsHandler.handle(
            pluginRequests, scriptHandler, target.`object` as PluginAwareInternal, targetScope)
    }

    private
    fun compileBuildscriptBlock(buildscriptRange: IntRange) =
        kotlinCompiler.compileBuildscriptBlockOf(
            scriptPath,
            script.linePreservingSubstring(buildscriptRange),
            buildscriptBlockCompilationClassPath,
            baseScope.exportClassLoader)

    private
    fun compilePluginsBlock(pluginsRange: IntRange) =
        kotlinCompiler.compilePluginsBlockOf(
            scriptPath,
            script.linePreservingSubstring_(pluginsRange),
            pluginsBlockCompilationClassPath,
            baseScope.exportClassLoader)

    private
    fun compileScriptFileFor(target: KotlinScriptPluginTarget<*>, classPath: ClassPath) =
        kotlinCompiler.compileGradleScript(
            target.scriptTemplate,
            scriptPath,
            script,
            classPath,
            targetScope.exportClassLoader)

    private
    fun classFrom(compiledScript: CachingKotlinCompiler.CompiledScript, scope: ClassLoaderScope): Class<*> =
        classLoaderFor(compiledScript.location, scope)
            .loadClass(compiledScript.className)

    private
    fun classLoaderFor(location: File, scope: ClassLoaderScope) =
        scope.run {
            local(DefaultClassPath(location))
            lock()
            localClassLoader
        }

    private
    fun executeScriptWithContextClassLoader(scriptClass: Class<*>, target: KotlinScriptPluginTarget<*>) {
        withContextClassLoader(scriptClass.classLoader) {
            executeScriptOf(scriptClass, target)
        }
    }

    private
    fun executeScriptOf(scriptClass: Class<*>, target: KotlinScriptPluginTarget<*>) {
        try {
            instantiate(scriptClass, target.targetType, target.`object`)
        } catch (e: InvocationTargetException) {
            if (e.cause is Error) {
                tryToLogClassLoaderHierarchyOf(scriptClass, target)
            }
            throw e.targetException
        }
    }

    private inline
    fun <reified T : Any> instantiate(scriptClass: Class<*>, targetType: KClass<*>, target: T) {
        scriptClass.getConstructor(targetType.java).newInstance(target)
    }

    private inline
    fun withUnexpectedBlockHandling(action: () -> Unit) {
        try {
            action()
        } catch (unexpectedBlock: UnexpectedBlock) {
            val (line, column) = script.lineAndColumnFromRange(unexpectedBlock.location)
            val message = compilerMessageFor(scriptPath, line, column, unexpectedBlockMessage(unexpectedBlock))
            throw IllegalStateException(message, unexpectedBlock)
        }
    }

    private
    fun unexpectedBlockMessage(block: UnexpectedBlock) =
        "Unexpected `${block.identifier}` block found. Only one `${block.identifier}` block is allowed per script."

    private
    fun tryToLogClassLoaderHierarchyOf(scriptClass: Class<*>, target: KotlinScriptPluginTarget<*>) {
        try {
            logClassLoaderHierarchyOf(scriptClass, target)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private
    fun logClassLoaderHierarchyOf(scriptClass: Class<*>, target: KotlinScriptPluginTarget<*>) {
        classLoaderHierarchyFileFor(target).writeText(
            classLoaderHierarchyJsonFor(scriptClass, targetScope, pathFormatterFor(target)))
    }

    private
    fun classLoaderHierarchyFileFor(target: KotlinScriptPluginTarget<*>) =
        File(target.logDir, "ClassLoaderHierarchy.json").apply {
            parentFile.mkdirs()
        }

    private
    fun pathFormatterFor(target: KotlinScriptPluginTarget<*>): PathStringFormatter {
        val baseDirs = baseDirsOf(target)
        return { pathString ->
            var result = pathString
            baseDirs.forEach { baseDir ->
                result = result.replace(baseDir.second, baseDir.first)
            }
            result
        }
    }

    private
    fun baseDirsOf(target: KotlinScriptPluginTarget<*>) =
        arrayListOf<Pair<String, String>>().apply {
            withBaseDir("PROJECT_ROOT", target.rootDir)
            withBaseDir("GRADLE_USER", target.gradleUserHomeDir)
            withOptionalBaseDir("GRADLE", target.gradleHomeDir)
            withBaseDir("HOME", userHome())
        }

    private
    fun ArrayList<Pair<String, String>>.withOptionalBaseDir(key: String, dir: File?) {
        dir?.let { withBaseDir(key, it) }
    }

    private
    fun ArrayList<Pair<String, String>>.withBaseDir(key: String, dir: File) {
        val label = '$' + key
        add(label + '/' to dir.toURI().toURL().toString())
        add(label to dir.canonicalPath)
    }

}


private inline
fun ignoringErrors(block: () -> Unit) {
    try {
        block()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}


private inline
fun withContextClassLoader(classLoader: ClassLoader, block: () -> Unit) {
    val currentThread = Thread.currentThread()
    val previous = currentThread.contextClassLoader
    try {
        currentThread.contextClassLoader = classLoader
        block()
    } finally {
        currentThread.contextClassLoader = previous
    }
}
