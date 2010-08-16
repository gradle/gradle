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
package org.gradle.api.artifacts.dsl;

import groovy.lang.Closure;
import org.gradle.api.artifacts.Dependency;

import java.util.Map;

/**
 * <p>A {@code DependencyHandler} is used to declare artifact dependencies. Artifact dependencies are grouped into
 * configurations (see {@link org.gradle.api.artifacts.Configuration}), and a given dependency declarations is always
 * attached to a single configuration.</p>
 *
 * <p>To declare a specific dependency for a configuration you can use the following syntax:</p>
 *
 * <pre>
 * dependencies {
 *     <i>configurationName</i> <i>dependencyNotation1</i>, <i>dependencyNotation2</i>, ...
 * }
 * </pre>
 *
 * <p>or, to configure a dependency when it is declared, you can additionally pass a configuration closure:</p>
 *
 * <pre>
 * dependencies {
 *     <i>configurationName</i> <i>dependencyNotation</i> {
 *         <i>configStatement1</i>
 *         <i>configStatement2</i>
 *     }
 * }
 * </pre>
 *
 * <p>There are several supported dependency notations. These are described below. For each dependency declared this
 * way, a {@link Dependency} object is created. You can use this object to query or further configure the
 * dependency.</p>
 *
 * <p>You can also always add instances of
 * {@link org.gradle.api.artifacts.Dependency} directly:</p>
 *
 * <code><i>configurationName</i> &lt;instance&gt;</code>
 *
 * <h2>External Modules</h2>
 *
 * <p>There are 2 notations supported for declaring a dependency on an external module. One is a string notation:</p>
 *
 * <code><i>configurationName</i> "<i>group</i>:<i>name</i>:<i>version</i>:<i>classifier</i>"</code>
 *
 * <p>The other is a map notation:</p>
 *
 * <code><i>configurationName</i> group: <i>group</i>:, name: <i>name</i>, version: <i>version</i>, classifier:
 * <i>classifier</i></code>
 *
 * <p>In both notations, all properties, except name, are optional.</p>
 *
 * <p>External dependencies are represented by a {@link
 * org.gradle.api.artifacts.ExternalModuleDependency}.</p>
 *
 * <h2>Client Modules</h2>
 *
 * <p>To add a client module to a configuration you can use the notation:</p>
 *
 * <pre>
 * <i>configurationName</i> module(<i>moduleNotation</i>) {
 *     <i>module dependencies</i>
 * }
 * </pre>
 *
 * The module notation is the same as the dependency notations described above, except that the classifier property is
 * not available. Client modules are represented using a {@link org.gradle.api.artifacts.ClientModule}.
 *
 * <h2>Projects</h2>
 *
 * <p>To add a project dependency, you use the following notation</p>
 *
 * <code><i>configurationName</i> project(':someProject')</code>
 *
 * <p>Project dependencies are represented using a {@link org.gradle.api.artifacts.ProjectDependency}.</p>
 *
 * <h2>Files</h2>
 *
 * <p>You can also add a dependency using a {@link org.gradle.api.file.FileCollection}:</p>
 * <code><i>configurationName</i> files('a file')</code>
 *
 * <p>File dependencies are represented using a {@link org.gradle.api.artifacts.SelfResolvingDependency}.</p>
 *
 * @author Hans Dockter
 */
public interface DependencyHandler {
    /**
     * Adds a dependency to the given configuration.
     *
     * @param configurationName The name of the configuration.
     * @param dependencyNotation The dependency notation, in one of the notations described above.
     * @return The dependency.
     */
    Dependency add(String configurationName, Object dependencyNotation);

    /**
     * Adds a dependency to the given configuration, and configures the dependency using the given closure.
     *
     * @param configurationName The name of the configuration.
     * @param dependencyNotation The dependency notation, in one of the notations described above.
     * @param configureClosure The closure to use to configure the dependency.
     * @return The dependency.
     */
    Dependency add(String configurationName, Object dependencyNotation, Closure configureClosure);

    /**
     * Creates a dependency on a client module.
     *
     * @param notation The module notation, in one of the notations described above.
     * @return The dependency.
     */
    Dependency module(Object notation);

    /**
     * Creates a dependency on a client module. The dependency is configured using the given closure before it is
     * returned.
     *
     * @param notation The module notation, in one of the notations described above.
     * @param configureClosure The closure to use to configure the dependency.
     * @return The dependency.
     */
    Dependency module(Object notation, Closure configureClosure);

    /**
     * Creates a dependency on a project.
     *
     * @param notation The project notation, in one of the notations described above.
     * @return The dependency.
     */
    Dependency project(Map notation);
    
    /**
     * Creates a dependency on the API of the current version of Gradle.
     *
     * @return The dependency.
     */
    Dependency gradleApi();
}
