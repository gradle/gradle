/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.tasks

import org.gradle.api.Incubating
import org.gradle.api.Nullable
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency
import org.gradle.api.plugins.scala.ScalaBasePlugin

import java.util.regex.Pattern

/**
 * Provides information related to the Scala runtime(s) used in a project. Added by the
 * {@link ScalaBasePlugin} as a project extension named {@code scalaRuntime}.
 *
 * <p>Example usage:
 *
 * <pre autoTested="">
 *     apply plugin: "scala"
 *
 *     repositories {
 *         mavenCentral()
 *     }
 *
 *     dependencies {
 *         compile "org.scala-lang:scala-library:2.10.1"
 *     }
 *
 *     def scalaClasspath = scalaRuntime.inferScalaClasspath(configurations.compile)
 *     // The returned class path can be used to configure the 'scalaClasspath' property of tasks
 *     // such as 'ScalaCompile' or 'ScalaDoc', or to execute these and other Scala tools directly.
 * </pre>
 */
@Incubating
class ScalaRuntime {
    private static final Pattern SCALA_JAR_PATTERN = Pattern.compile("scala-(\\w.*?)-(\\d.*).jar")

    private final Project project

    ScalaRuntime(Project project) {
        this.project = project
    }

    /**
     * Searches the specified class path for a 'scala-library' Jar, and returns a class path
     * containing a corresponding (same version) 'scala-compiler' Jar and its dependencies.
     *
     * <p>If the (deprecated) 'scalaTools' configuration is explicitly configured, no repository
     * is declared for the project, no 'scala-library' Jar is found on the specified class path,
     * or its version cannot be determined, the 'scalaTools' configuration is returned, irrespective
     * of its contents.
     *
     * <p>The returned class path may be empty, or may fail to resolve when asked for its contents.
     *
     * @param classpath a class path containing a 'scala-library' Jar
     * @return a class path containing a corresponding 'scala-compiler' Jar and its dependencies
     */
    FileCollection inferScalaClasspath(Iterable<File> classpath) {
        def scalaTools = project.configurations[ScalaBasePlugin.SCALA_TOOLS_CONFIGURATION_NAME]
        if (!scalaTools.dependencies.empty || project.repositories.empty) { return scalaTools }

        def scalaLibraryJar = findScalaJar(classpath, "library")
        if (scalaLibraryJar == null) { return scalaTools }

        def scalaVersion = getScalaVersion(scalaLibraryJar)
        if (scalaVersion == null) {
            throw new AssertionError("Unexpectedly failed to determine version of Scala Jar file: $scalaLibraryJar")
        }

        return project.configurations.detachedConfiguration(
                new DefaultExternalModuleDependency("org.scala-lang", "scala-compiler", scalaVersion))
    }

    /**
     * Searches the specified class path for a Scala Jar file (scala-compiler, scala-library,
     * scala-jdbc, etc.) with the specified appendix (compiler, library, jdbc, etc.).
     * If no such file is found, {@code null} is returned.
     *
     * @param classpath the class path to search
     * @param appendix the appendix to search for
     * @return a Scala Jar file with the specified appendix
     */
    @Nullable
    File findScalaJar(Iterable<File> classpath, String appendix) {
        for (file in classpath) {
            def matcher = SCALA_JAR_PATTERN.matcher(file.name)
            if (matcher.matches() && matcher.group(1) == appendix) {
                return file
            }
        }
        return null
    }

    /**
     * Determines the version of a Scala Jar file (scala-compiler, scala-library,
     * scala-jdbc, etc.). If the version cannot be determined, or the file is not a Scala
     * Jar file, {@code null} is returned.
     *
     * <p>Implementation note: The version is determined by parsing the file name, which
     * is expected to match the pattern 'scala-[component]-[version].jar'.
     *
     * @param scalaJar a Scala Jar file
     * @return the version of the Scala Jar file
     */
    @Nullable
    String getScalaVersion(File scalaJar) {
        def matcher = SCALA_JAR_PATTERN.matcher(scalaJar.name)
        matcher.matches() ? matcher.group(2) : null
    }
}
