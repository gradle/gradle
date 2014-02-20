/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.plugins.scala

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.FileTreeElement
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.tasks.DefaultScalaSourceSet
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.tasks.ScalaRuntime
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.scala.ScalaCompile
import org.gradle.api.tasks.scala.ScalaDoc
import org.gradle.api.tasks.JavaExec
import org.gradle.util.DeprecationLogger

import javax.inject.Inject

class ScalaBasePlugin implements Plugin<Project> {
    /**
     * The name of the configuration holding the Scala compiler and tools.
     *
     * @deprecated Typically, usages of {@code scalaTools} can simply be removed,
     * and the Scala tools libraries to be used will be inferred from the Scala
     * library found on the regular (compile) class path. In some cases, it may
     * be necessary to additionally configure the {@code scalaClasspath} property
     * of {@code ScalaCompile} and {@code ScalaDoc} tasks.
     */
    static final String SCALA_TOOLS_CONFIGURATION_NAME = "scalaTools"

    static final String ZINC_CONFIGURATION_NAME = "zinc"

    static final String SCALA_RUNTIME_EXTENSION_NAME = "scalaRuntime"

    private static final String DEFAULT_ZINC_VERSION = "0.3.0"

    private final FileResolver fileResolver

    private Project project
    private ScalaRuntime scalaRuntime

    @Inject
    ScalaBasePlugin(FileResolver fileResolver) {
        this.fileResolver = fileResolver
    }

    void apply(Project project) {
        this.project = project
        def javaPlugin = project.plugins.apply(JavaBasePlugin.class)

        configureConfigurations(project)
        configureScalaRuntimeExtension()
        configureCompileDefaults()
        configureSourceSetDefaults(javaPlugin)
        configureScaladoc()
    }

    private void configureConfigurations(Project project) {
        def scalaToolsConfiguration = project.configurations.create(SCALA_TOOLS_CONFIGURATION_NAME)
                .setVisible(false)
                .setDescription("The Scala tools libraries to be used for this Scala project. (Deprecated)")
        deprecateScalaToolsConfiguration(scalaToolsConfiguration)

        project.configurations.create(ZINC_CONFIGURATION_NAME)
                .setVisible(false)
                .setDescription("The Zinc incremental compiler to be used for this Scala project.")
    }

    private void deprecateScalaToolsConfiguration(Configuration scalaConfiguration) {
        scalaConfiguration.dependencies.whenObjectAdded {
            DeprecationLogger.nagUserOfDiscontinuedConfiguration(SCALA_TOOLS_CONFIGURATION_NAME,
                    "Typically, usages of 'scalaTools' can simply be removed, and the Scala tools libraries " +
                    "to be used will be inferred from the Scala library found on the regular (compile) class path. " +
                    "In some cases, it may be necessary to additionally configure the 'scalaClasspath' property of " +
                    "ScalaCompile and ScalaDoc tasks.");
        }
    }

    private void configureScalaRuntimeExtension() {
        scalaRuntime = project.extensions.create(SCALA_RUNTIME_EXTENSION_NAME, ScalaRuntime, project)
    }

    private void configureSourceSetDefaults(JavaBasePlugin javaPlugin) {
        project.convention.getPlugin(JavaPluginConvention.class).sourceSets.all { SourceSet sourceSet ->
            sourceSet.convention.plugins.scala = new DefaultScalaSourceSet(sourceSet.displayName, fileResolver)
            sourceSet.scala.srcDir { project.file("src/$sourceSet.name/scala") }
            sourceSet.allJava.source(sourceSet.scala)
            sourceSet.allSource.source(sourceSet.scala)
            sourceSet.resources.filter.exclude { FileTreeElement element -> sourceSet.scala.contains(element.file) }

            configureScalaCompile(javaPlugin, sourceSet)
            configureScalaConsole(sourceSet)
        }
    }

    private void configureScalaCompile(JavaBasePlugin javaPlugin, SourceSet sourceSet) {
        def taskName = sourceSet.getCompileTaskName('scala')
        def scalaCompile = project.tasks.create(taskName, ScalaCompile)
        scalaCompile.dependsOn sourceSet.compileJavaTaskName
        javaPlugin.configureForSourceSet(sourceSet, scalaCompile)
        scalaCompile.description = "Compiles the $sourceSet.scala."
        scalaCompile.source = sourceSet.scala
        project.tasks[sourceSet.classesTaskName].dependsOn(taskName)

        // cannot use convention mapping because the resulting object won't be serializable
        // cannot compute at task execution time because we need association with source set
        project.gradle.projectsEvaluated {
            scalaCompile.scalaCompileOptions.incrementalOptions.with {
                if (!analysisFile) {
                    analysisFile = new File("$project.buildDir/tmp/scala/compilerAnalysis/${scalaCompile.name}.analysis")
                }
                if (!publishedCode) {
                    def jarTask = project.tasks.findByName(sourceSet.getJarTaskName())
                    publishedCode = jarTask?.archivePath
                }
            }
        }
    }

    private void configureScalaConsole(SourceSet sourceSet) {
        def taskName = sourceSet.getTaskName("scala", "Console")
        def scalaConsole = project.tasks.create(taskName, JavaExec)
        scalaConsole.dependsOn(sourceSet.runtimeClasspath)
        scalaConsole.description = "Starts a Scala REPL with the $sourceSet.name runtime class path."
        scalaConsole.main = "scala.tools.nsc.MainGenericRunner"
        scalaConsole.conventionMapping.classpath = { scalaRuntime.inferScalaClasspath(sourceSet.runtimeClasspath) }
        scalaConsole.systemProperty("scala.usejavacp", true)
        scalaConsole.standardInput = System.in
        scalaConsole.conventionMapping.jvmArgs = { ["-classpath", sourceSet.runtimeClasspath.asPath] }
    }

    private void configureCompileDefaults() {
        project.tasks.withType(ScalaCompile.class) { ScalaCompile compile ->
            compile.conventionMapping.scalaClasspath = { scalaRuntime.inferScalaClasspath(compile.classpath) }
            compile.conventionMapping.zincClasspath = {
                def config = project.configurations[ZINC_CONFIGURATION_NAME]
                if (!compile.scalaCompileOptions.useAnt && config.dependencies.empty) {
                    project.dependencies {
                        zinc("com.typesafe.zinc:zinc:$DEFAULT_ZINC_VERSION")
                    }
                }
                config
            }
        }
    }

    private void configureScaladoc() {
        project.tasks.withType(ScalaDoc) { ScalaDoc scalaDoc ->
            scalaDoc.conventionMapping.destinationDir = { project.file("$project.docsDir/scaladoc") }
            scalaDoc.conventionMapping.title = { project.extensions.getByType(ReportingExtension).apiDocTitle }
            scalaDoc.conventionMapping.scalaClasspath = { scalaRuntime.inferScalaClasspath(scalaDoc.classpath) }
        }
    }
}