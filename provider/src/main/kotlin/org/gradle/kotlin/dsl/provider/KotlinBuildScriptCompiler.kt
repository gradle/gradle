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

import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.initialization.ScriptHandlerInternal
import org.gradle.api.internal.plugins.PluginAwareInternal

import org.gradle.groovy.scripts.ScriptSource

import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.exceptions.LocationAwareException

import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.support.EmbeddedKotlinProvider
import org.gradle.kotlin.dsl.support.ScriptCompilationException
import org.gradle.kotlin.dsl.support.compilerMessageFor
import org.gradle.kotlin.dsl.support.serviceOf

import org.gradle.plugin.management.internal.PluginRequests

import org.gradle.plugin.use.PluginDependenciesSpec
import org.gradle.plugin.use.internal.PluginRequestCollector

import org.gradle.util.TextUtil.convertLineSeparators

import java.lang.reflect.InvocationTargetException

import kotlin.reflect.KClass


internal
typealias KotlinScript = (Any) -> Unit


internal
data class PluginsBlockMetadata(val lineNumber: Int)


internal
class KotlinScriptSource(val source: ScriptSource) {

    private
    val scriptResource = source.resource!!

    val scriptPath = source.fileName!!

    val script = convertLineSeparators(scriptResource.text!!, "\n")

    val displayName: String
        get() = source.displayName

    fun classLoaderScopeIdFor(stage: String) =
        "kotlin-dsl:$scriptPath:$stage"
}


internal
class KotlinBuildScriptCompiler(
    private val kotlinCompiler: CachingKotlinCompiler,
    private val classloadingCache: KotlinScriptClassloadingCache,
    private val scriptSource: KotlinScriptSource,
    private val scriptTarget: KotlinScriptTarget<Any>,
    private val scriptHandler: ScriptHandlerInternal,
    private val pluginRequestsHandler: PluginRequestsHandler,
    private val baseScope: ClassLoaderScope,
    private val targetScope: ClassLoaderScope,
    private val classPathProvider: KotlinScriptClassPathProvider,
    private val embeddedKotlinProvider: EmbeddedKotlinProvider,
    private val classPathModeExceptionCollector: ClassPathModeExceptionCollector
) {

    private
    val buildscriptBlockCompilationClassPath: ClassPath = classPathProvider.compilationClassPathOf(targetScope.parent)

    private
    val pluginsBlockCompilationClassPath: ClassPath = buildscriptBlockCompilationClassPath

    private
    val compilationClassPath: ClassPath by lazy {
        buildscriptBlockCompilationClassPath + scriptHandler.scriptClassPath
    }

    private
    val accessorsClassPath: ClassPath by lazy {
        scriptTarget.accessorsClassPathFor(compilationClassPath).bin
    }

    private
    val buildscriptBlockRange: IntRange? by lazy {
        extractTopLevelSectionFrom(script, scriptTarget.buildscriptBlockName)
    }

    private
    val pluginsBlockRange: IntRange? by lazy {
        extractTopLevelSectionFrom(script, "plugins")
    }

    fun compile() =
        asKotlinScript {
            withUnexpectedBlockHandling {
                executeBuildscriptBlock()
                executePluginsBlock()
                executeScriptBody()
            }
        }

    fun compileForClassPath() =
        asKotlinScript {
            ignoringErrors { executeBuildscriptBlock() }
            ignoringErrors { executePluginsBlock() }
            ignoringErrors { executeScriptBody() }
        }

    private
    fun asKotlinScript(script: () -> Unit): KotlinScript = {
        scriptTarget.prepare()
        script()
    }

    private
    fun executeScriptBody() =
        loadScriptBodyClass().eval {
            scriptTarget.eval(scriptClass)
        }

    private
    fun executeBuildscriptBlock() =
        BuildscriptBlockEvaluator(
            scriptSource,
            scriptTarget,
            buildscriptBlockRange,
            buildscriptBlockCompilationClassPath,
            baseScope,
            kotlinCompiler,
            embeddedKotlinProvider,
            classloadingCache,
            classPathModeExceptionCollector).evaluate()

    private
    fun executePluginsBlock() {
        prepareTargetClassLoaderScope()
        applyPlugins(pluginRequests())
    }

    private
    fun prepareTargetClassLoaderScope() {
        targetScope.export(classPathProvider.gradleApiExtensions)
    }

    private
    fun pluginRequests() =
        scriptTarget.pluginsBlockTemplate?.let { template ->
            collectPluginRequestsFromPluginsBlock(template)
        }

    private
    fun collectPluginRequestsFromPluginsBlock(scriptTemplate: KClass<*>): PluginRequests {
        val pluginRequestCollector = PluginRequestCollector(scriptSource.source)
        executePluginsBlockOn(pluginRequestCollector, scriptTemplate)
        return pluginRequestCollector.pluginRequests
    }

    private
    fun executePluginsBlockOn(pluginRequestCollector: PluginRequestCollector, scriptTemplate: KClass<*>) =
        pluginsBlockRange?.let { pluginsRange ->
            val loadedPluginsBlockClass = loadPluginsBlockClass(scriptBlockForPlugins(pluginsRange, scriptTemplate))
            executeCompiledPluginsBlockOn(pluginRequestCollector, loadedPluginsBlockClass)
        }

    private
    fun executeCompiledPluginsBlockOn(
        pluginRequestCollector: PluginRequestCollector,
        loadedPluginsBlockClass: LoadedScriptClass<PluginsBlockMetadata>
    ) {

        val pluginDependenciesSpec = pluginRequestCollector.createSpec(loadedPluginsBlockClass.compiledScript.metadata.lineNumber)
        loadedPluginsBlockClass.eval {
            instantiate(scriptClass, PluginDependenciesSpec::class, pluginDependenciesSpec)
        }
    }

    private
    fun applyPlugins(pluginRequests: PluginRequests?) =
        pluginRequestsHandler.handle(
            pluginRequests, scriptHandler, scriptTarget.`object` as PluginAwareInternal, targetScope)

    private
    fun <T> withKotlinCompiler(action: CachingKotlinCompiler.() -> T) =
        scriptSource.withLocationAwareExceptionHandling {
            kotlinCompiler.action()
        }

    private
    fun scriptBlockForPlugins(pluginsRange: IntRange, scriptTemplate: KClass<*>) =
        script.linePreservingSubstring_(pluginsRange).let { (lineNumber, source) ->
            ScriptBlock(
                "plugins block '$scriptPath'",
                scriptTemplate,
                scriptPath,
                source,
                PluginsBlockMetadata(lineNumber))
        }

    private
    fun loadPluginsBlockClass(scriptBlock: ScriptBlock<PluginsBlockMetadata>) =
        classloadingCache.loadScriptClass(
            scriptBlock,
            baseScope.exportClassLoader,
            ::pluginsBlockClassLoaderScope,
            ::compilePluginsBlock)

    private
    fun compilePluginsBlock(scriptBlock: ScriptBlock<PluginsBlockMetadata>) =
        withKotlinCompiler {
            compileScriptBlock(scriptBlock, pluginsBlockCompilationClassPath)
        }

    private
    fun loadScriptBodyClass() =
        classloadingCache.loadScriptClass(
            scriptBlockForBody(),
            targetScope.localClassLoader,
            ::scriptBodyClassLoaderScope,
            ::compileScriptBody)

    private
    fun scriptBlockForBody() =
        ScriptBlock(
            scriptSource.displayName,
            scriptTarget.scriptTemplate,
            scriptPath,
            scriptWithoutBuildscriptAndPluginsBlocks,
            Unit)

    private
    val scriptWithoutBuildscriptAndPluginsBlocks
        get() = script.linePreservingBlankRanges(listOfNotNull(buildscriptBlockRange, pluginsBlockRange))

    private
    fun compileScriptBody(scriptBlock: ScriptBlock<Unit>) =
        withKotlinCompiler {
            compileScriptBlock(scriptBlock, compilationClassPath + accessorsClassPath)
        }

    private
    fun scriptBodyClassLoaderScope() = scriptClassLoaderScopeWith(accessorsClassPath)

    private
    fun scriptClassLoaderScopeWith(accessorsClassPath: ClassPath) =
        targetScope
            .createChild(classLoaderScopeIdFor("script"))
            .local(accessorsClassPath)

    private
    fun pluginsBlockClassLoaderScope() =
        baseScopeFor("plugins")

    private
    fun baseScopeFor(stage: String) =
        baseScope.createChild(classLoaderScopeIdFor(stage))

    private
    fun classLoaderScopeIdFor(stage: String) =
        scriptSource.classLoaderScopeIdFor(stage)

    private
    inline fun ignoringErrors(action: () -> Unit) = classPathModeExceptionCollector.ignoringErrors(action)

    private
    fun <T : Any> instantiate(scriptClass: Class<*>, targetType: KClass<*>, target: T) {
        scriptClass.getConstructor(targetType.java).newInstance(target)
    }

    private
    inline fun withUnexpectedBlockHandling(action: () -> Unit) {
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
    val scriptPath
        get() = scriptSource.scriptPath

    private
    val script
        get() = scriptSource.script
}


fun initScriptClassPathFor(
    gradle: GradleInternal,
    scriptHandler: ScriptHandlerInternal,
    scriptSource: ScriptSource
): ClassPath {

    val baseScope = gradle.classLoaderScope
    val classPathProvider = gradle.serviceOf<KotlinScriptClassPathProvider>()
    val gradleKotlinDsl = classPathProvider.gradleKotlinDsl

    val kotlinScriptSource = KotlinScriptSource(scriptSource)
    val scriptTarget = gradleInitScriptTarget(gradle, scriptHandler, scriptSource, baseScope)
    val initscriptBlockRange: IntRange? = extractTopLevelSectionFrom(kotlinScriptSource.script, scriptTarget.buildscriptBlockName)

    BuildscriptBlockEvaluator(
        kotlinScriptSource,
        scriptTarget,
        initscriptBlockRange,
        gradleKotlinDsl,
        baseScope,
        gradle.serviceOf(),
        gradle.serviceOf(),
        gradle.serviceOf(),
        gradle.serviceOf()).evaluateForClassPath()

    return gradleKotlinDsl + scriptHandler.scriptClassPath
}


private
class BuildscriptBlockEvaluator(
    val scriptSource: KotlinScriptSource,
    val scriptTarget: KotlinScriptTarget<Any>,
    val buildscriptBlockRange: IntRange?,
    val classPath: ClassPath,
    val baseScope: ClassLoaderScope,
    val kotlinCompiler: CachingKotlinCompiler,
    val embeddedKotlinProvider: EmbeddedKotlinProvider,
    val classloadingCache: KotlinScriptClassloadingCache,
    val classPathModeExceptionCollector: ClassPathModeExceptionCollector
) {

    fun evaluateForClassPath() {
        ignoringErrors {
            evaluate()
        }
    }

    fun evaluate() =
        scriptTarget.buildscriptBlockTemplate?.let { template ->
            setupEmbeddedKotlinForBuildscript()
            buildscriptBlockRange?.let { buildscriptRange ->
                executeBuildscriptBlockFrom(buildscriptRange, template)
            }
        }

    private
    fun compileBuildscriptBlock(scriptBlock: ScriptBlock<Unit>) =
        withKotlinCompiler {
            compileScriptBlock(scriptBlock, classPath)
        }

    private
    inline fun ignoringErrors(action: () -> Unit) = classPathModeExceptionCollector.ignoringErrors(action)

    private
    fun buildscriptBlockClassLoaderScope() =
        baseScopeFor("buildscript")

    private
    fun baseScopeFor(stage: String) =
        baseScope.createChild(classLoaderScopeIdFor(stage))

    private
    fun classLoaderScopeIdFor(stage: String) =
        scriptSource.classLoaderScopeIdFor(stage)

    private
    fun loadBuildscriptBlockClass(scriptBlock: ScriptBlock<Unit>) =
        classloadingCache.loadScriptClass(
            scriptBlock,
            baseScope.exportClassLoader,
            ::buildscriptBlockClassLoaderScope,
            ::compileBuildscriptBlock)

    private
    fun executeBuildscriptBlockFrom(buildscriptRange: IntRange, scriptTemplate: KClass<*>) =
        loadBuildscriptBlockClass(scriptBlockForBuildscript(buildscriptRange, scriptTemplate))
            .eval {
                scriptTarget.eval(scriptClass)
            }

    private
    fun scriptBlockForBuildscript(buildscriptRange: IntRange, scriptTemplate: KClass<*>) =
        ScriptBlock(
            "$buildscriptBlockName block '$scriptPath'",
            scriptTemplate,
            scriptPath,
            script.linePreservingSubstring(buildscriptRange),
            Unit)

    private
    fun setupEmbeddedKotlinForBuildscript() =
        embeddedKotlinProvider.run {
            val scriptHandler = scriptTarget.scriptHandler
            addRepositoryTo(scriptHandler.repositories)
            pinDependenciesOn(
                scriptHandler.configurations["classpath"],
                "stdlib-jdk8", "reflect")
        }

    private
    fun <T> withKotlinCompiler(action: CachingKotlinCompiler.() -> T): T =
        scriptSource.withLocationAwareExceptionHandling {
            action(kotlinCompiler)
        }

    private
    val scriptPath
        get() = scriptSource.scriptPath

    private
    val script
        get() = scriptSource.script

    private
    val buildscriptBlockName
        get() = scriptTarget.buildscriptBlockName
}


private
inline fun ClassPathModeExceptionCollector.ignoringErrors(action: () -> Unit) =
    try {
        action()
    } catch (e: Exception) {
        e.printStackTrace()
        collect(e)
    }


private
inline fun <T> KotlinScriptSource.withLocationAwareExceptionHandling(action: () -> T): T =
    try {
        action()
    } catch (e: ScriptCompilationException) {
        throw LocationAwareException(e, source, e.firstErrorLine)
    }


private
inline fun <T> LoadedScriptClass<T>.eval(action: LoadedScriptClass<T>.() -> Unit) =
    withContextClassLoader(scriptClass.classLoader) {
        try {
            action()
        } catch (e: InvocationTargetException) {
            throw e.targetException
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
