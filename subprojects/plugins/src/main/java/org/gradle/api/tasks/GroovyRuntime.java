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

import com.google.common.collect.Iterables;
import org.gradle.api.Buildable;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.collections.LazilyInitializedFileCollection;
import org.gradle.api.internal.plugins.GroovyJarFile;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.util.internal.VersionNumber;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Provides information related to the Groovy runtime(s) used in a project. Added by the
 * {@link org.gradle.api.plugins.GroovyBasePlugin} as a project extension named {@code groovyRuntime}.
 *
 * <p>Example usage:
 *
 * <pre class='autoTested'>
 *     plugins {
 *         id 'groovy'
 *     }
 *
 *     repositories {
 *         mavenCentral()
 *     }
 *
 *     dependencies {
 *         implementation "org.codehaus.groovy:groovy-all:2.1.2"
 *     }
 *
 *     def groovyClasspath = groovyRuntime.inferGroovyClasspath(configurations.compileClasspath)
 *     // The returned class path can be used to configure the 'groovyClasspath' property of tasks
 *     // such as 'GroovyCompile' or 'Groovydoc', or to execute these and other Groovy tools directly.
 * </pre>
 */
public class GroovyRuntime {
    private static final VersionNumber GROOVY_VERSION_WITH_SEPARATE_ANT = VersionNumber.parse("2.0");
    private static final VersionNumber GROOVY_VERSION_REQUIRING_TEMPLATES = VersionNumber.parse("2.5");

    private static final List<String> GROOVY3_LIBS = Arrays.asList(
        "groovy",
        "groovy-ant", "groovy-astbuilder", "groovy-console", "groovy-datetime", "groovy-dateutil",
        "groovy-nio", "groovy-sql", "groovy-test",
        "groovy-templates", "groovy-json", "groovy-xml", "groovy-groovydoc");

    private final Project project;

    public GroovyRuntime(Project project) {
        this.project = project;
    }

    /**
     * Searches the specified class path for Groovy Jars ({@code groovy(-indy)}, {@code groovy-all(-indy)}) and returns a corresponding class path for executing Groovy tools such as the Groovy
     * compiler and Groovydoc tool. The tool versions will match those of the Groovy Jars found. If no Groovy Jars are found on the specified class path, a class path with the contents of the {@code
     * groovy} configuration will be returned.
     *
     * <p>The returned class path may be empty, or may fail to resolve when asked for its contents.
     *
     * @param classpath a class path containing Groovy Jars
     * @return a corresponding class path for executing Groovy tools such as the Groovy compiler and Groovydoc tool
     */
    public FileCollection inferGroovyClasspath(final Iterable<File> classpath) {
        // alternatively, we could return project.getLayout().files(Runnable)
        // would differ in at least the following ways: 1. live 2. no autowiring
        return new LazilyInitializedFileCollection() {
            @Override
            public String getDisplayName() {
                return "Groovy runtime classpath";
            }

            @Override
            public FileCollection createDelegate() {
                GroovyJarFile groovyJar = findGroovyJarFile(classpath);
                if (groovyJar == null) {
                    throw new GradleException(String.format("Cannot infer Groovy class path because no Groovy Jar was found on class path: %s", Iterables.toString(classpath)));
                }

                if (groovyJar.isGroovyAll()) {
                    return project.getLayout().files(groovyJar.getFile());
                }

                VersionNumber groovyVersion = groovyJar.getVersion();
                // Groovy 3 does not have groovy-all yet we may have the required pieces on classpath via localGroovy()
                if (groovyVersion.getMajor() == 3) {
                    return inferGroovy3Classpath(groovyVersion);
                }

                String notation = groovyJar.getDependencyNotation();
                List<Dependency> dependencies = new ArrayList<>();
                // project.getDependencies().create(String) seems to be the only feasible way to create a Dependency with a classifier
                dependencies.add(project.getDependencies().create(notation));
                if (groovyVersion.compareTo(GROOVY_VERSION_WITH_SEPARATE_ANT) >= 0) {
                    // add groovy-ant to bring in Groovydoc for Groovy 2.0+
                    addGroovyDependency(notation, dependencies, "groovy-ant");
                }
                if (groovyVersion.compareTo(GROOVY_VERSION_REQUIRING_TEMPLATES) >= 0) {
                    // add groovy-templates for Groovy 2.5+
                    addGroovyDependency(notation, dependencies, "groovy-templates");
                }
                return project.getConfigurations().detachedConfiguration(dependencies.toArray(new Dependency[0]));
            }

            private void addGroovyDependency(String groovyDependencyNotion, List<Dependency> dependencies, String otherDependency) {
                dependencies.add(project.getDependencies().create(groovyDependencyNotion.replace(":groovy:", ":" + otherDependency + ":")));
            }

            private FileCollection inferGroovy3Classpath(VersionNumber groovyVersion) {
                Set<String> groovyJarNames = GROOVY3_LIBS.stream()
                    .map(libName -> libName + "-" + groovyVersion + ".jar")
                    .collect(Collectors.toSet());
                List<File> groovyClasspath = StreamSupport.stream(classpath.spliterator(), false)
                    .filter(f -> groovyJarNames.contains(f.getName()))
                    .collect(Collectors.toList());
                if (groovyClasspath.size() == GROOVY3_LIBS.size()) {
                    return project.getLayout().files(groovyClasspath);
                }

                return project.getConfigurations().detachedConfiguration(
                    GROOVY3_LIBS.stream()
                        .map(libName -> project.getDependencies().create("org.codehaus.groovy:" + libName + ":" + groovyVersion))
                        .toArray(Dependency[]::new)
                );
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

    @Nullable
    private static GroovyJarFile findGroovyJarFile(Iterable<File> classpath) {
        for (File file : classpath) {
            GroovyJarFile groovyJar = GroovyJarFile.parse(file);
            if (groovyJar != null) {
                return groovyJar;
            }
        }
        return null;
    }
}
