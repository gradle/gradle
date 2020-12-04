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

package gradlebuild.kotlin.tasks


import com.thoughtworks.qdox.JavaProjectBuilder
import com.thoughtworks.qdox.library.OrderedClassLibraryBuilder
import com.thoughtworks.qdox.model.JavaMethod

import gradlebuild.basics.util.ReproduciblePropertiesWriter

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.CompileClasspath
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

import org.gradle.internal.classloader.ClassLoaderFactory
import org.gradle.internal.classpath.DefaultClassPath

import java.io.File
import java.net.URLClassLoader
import javax.inject.Inject


@CacheableTask
abstract class ParameterNamesIndex : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:CompileClasspath
    abstract val classpath: ConfigurableFileCollection

    @get:OutputFile
    abstract val destinationFile: RegularFileProperty

    @get:Inject
    internal
    abstract val classLoaderFactory: ClassLoaderFactory

    @TaskAction
    fun generate(): Unit =
        destinationFile.get().asFile.let {
            if (sources.isEmpty) it.writeText("")
            else generateParameterNamesResource(sources.files, classpath.files, it)
        }

    private
    fun generateParameterNamesResource(sources: Set<File>, classpath: Set<File>, destinationFile: File) {
        val index = extractParameterNamesIndexFrom(sources, classpath)
        destinationFile.parentFile.mkdirs()
        ReproduciblePropertiesWriter.store(index, destinationFile)
    }

    private
    fun extractParameterNamesIndexFrom(sources: Set<File>, classpath: Set<File>): Map<String, String> =
        isolatedClassLoaderFor(classpath).use { loader ->
            javaProjectBuilderFor(loader)
                .sequenceOfJavaSourcesFrom(sources)
                .flatMap { it.classes.asSequence().filter { it.isPublic } }
                .flatMap { it.methods.asSequence().filter { it.isPublic && it.parameterTypes.isNotEmpty() } }
                .map { method -> fullyQualifiedSignatureOf(method) to commaSeparatedParameterNamesOf(method) }
                .toMap(linkedMapOf())
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
    fun javaProjectBuilderFor(loader: ClassLoader) =
        JavaProjectBuilder(OrderedClassLibraryBuilder().apply { appendClassLoader(loader) })

    private
    fun JavaProjectBuilder.sequenceOfJavaSourcesFrom(sources: Set<File>) =
        sources.asSequence().mapNotNull { addSource(it) }

    private
    fun isolatedClassLoaderFor(classpath: Set<File>) =
        classLoaderFactory.createIsolatedClassLoader(identityPath.path, DefaultClassPath.of(classpath)) as URLClassLoader
}
