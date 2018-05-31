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

package org.gradle.kotlin.dsl.build.plugins

import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.FileTree
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar

import org.gradle.kotlin.dsl.*

import org.gradle.kotlin.dsl.build.tasks.GenerateKotlinDslApiExtensions
import org.gradle.kotlin.dsl.build.tasks.GenerateParameterNamesIndexProperties

import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile


class KotlinDslJavaApiExtensionsPlugin : Plugin<Project> {

    override fun apply(project: Project): Unit = project.run {

        apply<GeneratedSourcesLayoutPlugin>()
        apply(plugin = "kotlin")

        val kotlinDslApiExtensions = project.container(KotlinDslApiExtensionsSet::class.java) { name ->
            KotlinDslApiExtensionsSet(project, name)
        }

        extensions.add(
            typeOf<NamedDomainObjectContainer<KotlinDslApiExtensionsSet>>(),
            "kotlinDslApiExtensions",
            kotlinDslApiExtensions)

        afterEvaluate {
            kotlinDslApiExtensions.all { extensionsSet ->
                configureProjectFor(extensionsSet)
            }
        }
    }

    private
    fun Project.configureProjectFor(extensionsSet: KotlinDslApiExtensionsSet) =
        createKotlinDslExtensionsSourceSetFor(extensionsSet.sourceSet.get()).let { extensionsSourceSet ->
            registerParameterNamesIndexTaskFor(extensionsSet).let { parameterNamesTask ->
                registerKotlinDslApiExtensionsTaskFor(extensionsSet, extensionsSourceSet, parameterNamesTask)
            }
        }

    private
    fun Project.createKotlinDslExtensionsSourceSetFor(sourceSet: SourceSet) =
        java.sourceSets.create("${sourceSet.name}KotlinDslExtensions") {
            it.compileClasspath += sourceSet.compileClasspath + sourceSet.output.classesDirs
        }.also { extensionsSourceSet ->
            dependencies {
                extensionsSourceSet.compileClasspathConfigurationName(kotlin("stdlib-jdk8"))
            }
            tasks {
                getByName<KotlinCompile>(extensionsSourceSet.getCompileTaskName("kotlin")) {
                    kotlinOptions {
                        jvmTarget = "1.8"
                        freeCompilerArgs += "-Xjsr305=strict"
                    }
                }
                getByName<Jar>("jar") {
                    from(extensionsSourceSet.kotlin.sourceDirectories)
                    from(extensionsSourceSet.output)
                    dependsOn(extensionsSourceSet.classesTaskName)
                }
            }
        }

    private
    fun Project.registerParameterNamesIndexTaskFor(
        extensionsSet: KotlinDslApiExtensionsSet
    ): TaskProvider<GenerateParameterNamesIndexProperties> =

        tasks.createLater(
            extensionsSet.javaParameterNamesTaskName,
            GenerateParameterNamesIndexProperties::class.java
        ) {
            it.sources.from(extensionsSet.sources)
            it.classpath.from(extensionsSet.sourceSetCompileClasspath)
            it.outputFile.set(extensionsSet.javaParameterNamesOutputFile)
        }


    private
    fun Project.registerKotlinDslApiExtensionsTaskFor(
        extensionsSet: KotlinDslApiExtensionsSet,
        extensionsSourceSet: SourceSet,
        vararg parameterNamesTasks: TaskProvider<GenerateParameterNamesIndexProperties>
    ): TaskProvider<GenerateKotlinDslApiExtensions> =

        extensionsSet.sourceSet.get().let { sourceSet ->
            tasks.createLater(
                extensionsSet.kotlinDslApiExtensionsTaskName,
                GenerateKotlinDslApiExtensions::class.java
            ) {

                it.description = "${it.description} for '${sourceSet.name}' source set"

                it.nameComponents.set(extensionsSet.nameComponents)
                it.classes.from(extensionsSet.sourceSetJavaOutputDir)
                it.classpath.from(extensionsSet.sourceSetCompileClasspath)
                it.includes.set(extensionsSet.includes)
                it.excludes.set(extensionsSet.excludes)
                it.parameterNamesIndices.from(files(parameterNamesTasks.map { it.get() }))

                it.outputDirectory.set(generatedSourcesLayout.sourcesOutputDirFor(extensionsSourceSet, "kotlinDslExtensions"))

            }.also { task ->
                extensionsSourceSet.kotlin.srcDir(files(provider { task.get().outputDirectory }).apply { builtBy(task) })
            }
        }
}


open class KotlinDslApiExtensionsSet(

    private
    val project: Project,

    private
    val name: String

) : Named {

    override fun getName(): String =
        name

    val sourceSet = project.objects.property<SourceSet>().apply {
        set(project.provider { project.java.sourceSets[name] })
    }

    val includes = project.objects.listProperty<String>()

    val excludes = project.objects.listProperty<String>()

    internal
    val nameComponents
        get() = project.run {
            if (project == rootProject) listOf(project.name, sourceSet.get().name)
            else listOf(rootProject.name, project.path, sourceSet.get().name)
        }

    internal
    val sources = project.provider {
        sourceSet.get().java.sourceDirectories
            .map { project.fileTree(it) as FileTree }
            .reduce { acc, fileTree -> acc.plus(fileTree) }
            .matching {
                includes.get().takeIf { it.isNotEmpty() }?.let { includes ->
                    it.include(includes)
                }
                excludes.get().takeIf { it.isNotEmpty() }?.let { excludes ->
                    it.exclude(excludes)
                }
            }
    }

    internal
    val sourceSetCompileClasspath = project.provider {
        sourceSet.get().compileClasspath
    }

    internal
    val javaParameterNamesTaskName
        get() = sourceSet.get().getTaskName("generate", "javaParameterNamesIndex")

    internal
    val javaParameterNamesOutputFile =
        project.layout.buildDirectory.file(project.provider {
            "java-parameter-names/${sourceSet.get().name}/java-parameter-names.properties"
        })

    internal
    val kotlinDslApiExtensionsTaskName
        get() = sourceSet.get().getTaskName("generate", "kotlinDslApiExtensions")

    internal
    val sourceSetJavaOutputDir = project.provider {
        sourceSet.get().java.outputDir
    }
}


private
val Project.java
    get() = the<JavaPluginConvention>()


private
val SourceSet.kotlin
    get() = withConvention(KotlinSourceSet::class) { kotlin }
