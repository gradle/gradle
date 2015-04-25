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

import java.io.File;
import java.util.Collections;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Provides information related to the Mirah runtime(s) used in a project. Added by the
 * {@link org.gradle.api.plugins.mirah.MirahBasePlugin} as a project extension named {@code mirahRuntime}.
 *
 * <p>Example usage:
 *
 * <pre autoTested="">
 *     apply plugin: "mirah"
 *
 *     repositories {
 *         mavenCentral()
 *     }
 *
 *     dependencies {
 *         compile "org.mirah-lang:mirah-library:2.10.1"
 *     }
 *
 *     def mirahClasspath = mirahRuntime.inferMirahClasspath(configurations.compile)
 *     // The returned class path can be used to configure the 'mirahClasspath' property of tasks
 *     // such as 'MirahCompile' or 'MirahDoc', or to execute these and other Mirah tools directly.
 * </pre>
 */
@Incubating
public class MirahRuntime {
    private static final Pattern SCALA_JAR_PATTERN = Pattern.compile("mirah-(\\w.*?)-(\\d.*).jar");

    private final Project project;

    public MirahRuntime(Project project) {
        this.project = project;
    }

    /**
     * Searches the specified class path for a 'mirah-library' Jar, and returns a class path
     * containing a corresponding (same version) 'mirah-compiler' Jar and its dependencies.
     *
     * <p>If the (deprecated) 'mirahTools' configuration is explicitly configured, no repository
     * is declared for the project, no 'mirah-library' Jar is found on the specified class path,
     * or its version cannot be determined, a class path with the contents of the 'mirahTools'
     * configuration is returned.
     *
     * <p>The returned class path may be empty, or may fail to resolve when asked for its contents.
     *
     * @param classpath a class path containing a 'mirah-library' Jar
     * @return a class path containing a corresponding 'mirah-compiler' Jar and its dependencies
     */
    public FileCollection inferMirahClasspath(final Iterable<File> classpath) {
        // alternatively, we could return project.files(Runnable)
        // would differ in the following ways: 1. live (not sure if we want live here) 2. no autowiring (probably want autowiring here)
        return new LazilyInitializedFileCollection() {
            @Override
            public FileCollection createDelegate() {
                if (project.getRepositories().isEmpty()) {
                    throw new GradleException(String.format("Cannot infer Mirah class path because no repository is declared in %s", project));
                }

                File mirahLibraryJar = findMirahJar(classpath, "library");
                if (mirahLibraryJar == null) {
                    throw new GradleException(String.format("Cannot infer Mirah class path because no Mirah library Jar was found. "
                            + "Does %s declare dependency to mirah-library? Searched classpath: %s.", project, classpath));
                }

                String mirahVersion = getMirahVersion(mirahLibraryJar);
                if (mirahVersion == null) {
                    throw new AssertionError(String.format("Unexpectedly failed to parse version of Mirah Jar file: %s in %s", mirahLibraryJar, project));
                }

                return project.getConfigurations().detachedConfiguration(
                        new DefaultExternalModuleDependency("org.mirah-lang", "mirah-compiler", mirahVersion));
            }

            // let's override this so that delegate isn't created at autowiring time (which would mean on every build)
            @Override
            public TaskDependency getBuildDependencies() {
                if (classpath instanceof Buildable) {
                    return ((Buildable) classpath).getBuildDependencies();
                }
                return new TaskDependency() {
                    public Set<? extends Task> getDependencies(Task task) {
                        return Collections.emptySet();
                    }
                };
            }
        };
    }

    /**
     * Searches the specified class path for a Mirah Jar file (mirah-compiler, mirah-library,
     * mirah-jdbc, etc.) with the specified appendix (compiler, library, jdbc, etc.).
     * If no such file is found, {@code null} is returned.
     *
     * @param classpath the class path to search
     * @param appendix the appendix to search for
     * @return a Mirah Jar file with the specified appendix
     */
    @Nullable
    public File findMirahJar(Iterable<File> classpath, String appendix) {
        for (File file : classpath) {
            Matcher matcher = SCALA_JAR_PATTERN.matcher(file.getName());
            if (matcher.matches() && matcher.group(1).equals(appendix)) {
                return file;
            }
        }
        return null;
    }

    /**
     * Determines the version of a Mirah Jar file (mirah-compiler, mirah-library,
     * mirah-jdbc, etc.). If the version cannot be determined, or the file is not a Mirah
     * Jar file, {@code null} is returned.
     *
     * <p>Implementation note: The version is determined by parsing the file name, which
     * is expected to match the pattern 'mirah-[component]-[version].jar'.
     *
     * @param mirahJar a Mirah Jar file
     * @return the version of the Mirah Jar file
     */
    @Nullable
    public String getMirahVersion(File mirahJar) {
        Matcher matcher = SCALA_JAR_PATTERN.matcher(mirahJar.getName());
        return matcher.matches() ? matcher.group(2) : null;
    }
}
