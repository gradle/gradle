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
package org.gradle.api.plugins.scala;

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.FileTreeElement
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.tasks.DefaultScalaSourceSet
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.scala.ScalaCompile
import org.gradle.api.tasks.scala.ScalaDoc
import org.gradle.api.tasks.JavaExec

import javax.inject.Inject
import java.util.regex.Pattern

class ScalaBasePlugin implements Plugin<Project> {
    // public configurations
    static final String SCALA_TOOLS_CONFIGURATION_NAME = "scalaTools"
    static final String ZINC_CONFIGURATION_NAME = "zinc"

    private static final String DEFAULT_ZINC_VERSION = "0.2.0"
    private static final Pattern SCALA_LIBRARY_JAR_PATTERN = Pattern.compile("scala-library-(\\d.*).jar")

    private Project project
    private final FileResolver fileResolver

    @Inject
    ScalaBasePlugin(FileResolver fileResolver) {
        this.fileResolver = fileResolver
    }

    void apply(Project project) {
        this.project = project
        def javaPlugin = project.plugins.apply(JavaBasePlugin.class)

        project.configurations.add(SCALA_TOOLS_CONFIGURATION_NAME)
                .setVisible(false)
                .setDescription("The Scala tools libraries to be used for this Scala project.")
        project.configurations.add(ZINC_CONFIGURATION_NAME)
                .setVisible(false)
                .setDescription("The Zinc incremental compiler to be used for this Scala project.")

        configureCompileDefaults(javaPlugin)
        configureSourceSetDefaults(javaPlugin)
        configureScaladoc()
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
        def scalaCompile = project.tasks.add(taskName, ScalaCompile)
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
        def scalaConsole = project.tasks.add(taskName, JavaExec)
        scalaConsole.dependsOn(sourceSet.runtimeClasspath)
        scalaConsole.description = "Starts a Scala REPL with the $sourceSet.name runtime class path."
        scalaConsole.main = "scala.tools.nsc.MainGenericRunner"
        scalaConsole.classpath = project.configurations[SCALA_TOOLS_CONFIGURATION_NAME]
        scalaConsole.systemProperty("scala.usejavacp", true)
        scalaConsole.standardInput = System.in
        scalaConsole.conventionMapping.jvmArgs = { ["-classpath", sourceSet.runtimeClasspath.asPath] }
    }

    private void configureCompileDefaults(JavaBasePlugin javaPlugin) {
        project.tasks.withType(ScalaCompile.class) { ScalaCompile compile ->
            compile.conventionMapping.scalaClasspath = {
                def classpath = project.configurations[SCALA_TOOLS_CONFIGURATION_NAME]
                if (classpath.dependencies.empty) {
                    def scalaVersion = sniffScalaVersion(compile.classpath)
                    if (scalaVersion != null) {
                        classpath = project.configurations.detachedConfiguration(
                                new DefaultExternalModuleDependency("org.scala-lang", "scala-compiler", scalaVersion))
                    }
                }
                classpath
            }
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

    private String sniffScalaVersion(Iterable<File> classpath) {
        for (file in classpath) {
            def matcher = SCALA_LIBRARY_JAR_PATTERN.matcher(file.name)
            if (matcher.matches()) {
                return matcher.group(1)
            }
        }
        return null
    }

    private void configureScaladoc() {
        project.getTasks().withType(ScalaDoc.class) { ScalaDoc scalaDoc ->
            scalaDoc.conventionMapping.destinationDir = { project.file("$project.docsDir/scaladoc") }
            scalaDoc.conventionMapping.title = { project.extensions.getByType(ReportingExtension).apiDocTitle }
            scalaDoc.scalaClasspath = project.configurations[SCALA_TOOLS_CONFIGURATION_NAME]
        }
    }
}