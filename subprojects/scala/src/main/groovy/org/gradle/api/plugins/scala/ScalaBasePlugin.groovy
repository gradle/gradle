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

import org.gradle.api.Incubating
import org.gradle.api.Nullable;
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
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
    private static final Pattern SCALA_JAR_PATTERN = Pattern.compile("scala-(\\w.*?)-(\\d.*).jar")

    private Project project
    private final FileResolver fileResolver

    @Inject
    ScalaBasePlugin(FileResolver fileResolver) {
        this.fileResolver = fileResolver
    }

    /**
     * Infers a Scala compiler class path (containing a 'scala-compiler' Jar and its dependencies)
     * based on the 'scala-library' Jar found on the specified class path.
     *
     * <p>Falls back to returning the 'scalaTools' configuration if one of the following holds:
     *
     * <ol>
     *     <li>The 'scalaTools' configuration is explicitly configured (ie. has dependencies declared).
     *         This is important for backwards compatibility.</li>
     *     <li>No repository is declared for the project.</li>
     *     <li>A 'scala-library' Jar cannot be found on the specified class path, or its
     *         version cannot be determined.</li>
     * </ol>
     *
     * Note that the returned class path may be empty, or may fail to resolve when asked for its contents.
     * If this happens at task execution time, it should usually be treated as a configuration error on part of the user.
     *
     * @param classpath a class path (supposedly) containing a 'scala-library' Jar
     * @return a Scala compiler class path
     */
    @Incubating
    FileCollection inferScalaCompilerClasspath(Iterable<File> classpath) {
        def scalaTools = project.configurations[SCALA_TOOLS_CONFIGURATION_NAME]
        if (!scalaTools.dependencies.empty || project.repositories.empty) return scalaTools

        def scalaLibraryJar = findScalaJar(classpath, "library")
        if (scalaLibraryJar == null) return scalaTools

        def scalaVersion = getScalaVersion(scalaLibraryJar)
        if (scalaVersion == null) {
            throw new AssertionError("Unexpectedly failed to determine version of Scala Jar file: $scalaLibraryJar")
        }

        return project.configurations.detachedConfiguration(
                new DefaultExternalModuleDependency("org.scala-lang", "scala-compiler", scalaVersion))
    }

    /**
     * Searches the specified class path for a Scala Jar file matching the specified
     * module (compiler, library, jdbc, etc.).
     *
     * @param classpath the class path to search
     * @param module the module to search for
     * @return a matching Scala Jar file, or {@code null} if no match was found
     */
    @Nullable
    @Incubating
    File findScalaJar(Iterable<File> classpath, String module) {
        for (file in classpath) {
            def matcher = SCALA_JAR_PATTERN.matcher(file.name)
            if (matcher.matches() && matcher.group(1) == module) {
                return file
            }
        }
        return null
    }

    /**
     * Determines the version of a Scala Jar file (scala-compiler, scala-library, scala-jdbc, etc.).
     * If the version cannot be determined, {@code null} is returned.
     *
     * <p>Implementation note: The version is determined by parsing the file name, which
     * is expected to match the pattern 'scala-[component]-[version].jar'.
     *
     * @param scalaJar a Scala Jar file
     * @return the version of the Jar file
     */
    @Nullable
    @Incubating
    String getScalaVersion(File scalaJar) {
        def matcher = SCALA_JAR_PATTERN.matcher(scalaJar.name)
        matcher.matches() ? matcher.group(2) : null
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

        configureCompileDefaults()
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
        scalaConsole.conventionMapping.classpath = { inferScalaCompilerClasspath(sourceSet.runtimeClasspath) }
        scalaConsole.systemProperty("scala.usejavacp", true)
        scalaConsole.standardInput = System.in
        scalaConsole.conventionMapping.jvmArgs = { ["-classpath", sourceSet.runtimeClasspath.asPath] }
    }

    private void configureCompileDefaults() {
        project.tasks.withType(ScalaCompile.class) { ScalaCompile compile ->
            compile.conventionMapping.scalaClasspath = { inferScalaCompilerClasspath(compile.classpath) }
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
            scalaDoc.conventionMapping.scalaClasspath = { inferScalaCompilerClasspath(scalaDoc.classpath) }
        }
    }
}