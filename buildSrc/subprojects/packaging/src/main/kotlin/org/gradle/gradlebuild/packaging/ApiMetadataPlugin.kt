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

package org.gradle.gradlebuild.packaging

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.WriteProperties
import org.gradle.api.tasks.bundling.Jar

import org.gradle.internal.classloader.ClassLoaderFactory
import org.gradle.internal.classpath.DefaultClassPath

import accessors.base
import org.gradle.build.ReproduciblePropertiesWriter

import com.thoughtworks.qdox.JavaProjectBuilder
import com.thoughtworks.qdox.library.SortedClassLibraryBuilder
import com.thoughtworks.qdox.model.JavaMethod

import org.gradle.kotlin.dsl.*
import org.gradle.kotlin.dsl.support.serviceOf


open class ApiMetadataExtension(project: Project, val jarTask: Provider<Jar>) {

    val sources = project.objects.property<FileTree>()
    val includes = project.objects.listProperty<String>()
    val excludes = project.objects.listProperty<String>()
    val classpath = project.objects.property<FileCollection>()
}


/**
 * Generates a JAR with Gradle API metadata resources.
 *
 * Include and exclude patterns for the Gradle API.
 * Parameter names for the Gradle API.
 */
open class ApiMetadataPlugin : Plugin<Project> {

    override fun apply(project: Project): Unit = project.run {

        val jarTask =
            tasks.register("apiMetadataJar", Jar::class.java)

        val extension =
            extensions.create("apiMetadata", ApiMetadataExtension::class.java, project, jarTask)

        val apiDeclarationTask =
            tasks.register("apiDeclarationResource", WriteProperties::class.java) {
                property("includes", extension.includes.get().joinToString(":"))
                property("excludes", extension.excludes.get().joinToString(":"))
                outputFile = generatedPropertiesFileFor("gradle-api-declaration").get().asFile
            }

        val apiParameterNamesTask =
            tasks.register("apiParameterNamesResource", ParameterNamesResourceTask::class.java) {
                sources = extension.sources.get().matching {
                    include(extension.includes.get())
                    exclude(extension.excludes.get())
                }
                classpath = extension.classpath.get()
                destinationFile.set(generatedPropertiesFileFor("gradle-api-parameter-names"))
            }

        jarTask.configure {
            description = "Assembles the API metadata jar."
            group = "build"
            from(apiDeclarationTask)
            from(apiParameterNamesTask)
            destinationDir = layout.buildDirectory.file("$name").get().asFile
            archiveName = "gradle-api-metadata-$version.jar"
        }

        // Required as this is overridden by :distributions build script
        afterEvaluate {
            jarTask.configure {
                destinationDir = base.libsDir
            }
        }
    }

    private
    fun Project.generatedPropertiesFileFor(name: String) =
        layout.buildDirectory.file("generated-resources/$name/$name.properties")
}


@CacheableTask
open class ParameterNamesResourceTask : DefaultTask() {

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    lateinit var sources: FileTree

    @InputFiles
    @Classpath
    lateinit var classpath: FileCollection

    @OutputFile
    @PathSensitive(PathSensitivity.NONE)
    val destinationFile = project.layout.fileProperty()

    @TaskAction
    fun generate() {

        val qdoxBuilder = JavaProjectBuilder(sortedClassLibraryBuilderWithClassLoaderFor(classpath))
        val qdoxSources = sources.asSequence().mapNotNull { qdoxBuilder.addSource(it) }

        val properties = qdoxSources
            .flatMap { it.classes.asSequence().filter { it.isPublic } }
            .flatMap { it.methods.asSequence().filter { it.isPublic && it.parameterTypes.isNotEmpty() } }
            .map { method ->
                fullyQualifiedSignatureOf(method) to commaSeparatedParameterNamesOf(method)
            }.toMap(linkedMapOf())

        write(properties)
    }

    private
    fun write(properties: LinkedHashMap<String, String>) {
        destinationFile.get().asFile.let { file ->
            file.parentFile.mkdirs()
            ReproduciblePropertiesWriter.store(properties, file)
        }
    }

    private
    fun fullyQualifiedSignatureOf(method: JavaMethod): String =
        "${method.declaringClass.binaryName}.${method.name}(${signatureOf(method)})"

    private
    fun signatureOf(method: JavaMethod): String =
        method.parameters.joinToString(separator = ",") { p ->
            if (p.isVarArgs || p.javaClass.isArray) "${p.type.binaryName}[]"
            else p.type.binaryName
        }

    private
    fun commaSeparatedParameterNamesOf(method: JavaMethod) =
        method.parameters.joinToString(separator = ",") { it.name }

    private
    fun sortedClassLibraryBuilderWithClassLoaderFor(classpath: FileCollection): SortedClassLibraryBuilder =
        SortedClassLibraryBuilder().apply {
            appendClassLoader(isolatedClassLoaderFor(classpath))
        }

    private
    fun isolatedClassLoaderFor(classpath: FileCollection) =
        classLoaderFactory.createIsolatedClassLoader(DefaultClassPath.of(classpath.files))

    private
    val classLoaderFactory
        get() = project.serviceOf<ClassLoaderFactory>()
}
