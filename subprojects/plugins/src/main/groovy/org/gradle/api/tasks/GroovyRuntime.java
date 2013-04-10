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

import org.gradle.api.Incubating;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.plugins.GroovyJarFile;
import org.gradle.api.plugins.GroovyBasePlugin;

import java.io.File;
import java.util.List;

/**
 * Provides information related to the Groovy runtime(s) used in a project. Added by the
 * {@link GroovyBasePlugin} as a project extension named {@code groovyRuntime}.
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
     * path, the (deprecated and possibly empty) {@code groovy} configuration will be returned.
     *
     * <p>The returned file collection may be a {@link Configuration}, which may fail to resolve.
     *
     * @param classpath a class path containing Groovy Jars
     * @return a corresponding class path for executing Groovy tools such as the Groovy compiler and Groovydoc tool
     */
    public FileCollection inferGroovyClasspath(Iterable<File> classpath) {
        Configuration groovyConfiguration = project.getConfigurations().getByName(GroovyBasePlugin.GROOVY_CONFIGURATION_NAME);
        if (!groovyConfiguration.getDependencies().isEmpty()) { return groovyConfiguration; }

        GroovyJarFile groovyJar = findGroovyJarFile(classpath);
        if (groovyJar == null) { return groovyConfiguration; }

        if (groovyJar.isGroovyAll()) {
            return project.files(groovyJar.getFile());
        }

        if (project.getRepositories().isEmpty()) {
            return groovyConfiguration;
        }

        String notation = groovyJar.getDependencyNotation();
        List<Dependency> dependencies = Lists.newArrayList();
        // project.getDependencies().create(String) seems to be the only feasible way to create a Dependency with a classifier
        dependencies.add(project.getDependencies().create(notation));
        if (groovyJar.getVersion().getMajor() >= 2) {
            // add groovy-ant to bring in AntGroovyCompiler
            dependencies.add(project.getDependencies().create(notation.replace(":groovy:", ":groovy-ant:")));
        }
        return project.getConfigurations().detachedConfiguration(dependencies.toArray(new Dependency[dependencies.size()]));
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
