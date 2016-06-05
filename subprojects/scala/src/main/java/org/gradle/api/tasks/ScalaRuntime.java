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
package org.gradle.api.tasks;

import org.gradle.api.*;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency;
import org.gradle.api.internal.file.collections.LazilyInitializedFileCollection;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Provides information related to the Scala runtime(s) used in a project. Added by the
 * {@code org.gradle.api.plugins.scala.ScalaBasePlugin} as a project extension named {@code scalaRuntime}.
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
public class ScalaRuntime {
    private static final Pattern SCALA_JAR_PATTERN = Pattern.compile("scala-(\\w.*?)-(\\d.*).jar");

    private final Project project;

    public ScalaRuntime(Project project) {
        this.project = project;
    }

    /**
     * Searches the specified class path for a 'scala-library' Jar, and returns a class path
     * containing a corresponding (same version) 'scala-compiler' Jar and its dependencies.
     *
     * <p>The returned class path may be empty, or may fail to resolve when asked for its contents.
     *
     * @param classpath a class path containing a 'scala-library' Jar
     * @return a class path containing a corresponding 'scala-compiler' Jar and its dependencies
     */
    public FileCollection inferScalaClasspath(final Iterable<File> classpath) {
        // alternatively, we could return project.files(Runnable)
        // would differ in the following ways: 1. live (not sure if we want live here) 2. no autowiring (probably want autowiring here)
        return new LazilyInitializedFileCollection() {
            @Override
            public String getDisplayName() {
                return "Scala runtime classpath";
            }

            @Override
            public FileCollection createDelegate() {
                if (project.getRepositories().isEmpty()) {
                    throw new GradleException(String.format("Cannot infer Scala class path because no repository is declared in %s", project));
                }

                File scalaLibraryJar = findScalaJar(classpath, "library");
                if (scalaLibraryJar == null) {
                    throw new GradleException(String.format("Cannot infer Scala class path because no Scala library Jar was found. "
                            + "Does %s declare dependency to scala-library? Searched classpath: %s.", project, classpath));
                }

                String scalaVersion = getScalaVersion(scalaLibraryJar);
                if (scalaVersion == null) {
                    throw new AssertionError(String.format("Unexpectedly failed to parse version of Scala Jar file: %s in %s", scalaLibraryJar, project));
                }

                return project.getConfigurations().detachedConfiguration(new DefaultExternalModuleDependency("org.scala-lang", "scala-compiler", scalaVersion));
            }

            // let's override this so that delegate isn't created at autowiring time (which would mean on every build)
            @Override
            public void visitDependencies(TaskDependencyResolveContext context) {
                if (classpath instanceof Buildable) {
                    context.add(classpath);
                }
            }
        };
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
    public File findScalaJar(Iterable<File> classpath, String appendix) {
        for (File file : classpath) {
            Matcher matcher = SCALA_JAR_PATTERN.matcher(file.getName());
            if (matcher.matches() && matcher.group(1).equals(appendix)) {
                return file;
            }
        }
        return null;
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
    public String getScalaVersion(File scalaJar) {
        Matcher matcher = SCALA_JAR_PATTERN.matcher(scalaJar.getName());
        return matcher.matches() ? matcher.group(2) : null;
    }
}
