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

import org.gradle.groovy.scripts.ScriptSource

import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath

import java.io.File

import java.lang.Error
import java.lang.Exception
import java.lang.reflect.InvocationTargetException

import java.net.URLClassLoader
import java.util.*

class KotlinBuildScriptCompiler(
    val kotlinCompiler: CachingKotlinCompiler,
    scriptSource: ScriptSource,
    val topLevelScript: Boolean,
    val scriptHandler: ScriptHandlerInternal,
    val baseScope: ClassLoaderScope,
    val targetScope: ClassLoaderScope,
    gradleApi: ClassPath,
    val gradleApiExtensions: ClassPath,
    gradleScriptKotlinJars: ClassPath) {

    val scriptResource = scriptSource.resource!!
    val scriptFile = scriptResource.file!!
    val script = scriptResource.text!!

    /**
     * buildSrc output directories.
     */
    val buildSrc: ClassPath = exportClassPathOf(baseScope)

    val buildscriptBlockCompilationClassPath: ClassPath = gradleApi + gradleScriptKotlinJars + buildSrc

    val scriptClassPath: ClassPath by lazy {
        scriptHandler.scriptClassPath
    }

    val compilationClassPath: ClassPath by lazy {
        scriptClassPath + buildscriptBlockCompilationClassPath
    }

    fun compile(): (Project) -> Unit {
        val buildscriptBlockRange = extractTopLevelBuildscriptBlockRange()
        return when {
            buildscriptBlockRange != null ->
                twoPassScript(buildscriptBlockRange)
            else ->
                onePassScript()
        }
    }

    fun compileForClassPath(): (Project) -> Unit = { target ->
        executeBuildscriptBlockIgnoringErrors(target)
    }

    private fun extractTopLevelBuildscriptBlockRange() =
        if (topLevelScript) extractBuildscriptBlockFrom(script) else null

    private fun executeBuildscriptBlockIgnoringErrors(target: Project) {
        try {
            val buildscriptRange = extractBuildscriptBlockFrom(script)
            if (buildscriptRange != null) {
                executeBuildscriptBlock(buildscriptRange, target)
            }
        } catch(e: Exception) {
            e.printStackTrace()
        }
    }

    private fun onePassScript(): (Project) -> Unit {
        return { target ->
            val scriptClassLoader = scriptBodyClassLoader()
            val scriptClass = compileScriptFile(scriptClassLoader)
            executeScriptWithContextClassLoader(scriptClassLoader, scriptClass, target)
        }
    }

    private fun twoPassScript(buildscriptRange: IntRange): (Project) -> Unit {
        return { target ->
            executeBuildscriptBlock(buildscriptRange, target)

            val scriptClassLoader = scriptBodyClassLoader()
            val scriptClass = compileScriptFile(scriptClassLoader)
            executeScriptWithContextClassLoader(scriptClassLoader, scriptClass, target)
        }
    }

    private fun executeBuildscriptBlock(buildscriptRange: IntRange, target: Project) {
        val buildscriptClassLoader = buildscriptClassLoaderFrom(baseScope)
        val buildscriptClass = compileBuildscriptBlock(buildscriptRange, buildscriptClassLoader)
        executeScriptWithContextClassLoader(buildscriptClassLoader, buildscriptClass, target)
    }

    private fun scriptBodyClassLoader(): ClassLoader =
        defaultClassLoaderFor(targetScope.apply { export(scriptClassPath) })

    private fun buildscriptClassLoaderFrom(baseScope: ClassLoaderScope) =
        defaultClassLoaderFor(baseScope.createChild("buildscript"))

    private fun defaultClassLoaderFor(scope: ClassLoaderScope) =
        scope.run {
            export(gradleApiExtensions)
            lock()
            localClassLoader
        }

    private fun compileBuildscriptBlock(buildscriptRange: IntRange, classLoader: ClassLoader) =
        kotlinCompiler.compileBuildscriptBlockOf(scriptFile, buildscriptRange, buildscriptBlockCompilationClassPath, classLoader)

    private fun compileScriptFile(classLoader: ClassLoader): Class<*> =
        kotlinCompiler.compileBuildScript(scriptFile, compilationClassPath, classLoader)

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
