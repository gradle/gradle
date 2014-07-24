/*
 * Copyright 2010 the original author or authors.
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

import groovy.lang.Closure;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.SourceDirectorySet;

/**
 * A {@code SourceSet} represents a logical group of Java source and resources.
 * <p>
 * See the example below how {@link SourceSet} 'main' is accessed and how the {@link SourceDirectorySet} 'java'
 * is configured to exclude some package from compilation.
 *
 * <pre autoTested=''>
 * apply plugin: 'java'
 *
 * sourceSets {
 *   main {
 *     java {
 *       exclude 'some/unwanted/package/**'
 *     }
 *   }
 * }
 * </pre>
 */
public interface SourceSet {
    /**
     * The name of the main source set.
     */
    String MAIN_SOURCE_SET_NAME = "main";

    /**
     * The name of the test source set.
     */
    String TEST_SOURCE_SET_NAME = "test";

    /**
     * Returns the name of this source set.
     *
     * @return The name. Never returns null.
     */
    String getName();

    /**
     * Returns the classpath used to compile this source.
     *
     * @return The classpath. Never returns null.
     */
    FileCollection getCompileClasspath();

    /**
     * Sets the classpath used to compile this source.
     *
     * @param classpath The classpath. Should not be null.
     */
    void setCompileClasspath(FileCollection classpath);

    /**
     * Returns the classpath used to execute this source.
     *
     * @return The classpath. Never returns null.
     */
    FileCollection getRuntimeClasspath();

    /**
     * Sets the classpath used to execute this source.
     *
     * @param classpath The classpath. Should not be null.
     */
    void setRuntimeClasspath(FileCollection classpath);

   /**
     * {@link SourceSetOutput} is a {@link FileCollection} of all output directories (compiled classes, processed resources, etc.)
     *  and it provides means to configure the default output dirs and register additional output dirs. See examples in {@link SourceSetOutput}
     *
     * @return The output dirs, as a {@link SourceSetOutput}.
     */
    SourceSetOutput getOutput();

    /**
     * Registers a set of tasks which are responsible for compiling this source set into the classes directory. The
     * paths are evaluated as per {@link org.gradle.api.Task#dependsOn(Object...)}.
     *
     * @param taskPaths The tasks which compile this source set.
     * @return this
     */
    SourceSet compiledBy(Object... taskPaths);

    /**
     * Returns the non-Java resources which are to be copied into the resources output directory.
     *
     * @return the resources. Never returns null.
     */
    SourceDirectorySet getResources();

    /**
     * Configures the non-Java resources for this set.
     *
     * <p>The given closure is used to configure the {@link SourceDirectorySet} which contains the resources.
     *
     * @param configureClosure The closure to use to configure the resources.
     * @return this
     */
    SourceSet resources(Closure configureClosure);

    /**
     * Returns the Java source which is to be compiled by the Java compiler into the class output directory.
     *
     * @return the Java source. Never returns null.
     */
    SourceDirectorySet getJava();

    /**
     * Configures the Java source for this set.
     *
     * <p>The given closure is used to configure the {@link SourceDirectorySet} which contains the Java source.
     *
     * @param configureClosure The closure to use to configure the Java source.
     * @return this
     */
    SourceSet java(Closure configureClosure);

    /**
     * All Java source files for this source set. This includes, for example, source which is directly compiled, and
     * source which is indirectly compiled through joint compilation.
     *
     * @return the Java source. Never returns null.
     */
    SourceDirectorySet getAllJava();

    /**
     * All source files for this source set.
     *
     * @return the source. Never returns null.
     */
    SourceDirectorySet getAllSource();

    /**
     * Returns the name of the classes task for this source set.
     *
     * @return The task name. Never returns null.
     */
    String getClassesTaskName();

    /**
     * Returns the name of the resource process task for this source set.
     *
     * @return The task name. Never returns null.
     */
    String getProcessResourcesTaskName();

    /**
     * Returns the name of the compile Java task for this source set.
     *
     * @return The task name. Never returns null.
     */
    String getCompileJavaTaskName();

    /**
     * Returns the name of a compile task for this source set.
     *
     * @param language The language to be compiled.
     * @return The task name. Never returns null.
     */
    String getCompileTaskName(String language);

    /**
     * Returns the name of the Jar task for this source set.
     *
     * @return The task name. Never returns null.
     */
    String getJarTaskName();

    /**
     * Returns the name of a task for this source set.
     *
     * @param verb The action, may be null.
     * @param target The target, may be null
     * @return The task name, generally of the form ${verb}${name}${noun}
     */
    String getTaskName(String verb, String target);

    /**
     * Returns the name of the compile configuration for this source set.
     * @return The configuration name
     */
    String getCompileConfigurationName();

    /**
     * Returns the name of the runtime configuration for this source set.
     * @return The runtime configuration name
     */
    String getRuntimeConfigurationName();
}
