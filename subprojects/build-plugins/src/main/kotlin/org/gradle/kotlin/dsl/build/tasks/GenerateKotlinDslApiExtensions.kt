/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.kotlin.dsl.build.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

import org.gradle.internal.classloader.ClassLoaderFactory
import org.gradle.internal.classpath.DefaultClassPath

import org.gradle.kotlin.dsl.*

import org.gradle.kotlin.dsl.codegen.generateKotlinDslApiExtensionsSourceTo
import org.gradle.kotlin.dsl.support.serviceOf

import java.io.Closeable
import java.io.File
import java.util.*


/**
 * Generates Kotlin Extensions sources for API the Gradle Kotlin DSL.
 */
@CacheableTask
@Suppress("MemberVisibilityCanBePrivate")
open class GenerateKotlinDslApiExtensions : DefaultTask() {

    init {
        description = "Generates Gradle Kotlin DSL Kotlin Extensions sources from Java"
    }

    /**
     * Defaults to `false`, set to `true` to use the embedded Gradle Kotlin DSL provider.
     */
    @Input
    val isUseEmbeddedKotlinDslProvider = project.objects.property<Boolean>().apply {
        set(false)
    }

    @Input
    val nameComponents = project.objects.listProperty<String>().apply {
        set(listOf(name))
    }

    @Input
    val packageName = project.objects.property<String>()

    @InputFiles
    @Classpath
    val classes = project.files()

    @InputFiles
    @Classpath
    val classpath = project.files()

    @Input
    val includes = project.objects.listProperty<String>()

    @Input
    val excludes = project.objects.listProperty<String>()

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    val parameterNamesIndices = project.files()

    @OutputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    val outputDirectory = project.layout.directoryProperty()

    @TaskAction
    @Suppress("unused")
    internal
    fun generateKotlinDslApiExtensions() =
        if (isUseEmbeddedKotlinDslProvider.get()) runEmbedded()
        else runIsolated()

    private
    fun runEmbedded() =
        generateKotlinDslApiExtensionsSourceTo(
            outputFile,
            packageName.get(),
            classes.toList(),
            classpath.toList(),
            includes.get(),
            excludes.get(),
            parameterNamesIndices.toList()
        )

    private
    fun runIsolated() = project.run {
        val kotlinDslDependency = dependencies.create("org.gradle:gradle-kotlin-dsl:${versions["kotlin-dsl"]}")
        val kotlinDslClasspath = configurations.detachedConfiguration(kotlinDslDependency).files
        val loaderFactory = serviceOf<ClassLoaderFactory>()
        val loader = loaderFactory.createIsolatedClassLoader(DefaultClassPath.of(kotlinDslClasspath))
        try {
            val generatorClass = loader.loadClass("org.gradle.kotlin.dsl.codegen.ApiExtensionsGenerator")
            val generatorMethod = generatorClass.getMethod(
                "generateKotlinDslApiExtensionsSourceTo",
                File::class.java,
                String::class.java,
                List::class.java,
                List::class.java,
                List::class.java,
                List::class.java,
                List::class.java
            )
            generatorMethod.invoke(
                null,
                outputFile,
                packageName.get(),
                classes.toList(),
                classpath.toList(),
                includes.get(),
                excludes.get(),
                parameterNamesIndices.toList()
            )
        } finally {
            (loader as Closeable).close()
        }
    }

    private
    val outputFile
        get() = outputDirectory.file("$packageDirectory/$generatedFileName").get().asFile.apply {
            parentFile.mkdirs()
        }

    private
    val packageDirectory
        get() = packageName.get().replace('.', File.separatorChar)

    private
    val generatedFileName
        get() = nameComponents.get().joinToString("") { it.capitalizeNameComponent() }.let { name ->
            "Generated${name}KotlinDslApiExtensions.kt"
        }

    private
    fun String.capitalizeNameComponent() =
        split(Regex("[^a-zA-Z0-9']"))
            .filter { it.isNotEmpty() }
            .joinToString("") { "${it.first().toUpperCase()}${it.drop(1)}" }
}


internal
val versions: Map<String, String> by lazy {
    @Suppress("unchecked_cast")
    Properties().apply {
        val loader = GenerateKotlinDslApiExtensions::class.java.classLoader
        load(loader.getResourceAsStream("gradle-kotlin-dsl-build-plugins-versions.properties"))
    }.toMap() as Map<String, String>
}
