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

import org.gradle.api.Project

import org.gradle.api.file.FileCollection

import org.gradle.api.internal.ClassPathRegistry
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactoryInternal
import org.gradle.api.internal.classpath.ModuleRegistry
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.initialization.ClassLoaderScope

import org.gradle.internal.classloader.ClassLoaderVisitor
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath

import org.gradle.kotlin.dsl.support.isGradleKotlinDslJar
import org.gradle.kotlin.dsl.support.isGradleKotlinDslJarName
import org.gradle.kotlin.dsl.support.serviceOf

import java.io.File

import java.net.URI
import java.net.URISyntaxException
import java.net.URL

import java.util.concurrent.ConcurrentHashMap


internal
fun gradleKotlinDslOf(project: Project): List<File> =
    kotlinScriptClassPathProviderOf(project).run {
        gradleKotlinDsl.asFiles
    }


fun gradleKotlinDslJarsOf(project: Project): FileCollection =
    project.fileCollectionOf(
        kotlinScriptClassPathProviderOf(project).gradleKotlinDslJars.asFiles.filter(::isGradleKotlinDslJar),
        "gradleKotlinDsl"
    )


internal
fun Project.fileCollectionOf(files: Collection<File>, name: String): FileCollection =
    serviceOf<FileCollectionFactory>().fixed(name, files)


internal
fun kotlinScriptClassPathProviderOf(project: Project) =
    project.serviceOf<KotlinScriptClassPathProvider>()


class KotlinScriptClassPathProvider(
    private val moduleRegistry: ModuleRegistry,
    private val classPathRegistry: ClassPathRegistry,
    private val coreAndPluginsScope: ClassLoaderScope,
) {

    /**
     * Generated Gradle API jar plus supporting libraries such as groovy-all.jar and generated API extensions.
     */
    internal
    val gradleKotlinDsl: ClassPath by lazy {
        gradleApi + gradleApiExtensions + gradleKotlinDslJars
    }

    private
    val gradleApi: ClassPath by lazy {
        DefaultClassPath.of(gradleApiJars())
    }

    /**
     * Generated extensions to the Gradle API.
     */
    private
    val gradleApiExtensions: ClassPath by lazy {
        moduleRegistry.getModule("gradle-kotlin-dsl-extensions").classpath
    }

    /**
     * gradle-kotlin-dsl.jar plus kotlin libraries.
     */
    internal
    val gradleKotlinDslJars: ClassPath by lazy {
        DefaultClassPath.of(gradleKotlinDslJars())
    }

    /**
     * The Gradle implementation classpath which should **NOT** be visible
     * in the compilation classpath of any script.
     */
    private
    val gradleImplementationClassPath: Set<File> by lazy {
        cachedClassLoaderClassPath.of(coreAndPluginsScope.exportClassLoader)
    }

    fun compilationClassPathOf(scope: ClassLoaderScope): ClassPath =
        cachedScopeCompilationClassPath.computeIfAbsent(scope, ::computeCompilationClassPath)

    private
    fun computeCompilationClassPath(scope: ClassLoaderScope): ClassPath {
        return gradleKotlinDsl + exportClassPathFromHierarchyOf(scope)
    }

    internal
    fun exportClassPathFromHierarchyOf(scope: ClassLoaderScope): ClassPath {
        require(scope.isLocked) {
            "$scope must be locked before it can be used to compute a classpath!"
        }
        val exportedClassPath = cachedClassLoaderClassPath.of(scope.exportClassLoader)
        return DefaultClassPath.of(exportedClassPath - gradleImplementationClassPath)
    }

    private
    fun gradleApiJars(): List<File> =
        gradleJars.filter { file ->
            file.name.let { !isKotlinJar(it) && !isGradleKotlinDslJarName(it) }
        }

    private
    fun gradleKotlinDslJars(): List<File> =
        gradleJars.filter { file ->
            file.name.let { isKotlinJar(it) || isGradleKotlinDslJarName(it) }
        }

    private
    val gradleJars by lazy {
        classPathRegistry.getClassPath(gradleApiNotation.name).asFiles
    }

    private
    val cachedScopeCompilationClassPath = ConcurrentHashMap<ClassLoaderScope, ClassPath>()

    private
    val cachedClassLoaderClassPath = ClassLoaderClassPathCache()
}


private
val gradleApiNotation = DependencyFactoryInternal.ClassPathNotation.GRADLE_API


private
fun isKotlinJar(name: String): Boolean =
    name.startsWith("kotlin-stdlib-")
        || name.startsWith("kotlin-reflect-")


private
class ClassLoaderClassPathCache {

    private
    val cachedClassPaths = hashMapOf<ClassLoader, Set<File>>()

    fun of(classLoader: ClassLoader): Set<File> =
        cachedClassPaths.getOrPut(classLoader) {
            classPathOf(classLoader)
        }

    private
    fun classPathOf(classLoader: ClassLoader): Set<File> {
        val classPathFiles = mutableSetOf<File>()

        object : ClassLoaderVisitor() {
            override fun visitClassPath(classPath: Array<URL>) {
                classPath.forEach { url ->
                    if (url.protocol == "file") {
                        classPathFiles.add(fileFrom(url))
                    }
                }
            }

            override fun visitParent(classLoader: ClassLoader) {
                classPathFiles.addAll(of(classLoader))
            }
        }.visit(classLoader)

        return classPathFiles
    }

    private
    fun fileFrom(url: URL) = File(toURI(url))
}


private
fun toURI(url: URL): URI =
    try {
        url.toURI()
    } catch (e: URISyntaxException) {
        URL(
            url.protocol,
            url.host,
            url.port,
            url.file.replace(" ", "%20")
        ).toURI()
    }
