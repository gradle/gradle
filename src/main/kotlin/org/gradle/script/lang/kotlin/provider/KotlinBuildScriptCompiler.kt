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

import org.gradle.script.lang.kotlin.KotlinBuildScript
import org.gradle.script.lang.kotlin.support.KotlinBuildScriptSection
import org.gradle.script.lang.kotlin.support.compileKotlinScript
import org.gradle.script.lang.kotlin.embeddedKotlinVersion

import org.gradle.api.Project
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.initialization.ScriptHandlerInternal

import org.gradle.groovy.scripts.ScriptSource

import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath

import org.jetbrains.kotlin.script.KotlinScriptDefinitionFromTemplate

import org.slf4j.Logger

import java.io.File

import java.lang.Error
import java.lang.Exception
import java.lang.reflect.InvocationTargetException

import java.net.URLClassLoader
import java.util.*

import kotlin.reflect.KClass

class KotlinBuildScriptCompiler(
    scriptSource: ScriptSource,
    val topLevelScript: Boolean,
    val scriptHandler: ScriptHandlerInternal,
    val baseScope: ClassLoaderScope,
    val targetScope: ClassLoaderScope,
    gradleApi: ClassPath,
    val gradleApiExtensions: ClassPath,
    val gradleScriptKotlinJars: ClassPath,
    val logger: Logger) {

    val scriptResource = scriptSource.resource!!
    val scriptFile = scriptResource.file!!
    val script = scriptResource.text!!

    /**
     * buildSrc output directories.
     */
    val buildSrc: ClassPath = exportClassPathOf(baseScope)

    val buildScriptSectionCompilationClassPath: ClassPath = gradleApi + gradleScriptKotlinJars + buildSrc

    val scriptClassPath: ClassPath by lazy {
        scriptHandler.scriptClassPath
    }

    val compilationClassPath: ClassPath by lazy {
        val classPath = scriptClassPath + buildScriptSectionCompilationClassPath
        logger.info("Kotlin compilation classpath: {}", classPath)
        classPath
    }

    fun compile(): (Project) -> Unit {
        val buildscriptRange = extractTopLevelBuildScriptRange()
        return when {
            buildscriptRange != null ->
                twoPassScript(buildscriptRange)
            else ->
                onePassScript()
        }
    }

    fun compileForClassPath(): (Project) -> Unit = { target ->
        executeBuildscriptSectionIgnoringErrors(target)
    }

    private fun extractTopLevelBuildScriptRange() =
        if (topLevelScript) extractBuildScriptFrom(script) else null

    private fun executeBuildscriptSectionIgnoringErrors(target: Project) {
        try {
            val buildscriptRange = extractBuildScriptFrom(script)
            if (buildscriptRange != null) {
                executeBuildscriptSection(buildscriptRange, target)
            }
        } catch(e: Exception) {
            e.printStackTrace()
        }
    }

    private fun onePassScript(): (Project) -> Unit {
        val scriptClassLoader = scriptBodyClassLoaderFor(baseScope.exportClassLoader)
        val scriptClass = compileScriptFile(scriptClassLoader)
        return { target ->
            executeScriptWithContextClassLoader(scriptClassLoader, scriptClass, target)
        }
    }

    private fun twoPassScript(buildscriptRange: IntRange): (Project) -> Unit {
        return { target ->
            val buildscriptClassLoader = executeBuildscriptSection(buildscriptRange, target)

            val scriptClassLoader = scriptBodyClassLoaderFor(buildscriptClassLoader)
            val scriptClass = compileScriptFile(scriptClassLoader)
            executeScriptWithContextClassLoader(scriptClassLoader, scriptClass, target)
        }
    }

    private fun executeBuildscriptSection(buildscriptRange: IntRange, target: Project): ClassLoader {
        val buildscriptClassLoader = buildscriptClassLoaderFrom(baseScope)
        val buildscriptClass = compileBuildscriptSection(buildscriptRange, buildscriptClassLoader)
        executeScriptWithContextClassLoader(buildscriptClassLoader, buildscriptClass, target)
        return buildscriptClassLoader
    }

    private fun scriptBodyClassLoaderFor(parentClassLoader: ClassLoader): ClassLoader =
        if (scriptClassPath.hasConflictingKotlinRuntime() || buildSrc.hasConflictingKotlinRuntime())
            isolatedKotlinClassLoaderFor(parentClassLoader)
        else
            defaultClassLoaderFor(targetScope.apply { export(scriptClassPath) })

    private fun ClassPath.hasConflictingKotlinRuntime() =
        asFiles
            .asSequence()
            .map { kotlinRuntimeVersionFor(it) }
            .filterNotNull()
            .any { it != embeddedKotlinVersion }

    companion object {
        val kotlinRuntimePattern = "kotlin-runtime-(\\d\\.\\d.+?)\\.jar$".toPattern()

        fun kotlinRuntimeVersionFor(jar: File): String? =
            kotlinRuntimePattern.matcher(jar.name).run {
                if (find()) group(1) else null
            }
    }
    /**
     * Creates a [ChildFirstClassLoader] that reloads gradle-script-kotlin.jar in the context of
     * the buildscript classpath so to share the correct version of the Kotlin
     * standard library types.
     */
    private fun isolatedKotlinClassLoaderFor(parentClassLoader: ClassLoader): ChildFirstClassLoader {
        val isolatedClassPath = scriptClassPath + gradleScriptKotlinJars + buildSrc + gradleApiExtensions
        val isolatedClassLoader = ChildFirstClassLoader(parentClassLoader, isolatedClassPath)
        exportTo(targetScope, isolatedClassLoader)
        return isolatedClassLoader
    }

    private fun exportTo(targetScope: ClassLoaderScope, scriptClassLoader: ClassLoader) {
        targetScope.apply {
            export(scriptClassLoader)
            lock()
        }
    }

    private fun buildscriptClassLoaderFrom(baseScope: ClassLoaderScope) =
        defaultClassLoaderFor(baseScope.createChild("buildscript"))

    private fun defaultClassLoaderFor(scope: ClassLoaderScope) =
        scope.run {
            export(gradleApiExtensions)
            lock()
            localClassLoader
        }

    private fun compileBuildscriptSection(buildscriptRange: IntRange, classLoader: ClassLoader) =
        compileKotlinScript(
            tempBuildscriptFileFor(script.substring(buildscriptRange)),
            scriptDefinitionFromTemplate(KotlinBuildScriptSection::class, buildScriptSectionCompilationClassPath),
            classLoader, logger)

    private fun compileScriptFile(classLoader: ClassLoader) =
        compileKotlinScript(
            scriptFile,
            scriptDefinitionFromTemplate(KotlinBuildScript::class, compilationClassPath),
            classLoader, logger)

    private fun scriptDefinitionFromTemplate(template: KClass<out Any>, classPath: ClassPath) =
        KotlinScriptDefinitionFromTemplate(template, environment = mapOf("classPath" to classPath))

    private fun executeScriptWithContextClassLoader(classLoader: ClassLoader, scriptClass: Class<*>, target: Any) {
        withContextClassLoader(classLoader) {
            executeScriptOf(scriptClass, target)
        }
    }

    private fun executeScriptOf(scriptClass: Class<*>, target: Any) {
        try {
            scriptClass.getConstructor(Project::class.java).newInstance(target)
        } catch(e: InvocationTargetException) {
            if (e.cause is Error) {
                logClassLoaderHierarchyOf(scriptClass, target as Project)
            }
            throw e.targetException
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
            withBaseDir("PROJECT_ROOT", project.rootDir)
            withBaseDir("GRADLE", project.gradle.gradleHomeDir)
            withBaseDir("GRADLE_USER", project.gradle.gradleUserHomeDir)
            withBaseDir("HOME", userHome())
        }

    private fun ArrayList<Pair<String, String>>.withBaseDir(key: String, dir: File) {
        val label = '$' + key
        add(label + '/' to dir.toURI().toURL().toString())
        add(label to dir.canonicalPath)
    }

    private fun userHome() = File(System.getProperty("user.home"))

    private fun tempBuildscriptFileFor(buildscript: String) =
        createTempFile("buildscript-section", ".gradle.kts").apply {
            writeText(buildscript)
        }

    private fun exportClassPathOf(baseScope: ClassLoaderScope): ClassPath =
        DefaultClassPath.of(
            (baseScope.exportClassLoader as? URLClassLoader)?.urLs?.map { File(it.toURI()) })
}

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
