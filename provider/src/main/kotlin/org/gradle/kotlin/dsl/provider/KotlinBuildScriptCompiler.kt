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

import org.gradle.plugin.management.internal.DefaultPluginRequests
import org.gradle.plugin.management.internal.PluginRequests

import org.gradle.plugin.use.PluginDependenciesSpec
import org.gradle.plugin.use.internal.PluginRequestCollector

import org.jetbrains.kotlin.com.intellij.openapi.util.text.StringUtilRt.convertLineSeparators

import java.io.File

import java.lang.reflect.InvocationTargetException

import kotlin.reflect.KClass


internal
typealias KotlinScript = (Any) -> Unit


internal
class KotlinBuildScriptCompiler(
    val kotlinCompiler: CachingKotlinCompiler,
    val scriptTarget: KotlinScriptTarget<out Any>,
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

    fun compile() =
        when {
            topLevelScript -> compileTopLevelScript()
            else           -> compileScriptPlugin()
        }

    fun compileForClassPath() =
        asKotlinScript {
            ignoringErrors { executeBuildscriptBlock() }
            ignoringErrors { prepareTargetClassLoaderScope() }
            ignoringErrors { executeScriptBody() }
        }

    private
    fun compileTopLevelScript() =
        asKotlinScript {
            withUnexpectedBlockHandling {
                executeBuildscriptBlock()
                prepareAndExecuteScriptBody()
            }
        }

    private
    fun compileScriptPlugin() =
        asKotlinScript {
            withUnexpectedBlockHandling {
                prepareAndExecuteScriptBody()
            }
        }

    private
    fun asKotlinScript(script: () -> Unit): KotlinScript = {
        scriptTarget.prepare()
        script()
    }

    private
    fun prepareAndExecuteScriptBody() {
        prepareTargetClassLoaderScope()
        executeScriptBody()
    }

    private
    fun executeScriptBody() {
        val accessorsClassPath = accessorsClassPath()
        val compiledScript = compileScriptFileFor(compilationClassPath + accessorsClassPath)
        val scriptScope = scriptClassLoaderScopeWith(accessorsClassPath)
        executeCompiledScript(compiledScript, scriptScope)
    }

    private
    fun accessorsClassPath(): ClassPath =
        scriptTarget.takeIf { topLevelScript }?.accessorsClassPathFor(compilationClassPath)?.bin
            ?: ClassPath.EMPTY

    private
    fun scriptClassLoaderScopeWith(accessorsClassPath: ClassPath) =
        targetScope.createChild("script").apply { local(accessorsClassPath) }

    private
    fun executeBuildscriptBlock() {
        setupEmbeddedKotlinForBuildscript()
        if (scriptTarget.supportsBuildscriptBlock) {
            extractBuildscriptBlockFrom(script)?.let { buildscriptRange ->
                executeBuildscriptBlockFrom(buildscriptRange)
            }
        }
    }

    private
    fun executeBuildscriptBlockFrom(buildscriptRange: IntRange) {
        val compiledScript = compileBuildscriptBlock(buildscriptRange)
        executeCompiledScript(compiledScript, buildscriptBlockClassLoaderScope())
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
                "stdlib-jre8", "reflect")
        }
    }

    private
    fun executeCompiledScript(compiledScript: CachingKotlinCompiler.CompiledScript, scope: ClassLoaderScope) {
        val scriptClass = classFrom(compiledScript, scope)
        executeScriptWithContextClassLoader(scriptClass)
    }

    private
    fun prepareTargetClassLoaderScope() {
        targetScope.export(classPathProvider.gradleApiExtensions)
        executePluginsBlock()
    }

    private
    fun executePluginsBlock() {
        val pluginRequests = pluginRequests()
        applyPlugins(pluginRequests)
    }

    private
    fun pluginRequests(): PluginRequests =
        if (scriptTarget.supportsPluginsBlock) collectPluginRequestsFromPluginsBlock()
        else DefaultPluginRequests.EMPTY

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
    fun applyPlugins(pluginRequests: PluginRequests) {
        pluginRequestsHandler.handle(
            pluginRequests, scriptHandler, scriptTarget.`object` as PluginAwareInternal, targetScope)
    }

    private
    fun compileBuildscriptBlock(buildscriptRange: IntRange) =
        kotlinCompiler.compileBuildscriptBlockOf(
            scriptTarget.buildscriptBlockTemplate!!,
            scriptPath,
            script.linePreservingSubstring(buildscriptRange),
            buildscriptBlockCompilationClassPath,
            baseScope.exportClassLoader)

    private
    fun compilePluginsBlock(pluginsRange: IntRange) =
        kotlinCompiler.compilePluginsBlockOf(
            scriptTarget.pluginsBlockTemplate!!,
            scriptPath,
            script.linePreservingSubstring_(pluginsRange),
            pluginsBlockCompilationClassPath,
            baseScope.exportClassLoader)

    private
    fun compileScriptFileFor(classPath: ClassPath) =
        kotlinCompiler.compileGradleScript(
            scriptTarget.scriptTemplate,
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
    fun executeScriptWithContextClassLoader(scriptClass: Class<*>) {
        withContextClassLoader(scriptClass.classLoader) {
            executeScriptOf(scriptClass)
        }
    }

    private
    fun executeScriptOf(scriptClass: Class<*>) {
        try {
            instantiate(scriptClass, scriptTarget.type, scriptTarget.`object`)
        } catch (e: InvocationTargetException) {
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
