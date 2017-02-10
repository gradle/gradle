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

import org.gradle.cache.CacheRepository
import org.gradle.cache.internal.CacheKeyBuilder
import org.gradle.cache.internal.CacheKeyBuilder.CacheKeySpec

import org.gradle.internal.classloader.VisitableURLClassLoader
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.internal.logging.progress.ProgressLoggerFactory

import org.gradle.script.lang.kotlin.KotlinBuildScript

import org.gradle.script.lang.kotlin.loggerFor
import org.gradle.script.lang.kotlin.support.ImplicitImports
import org.gradle.script.lang.kotlin.support.KotlinBuildscriptBlock
import org.gradle.script.lang.kotlin.support.KotlinPluginsBlock
import org.gradle.script.lang.kotlin.support.compileKotlinScriptToDirectory

import org.jetbrains.kotlin.com.intellij.openapi.project.Project

import org.jetbrains.kotlin.script.KotlinScriptDefinition
import org.jetbrains.kotlin.script.KotlinScriptExternalDependencies

import java.io.File

import kotlin.reflect.KClass

class CachingKotlinCompiler(
    val cacheKeyBuilder: CacheKeyBuilder,
    val cacheRepository: CacheRepository,
    val progressLoggerFactory: ProgressLoggerFactory,
    val recompileScripts: Boolean) {

    private val logger = loggerFor<KotlinScriptPluginFactory>()

    private val cacheKeyPrefix = CacheKeySpec.withPrefix("gradle-script-kotlin")

    fun compileBuildscriptBlockOf(
        scriptFile: File,
        buildscript: String,
        classPath: ClassPath,
        parentClassLoader: ClassLoader): Class<*> {

        val scriptFileName = scriptFile.name
        return compileWithCache(cacheKeyPrefix + scriptFileName + buildscript, classPath, parentClassLoader) { cacheDir ->
            ScriptCompilationSpec(
                KotlinBuildscriptBlock::class,
                cacheFileFor(buildscript, cacheDir, scriptFileName),
                scriptFileName + " buildscript block")
        }
    }

    fun compilePluginsBlockOf(
        scriptFile: File,
        lineNumberedPluginsBlock: Pair<Int, String>,
        classPath: ClassPath,
        parentClassLoader: ClassLoader): CompiledPluginsBlock {

        val (lineNumber, plugins) = lineNumberedPluginsBlock
        val scriptFileName = scriptFile.name
        val scriptClass = compileWithCache(cacheKeyPrefix + scriptFileName + plugins, classPath, parentClassLoader) { cacheDir ->
            ScriptCompilationSpec(
                KotlinPluginsBlock::class,
                cacheFileFor(plugins, cacheDir, scriptFileName),
                scriptFileName + " plugins block")
        }
        return CompiledPluginsBlock(lineNumber, scriptClass)
    }

    data class CompiledPluginsBlock(val lineNumber: Int, val scriptClass: Class<*>)

    fun compileBuildScript(
        scriptFile: File,
        classPath: ClassPath,
        parentClassLoader: ClassLoader,
        additionalSourceFiles: List<File>): Class<*> {

        val scriptFileName = scriptFile.name
        return compileWithCache(cacheKeyPrefix + scriptFileName + scriptFile, classPath, parentClassLoader) {
            ScriptCompilationSpec(
                KotlinBuildScript::class,
                scriptFile,
                scriptFileName,
                additionalSourceFiles)
        }
    }

    private
    fun compileWithCache(
        cacheKeySpec: CacheKeySpec,
        classPath: ClassPath,
        parentClassLoader: ClassLoader,
        compilationSpecFor: (File) -> ScriptCompilationSpec): Class<*> {

        val cacheDir = cacheRepository
            .cache(cacheKeyFor(cacheKeySpec + parentClassLoader))
            .withProperties(mapOf("version" to "3"))
            .let { if (recompileScripts) it.withValidator { false } else it }
            .withInitializer { cache ->
                val cacheDir = cache.baseDir
                val scriptClass =
                    compileTo(classesDirOf(cacheDir), compilationSpecFor(cacheDir), classPath, parentClassLoader)
                writeClassNameTo(cacheDir, scriptClass.name)
            }.open().run {
                close()
                baseDir
            }
        return loadClassFrom(classesDirOf(cacheDir), readClassNameFrom(cacheDir), parentClassLoader)
    }

    data class ScriptCompilationSpec(
        val scriptTemplate: KClass<out Any>,
        val scriptFile: File,
        val description: String,
        val additionalSourceFiles: List<File> = emptyList())

    private
    fun compileTo(
        outputDir: File,
        spec: ScriptCompilationSpec,
        classPath: ClassPath,
        parentClassLoader: ClassLoader): Class<*> =

        withProgressLoggingFor(spec.description) {
            logger.debug("Kotlin compilation classpath for {}: {}", spec.description, classPath)
            compileKotlinScriptToDirectory(
                outputDir,
                spec.scriptFile,
                scriptDefinitionFromTemplate(spec.scriptTemplate),
                spec.additionalSourceFiles,
                classPath.asFiles,
                parentClassLoader, logger)
        }

    private fun cacheKeyFor(spec: CacheKeySpec) = cacheKeyBuilder.build(spec)

    private fun writeClassNameTo(cacheDir: File, className: String) =
        scriptClassNameFile(cacheDir).writeText(className)

    private fun readClassNameFrom(cacheDir: File) =
        scriptClassNameFile(cacheDir).readText()

    private fun scriptClassNameFile(cacheDir: File) = File(cacheDir, "script-class-name")

    private fun classesDirOf(cacheDir: File) = File(cacheDir, "classes")

    private fun loadClassFrom(classesDir: File, scriptClassName: String, classLoader: ClassLoader) =
        VisitableURLClassLoader(classLoader, classPathOf(classesDir)).loadClass(scriptClassName)

    private fun classPathOf(classesDir: File) =
        DefaultClassPath.of(listOf(classesDir))

    private fun cacheFileFor(text: String, cacheDir: File, fileName: String) =
        File(cacheDir, fileName).apply {
            writeText(text)
        }

    private fun scriptDefinitionFromTemplate(template: KClass<out Any>) =

        object : KotlinScriptDefinition(template) {

            override fun <TF : Any> getDependenciesFor(
                file: TF,
                project: Project,
                previousDependencies: KotlinScriptExternalDependencies?): KotlinScriptExternalDependencies? =

                object : KotlinScriptExternalDependencies {
                    override val imports: Iterable<String>
                        get() = ImplicitImports.list
                }
        }

    private fun <T> withProgressLoggingFor(description: String, action: () -> T): T {
        val operation = progressLoggerFactory
            .newOperation(javaClass)
            .start("Compiling script into cache", "Compiling $description into local build cache")
        try {
            return action()
        } finally {
            operation.completed()
        }
    }
}
