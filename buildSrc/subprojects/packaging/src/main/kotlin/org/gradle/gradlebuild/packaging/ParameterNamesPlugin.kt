package org.gradle.gradlebuild.packaging

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.bundling.Jar

import org.gradle.internal.classloader.ClassLoaderFactory
import org.gradle.internal.classpath.DefaultClassPath

import accessors.base
import org.gradle.build.ReproduciblePropertiesWriter

import com.thoughtworks.qdox.JavaProjectBuilder
import com.thoughtworks.qdox.library.SortedClassLibraryBuilder

import org.gradle.kotlin.dsl.*
import org.gradle.kotlin.dsl.support.serviceOf


open class ParameterNamesExtension(project: Project, val jarTask: Jar) {

    val sources = project.objects.property<FileTree>()
    val classpath = project.objects.property<FileCollection>()
    val baseName = project.objects.property<String>()
}


open class ParameterNamesPlugin : Plugin<Project> {

    override fun apply(project: Project): Unit = project.run {

        val parameterNamesResource by tasks.creating(ParameterNamesResourceTask::class)
        val parameterNamesJar by tasks.creating(Jar::class)

        val extension = extensions.create("parameterNames", ParameterNamesExtension::class.java, project, parameterNamesJar)

        afterEvaluate {
            parameterNamesResource.apply {
                sources = extension.sources.get()
                classpath = extension.classpath.get()
                destinationFile.set(layout.buildDirectory.file("generated-resources/parameter-names/${extension.baseName.get()}.properties"))
            }
            parameterNamesJar.apply {
                from(parameterNamesResource)
                destinationDir = base.libsDir
                archiveName = "${extension.baseName.get()}-$version.jar"
            }
        }
    }
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

        val libraryBuilder = SortedClassLibraryBuilder()
        libraryBuilder.appendClassLoader(project.serviceOf<ClassLoaderFactory>().createIsolatedClassLoader(DefaultClassPath.of(classpath.files)))
        val qdoxBuilder = JavaProjectBuilder(libraryBuilder)
        val qdoxSources = sources.asSequence().mapNotNull { qdoxBuilder.addSource(it) }

        val properties = qdoxSources.flatMap { it.classes.asSequence().filter { it.isPublic } }
            .flatMap { it.methods.asSequence().filter { it.isPublic && it.parameterTypes.isNotEmpty() } }
            .map {
                val parameters = it.parameters.joinToString(separator = ",") { p ->
                    if (p.isVarArgs || p.javaClass.isArray) "${p.type.binaryName}[]"
                    else p.type.binaryName
                }
                val key = "${it.declaringClass.binaryName}.${it.name}($parameters)"
                val value = it.parameters.joinToString(separator = ",") { it.name }
                Pair(key, value)
            }.toMap(linkedMapOf())

        destinationFile.get().asFile.let { file ->
            file.parentFile.mkdirs()
            ReproduciblePropertiesWriter.store(properties, file)
        }
    }
}
