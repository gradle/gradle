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

import org.gradle.cache.CacheOpenException
import org.gradle.cache.PersistentCache
import org.gradle.cache.internal.CacheKeyBuilder.CacheKeySpec

import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.logging.progress.ProgressLoggerFactory

import org.gradle.kotlin.dsl.cache.ScriptCache

import org.gradle.kotlin.dsl.support.ImplicitImports
import org.gradle.kotlin.dsl.support.ScriptCompilationException
import org.gradle.kotlin.dsl.support.compileKotlinScriptToDirectory
import org.gradle.kotlin.dsl.support.loggerFor
import org.gradle.kotlin.dsl.support.messageCollectorFor

import org.jetbrains.kotlin.script.KotlinScriptDefinition

import java.io.File

import kotlin.reflect.KClass

import kotlin.script.dependencies.Environment
import kotlin.script.dependencies.ScriptContents
import kotlin.script.experimental.dependencies.DependenciesResolver
import kotlin.script.experimental.dependencies.ScriptDependencies


internal
data class CompiledScript<out T : Any>(val location: File, val className: String, val metadata: T)


internal
data class PluginsBlockMetadata(val lineNumber: Int)


internal
class CachingKotlinCompiler(
    private val scriptCache: ScriptCache,
    private val implicitImports: ImplicitImports,
    private val progressLoggerFactory: ProgressLoggerFactory) {

    init {
        org.jetbrains.kotlin.cli.common.environment.setIdeaIoUseFallback()
    }

    private
    val logger = loggerFor<KotlinScriptPluginFactory>()

    private
    val cacheKeyPrefix = CacheKeySpec.withPrefix("gradle-kotlin-dsl")

    private
    val cacheProperties = mapOf("version" to "6")

    fun compileBuildscriptBlockOf(
        displayName: String,
        buildscriptBlockTemplate: KClass<out Any>,
        scriptPath: String,
        buildscript: String,
        classPath: ClassPath): CompiledScript<Unit> {

        val scriptFileName = scriptFileNameFor(scriptPath)
        val cacheKeySpec = cacheKeyPrefix + buildscriptBlockTemplate.qualifiedName + scriptFileName + buildscript
        return compileScript(cacheKeySpec, classPath, Unit) { cacheDir ->
            ScriptCompilationSpec(
                displayName,
                buildscriptBlockTemplate,
                scriptPath,
                cacheFileFor(buildscript, cacheDir, scriptFileName))
        }
    }

    fun compilePluginsBlockOf(
        displayName: String,
        pluginsBlockTemplate: KClass<out Any>,
        scriptPath: String,
        lineNumberedPluginsBlock: Pair<Int, String>,
        classPath: ClassPath): CompiledScript<PluginsBlockMetadata> {

        val (lineNumber, plugins) = lineNumberedPluginsBlock
        val scriptFileName = scriptFileNameFor(scriptPath)
        val cacheKeySpec = cacheKeyPrefix + pluginsBlockTemplate.qualifiedName + scriptFileName + plugins
        return compileScript(cacheKeySpec, classPath, PluginsBlockMetadata(lineNumber)) { cacheDir ->
            ScriptCompilationSpec(
                displayName,
                pluginsBlockTemplate,
                scriptPath,
                cacheFileFor(plugins, cacheDir, scriptFileName))
        }
    }

    fun <T : Any> compileGradleScript(
        displayName: String,
        scriptTemplate: KClass<out Any>,
        scriptPath: String,
        script: String,
        classPath: ClassPath,
        metadata: T): CompiledScript<T> {

        val scriptFileName = scriptFileNameFor(scriptPath)
        val cacheKeySpec = cacheKeyPrefix + scriptTemplate.qualifiedName + scriptFileName + script
        return compileScript(cacheKeySpec, classPath, metadata) { cacheDir ->
            ScriptCompilationSpec(
                displayName,
                scriptTemplate,
                scriptPath,
                cacheFileFor(script, cacheDir, scriptFileName))
        }
    }

    private
    fun scriptFileNameFor(scriptPath: String) = scriptPath.run {
        val index = lastIndexOf('/')
        if (index != -1) substring(index + 1, length) else substringAfterLast('\\')
    }

    private
    fun <T : Any> compileScript(
        cacheKeySpec: CacheKeySpec,
        classPath: ClassPath,
        metadata: T,
        compilationSpecFor: (File) -> ScriptCompilationSpec): CompiledScript<T> {

        try {
            val cacheDir = cacheDirFor(cacheKeySpec + classPath) {
                val scriptClassName =
                    compileScriptTo(classesDirOf(baseDir), compilationSpecFor(baseDir), classPath)
                writeClassNameTo(baseDir, scriptClassName)
            }
            return CompiledScript(classesDirOf(cacheDir), readClassNameFrom(cacheDir), metadata)
        } catch (e: CacheOpenException) {
            throw e.cause as? ScriptCompilationException ?: e
        }
    }

    data class ScriptCompilationSpec(
        val displayName: String,
        val scriptTemplate: KClass<out Any>,
        val originalPath: String,
        val scriptFile: File)

    private
    fun compileScriptTo(
        outputDir: File,
        spec: ScriptCompilationSpec,
        classPath: ClassPath): String =

        spec.run {
            withProgressLoggingFor(displayName) {
                logger.debug(
                    "Compiling {} from {} with classpath: {}",
                    spec.scriptTemplate.simpleName, displayName, classPath)
                compileKotlinScriptToDirectory(
                    outputDir,
                    scriptFile,
                    scriptDefinitionFromTemplate(scriptTemplate),
                    classPath.asFiles,
                    messageCollectorFor(logger) { path ->
                        if (path == scriptFile.path) originalPath
                        else path
                    })
            }
        }

    private
    fun cacheDirFor(cacheKeySpec: CacheKeySpec, initializer: PersistentCache.() -> Unit): File =
        scriptCache.cacheDirFor(cacheKeySpec, properties = cacheProperties, initializer = initializer)

    private
    fun writeClassNameTo(cacheDir: File, className: String) =
        scriptClassNameFile(cacheDir).writeText(className)

    private
    fun readClassNameFrom(cacheDir: File) =
        scriptClassNameFile(cacheDir).readText()

    private
    fun scriptClassNameFile(cacheDir: File) = File(cacheDir, "script-class-name")

    private
    fun classesDirOf(cacheDir: File) = File(cacheDir, "classes")

    private
    fun cacheFileFor(text: String, cacheDir: File, fileName: String) =
        File(cacheDir, fileName).apply {
            writeText(text)
        }

    private
    fun scriptDefinitionFromTemplate(template: KClass<out Any>) =
        object : KotlinScriptDefinition(template) {
            override val dependencyResolver = Resolver
        }

    private
    val Resolver by lazy {
        object : DependenciesResolver {
            override fun resolve(
                scriptContents: ScriptContents,
                environment: Environment): DependenciesResolver.ResolveResult =

                DependenciesResolver.ResolveResult.Success(
                    ScriptDependencies(imports = implicitImports.list), emptyList())
        }
    }

    private
    fun <T> withProgressLoggingFor(description: String, action: () -> T): T {
        val operation = progressLoggerFactory
            .newOperation(this::class.java)
            .start("Compiling script into cache", "Compiling $description into local build cache")
        try {
            return action()
        } finally {
            operation.completed()
        }
    }
}
