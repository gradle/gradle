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

@Incubating
class ScalaRuntime {
    private static final Pattern SCALA_JAR_PATTERN = Pattern.compile("scala-(\\w.*?)-(\\d.*).jar")

    private final Project project

    ScalaRuntime(Project project) {
        this.project = project
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
}
