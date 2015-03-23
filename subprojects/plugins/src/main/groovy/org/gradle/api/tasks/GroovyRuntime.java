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

import com.google.common.collect.Lists;
import org.gradle.api.*;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.collections.LazilyInitializedFileCollection;
import org.gradle.api.internal.plugins.GroovyJarFile;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Provides information related to the Groovy runtime(s) used in a project. Added by the
 * {@link org.gradle.api.plugins.GroovyBasePlugin} as a project extension named {@code groovyRuntime}.
 *
 * <p>Example usage:
 *
 * <pre autoTested="">
 *     apply plugin: "groovy"
 *
 *     repositories {
 *         mavenCentral()
 *     }
 *
 *     dependencies {
 *         compile "org.codehaus.groovy:groovy-all:2.1.2"
 *     }
 *
 *     def groovyClasspath = groovyRuntime.inferGroovyClasspath(configurations.compile)
 *     // The returned class path can be used to configure the 'groovyClasspath' property of tasks
 *     // such as 'GroovyCompile' or 'Groovydoc', or to execute these and other Groovy tools directly.
 * </pre>
 */
@Incubating
public class GroovyRuntime {
    private final Project project;

    public GroovyRuntime(Project project) {
        this.project = project;
    }

    /**
     * Searches the specified class path for Groovy Jars ({@code groovy(-indy)}, {@code groovy-all(-indy)})
     * and returns a corresponding class path for executing Groovy tools such as the Groovy compiler and Groovydoc tool.
     * The tool versions will match those of the Groovy Jars found. If no Groovy Jars are found on the specified class
     * path, a class path with the contents of the {@code groovy} configuration will be returned.
     *
     * <p>The returned class path may be empty, or may fail to resolve when asked for its contents.
     *
     * @param classpath a class path containing Groovy Jars
     * @return a corresponding class path for executing Groovy tools such as the Groovy compiler and Groovydoc tool
     */
    public FileCollection inferGroovyClasspath(final Iterable<File> classpath) {
        // alternatively, we could return project.files(Runnable)
        // would differ in at least the following ways: 1. live 2. no autowiring
        return new LazilyInitializedFileCollection() {
            @Override
            public FileCollection createDelegate() {
                GroovyJarFile groovyJar = findGroovyJarFile(classpath);
                if (groovyJar == null) {
                    throw new GradleException(String.format("Cannot infer Groovy class path because no Groovy Jar was found on class path: %s", classpath));
                }

                if (groovyJar.isGroovyAll()) {
                    return project.files(groovyJar.getFile());
                }

                if (project.getRepositories().isEmpty()) {
                    throw new GradleException("Cannot infer Groovy class path because no repository is declared for the project.");
                }

                String notation = groovyJar.getDependencyNotation();
                List<Dependency> dependencies = Lists.newArrayList();
                // project.getDependencies().create(String) seems to be the only feasible way to create a Dependency with a classifier
                dependencies.add(project.getDependencies().create(notation));
                if (groovyJar.getVersion().getMajor() >= 2) {
                    // add groovy-ant to bring in Groovydoc
                    dependencies.add(project.getDependencies().create(notation.replace(":groovy:", ":groovy-ant:")));
                }
                return project.getConfigurations().detachedConfiguration(dependencies.toArray(new Dependency[dependencies.size()]));
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

    private GroovyJarFile findGroovyJarFile(Iterable<File> classpath) {
        if (classpath == null) { return null; }
        for (File file : classpath) {
            GroovyJarFile groovyJar = GroovyJarFile.parse(file);
            if (groovyJar != null) { return groovyJar; }
        }
        return null;
    }
}
