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
import org.gradle.internal.exceptions.LocationAwareException

import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.support.EmbeddedKotlinProvider
import org.gradle.kotlin.dsl.support.ScriptCompilationException
import org.gradle.kotlin.dsl.support.compilerMessageFor

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
    private val kotlinCompiler: CachingKotlinCompiler,
    private val classloadingCache: KotlinScriptClassloadingCache,
    private val scriptSource: ScriptSource,
    private val scriptTarget: KotlinScriptTarget<out Any>,
    private val scriptHandler: ScriptHandlerInternal,
    private val pluginRequestsHandler: PluginRequestsHandler,
    private val baseScope: ClassLoaderScope,
    private val targetScope: ClassLoaderScope,
    private val classPathProvider: KotlinScriptClassPathProvider,
    private val embeddedKotlinProvider: EmbeddedKotlinProvider) {

    private
    val scriptResource = scriptSource.resource!!

    private
    val scriptPath = scriptSource.fileName!!

    val script = convertLineSeparators(scriptResource.text!!)

    private
    val buildscriptBlockCompilationClassPath: ClassPath = classPathProvider.compilationClassPathOf(targetScope.parent)

    private
    val pluginsBlockCompilationClassPath: ClassPath = buildscriptBlockCompilationClassPath

    private
    val compilationClassPath: ClassPath by lazy {
        buildscriptBlockCompilationClassPath + scriptHandler.scriptClassPath
    }

    fun compile() =
        asKotlinScript {
            withUnexpectedBlockHandling {
                executeBuildscriptBlock()
                prepareAndExecuteScriptBody()
            }
        }

    fun compileForClassPath() =
        asKotlinScript {
            ignoringErrors { executeBuildscriptBlock() }
            ignoringErrors { prepareTargetClassLoaderScope() }
            ignoringErrors { executeScriptBody() }
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
        val compiledScriptBody = compileScriptFileFor(compilationClassPath + accessorsClassPath) { compiledScript ->
            classFrom(compiledScript, scriptClassLoaderScopeWith(accessorsClassPath))
        }
        withContextClassLoader(compiledScriptBody.scriptClass.classLoader) {
            scriptTarget.eval(compiledScriptBody.scriptClass)
        }
    }

    private
    fun accessorsClassPath(): ClassPath =
        scriptTarget.accessorsClassPathFor(compilationClassPath).bin

    private
    fun executeBuildscriptBlock() {
        scriptTarget.buildscriptBlockTemplate?.let { template ->
            setupEmbeddedKotlinForBuildscript()
            extractTopLevelSectionFrom(script, scriptTarget.buildscriptBlockName)?.let { buildscriptRange ->
                executeBuildscriptBlockFrom(buildscriptRange, template)
            }
        }
    }

    private
    fun executeBuildscriptBlockFrom(buildscriptRange: IntRange, scriptTemplate: KClass<*>) {
        val compiledBuildscriptBlock = compileBuildscriptBlock(buildscriptRange, scriptTemplate) { compiledScript ->
            classFrom(compiledScript, buildscriptBlockClassLoaderScope())
        }
        withContextClassLoader(compiledBuildscriptBlock.scriptClass.classLoader) {
            scriptTarget.evalBuildscriptBlock(compiledBuildscriptBlock.scriptClass)
        }
    }

    private
    fun setupEmbeddedKotlinForBuildscript() {
        embeddedKotlinProvider.run {
            addRepositoryTo(scriptHandler.repositories)
            pinDependenciesOn(
                scriptHandler.configurations["classpath"],
                "stdlib-jdk8", "reflect")
        }
    }

    private
    fun prepareTargetClassLoaderScope() {
        targetScope.export(classPathProvider.gradleApiExtensions)
        executePluginsBlock()
    }

    private
    fun executePluginsBlock() {
        applyPlugins(pluginRequests())
    }

    private
    fun pluginRequests() =
        scriptTarget.pluginsBlockTemplate?.let { template ->
            collectPluginRequestsFromPluginsBlock(template)
        }

    private
    fun collectPluginRequestsFromPluginsBlock(scriptTemplate: KClass<*>): PluginRequests {
        val pluginRequestCollector = PluginRequestCollector(scriptSource)
        executePluginsBlockOn(pluginRequestCollector, scriptTemplate)
        return pluginRequestCollector.pluginRequests
    }

    private
    fun executePluginsBlockOn(pluginRequestCollector: PluginRequestCollector, scriptTemplate: KClass<*>) {
        extractPluginsBlockFrom(script)?.let { pluginsRange ->
            val compiledPluginsBlock = compilePluginsBlock(pluginsRange, scriptTemplate) { compiledScript ->
                classFrom(compiledScript, pluginsBlockClassLoaderScope())
            }
            executeCompiledPluginsBlockOn(pluginRequestCollector, compiledPluginsBlock)
        }
    }

    private
    fun executeCompiledPluginsBlockOn(
        pluginRequestCollector: PluginRequestCollector,
        compiledPluginsBlock: LoadedScriptClass<PluginsBlockMetadata>) {

        val pluginDependenciesSpec = pluginRequestCollector.createSpec(compiledPluginsBlock.compiledScript.metadata.lineNumber)
        withContextClassLoader(compiledPluginsBlock.scriptClass.classLoader) {
            instantiate(compiledPluginsBlock.scriptClass, PluginDependenciesSpec::class, pluginDependenciesSpec)
        }
    }

    private
    fun extractPluginsBlockFrom(script: String) =
        extractTopLevelSectionFrom(script, "plugins")

    private
    fun applyPlugins(pluginRequests: PluginRequests?) {
        pluginRequestsHandler.handle(
            pluginRequests, scriptHandler, scriptTarget.`object` as PluginAwareInternal, targetScope)
    }

    private
    fun compileBuildscriptBlock(
        buildscriptRange: IntRange,
        scriptTemplate: KClass<*>,
        classLoading: (CompiledScript<Unit>) -> Class<*>) =

        "buildscript block '$scriptPath'".let { displayName ->
            classloadingCache.loadScriptClass(displayName, scriptTemplate, scriptSource, baseScope.exportClassLoader, classLoading) {
                withKotlinCompiler {
                    compileBuildscriptBlockOf(
                        displayName,
                        scriptTemplate,
                        scriptPath,
                        script.linePreservingSubstring(buildscriptRange),
                        buildscriptBlockCompilationClassPath)
                }
            }
        }

    private
    fun compilePluginsBlock(
        pluginsRange: IntRange,
        scriptTemplate: KClass<*>,
        classLoading: (CompiledScript<PluginsBlockMetadata>) -> Class<*>) =

        "plugins block '$scriptPath'".let { displayName ->
            classloadingCache.loadScriptClass(displayName, scriptTemplate, scriptSource, baseScope.exportClassLoader, classLoading) {
                withKotlinCompiler {
                    compilePluginsBlockOf(
                        displayName,
                        scriptTemplate,
                        scriptPath,
                        script.linePreservingSubstring_(pluginsRange),
                        pluginsBlockCompilationClassPath)
                }
            }
        }

    private
    fun compileScriptFileFor(
        classPath: ClassPath,
        classLoading: (CompiledScript<Unit>) -> Class<*>) =

        classloadingCache.loadScriptClass(scriptSource.displayName, scriptTarget.scriptTemplate, scriptSource, targetScope.localClassLoader, classLoading) {
            withKotlinCompiler {
                compileGradleScript(
                    scriptSource.displayName,
                    scriptTarget.scriptTemplate,
                    scriptPath,
                    script,
                    classPath,
                    Unit)
            }
        }

    private
    fun <T> withKotlinCompiler(action: CachingKotlinCompiler.() -> T): T =
        try {
            kotlinCompiler.action()
        } catch (e: ScriptCompilationException) {
            throw LocationAwareException(e, scriptSource, e.firstErrorLine)
        }

    private
    fun classFrom(compiledScript: CompiledScript<*>, scope: ClassLoaderScope): Class<*> =
        classLoaderFor(compiledScript.location, scope)
            .loadClass(compiledScript.className)

    private
    fun scriptClassLoaderScopeWith(accessorsClassPath: ClassPath) =
        targetScope
            .createChild(classLoaderScopeIdFor("script"))
            .local(accessorsClassPath)

    private
    fun pluginsBlockClassLoaderScope() =
        baseScopeFor("plugins")

    private
    fun buildscriptBlockClassLoaderScope() =
        baseScopeFor("buildscript")

    private
    fun baseScopeFor(stage: String) =
        baseScope.createChild(classLoaderScopeIdFor(stage))

    private
    fun classLoaderScopeIdFor(stage: String) =
        "kotlin-dsl:$scriptPath:$stage"

    private
    fun classLoaderFor(location: File, scope: ClassLoaderScope) =
        scope
            .local(DefaultClassPath(location))
            .lock()
            .localClassLoader

    private
    fun <T : Any> instantiate(scriptClass: Class<*>, targetType: KClass<*>, target: T) {
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
fun ignoringErrors(action: () -> Unit) {
    try {
        action()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}


private inline
fun withContextClassLoader(classLoader: ClassLoader, action: () -> Unit) {
    val currentThread = Thread.currentThread()
    val previous = currentThread.contextClassLoader
    try {
        currentThread.contextClassLoader = classLoader
        action()
    } catch (e: InvocationTargetException) {
        throw e.targetException
    } finally {
        currentThread.contextClassLoader = previous
    }
}
