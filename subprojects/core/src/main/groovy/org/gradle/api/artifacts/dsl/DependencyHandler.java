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
import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.query.ArtifactResolutionQuery;

import java.util.Map;

/**
 * <p>A {@code DependencyHandler} is used to declare dependencies. Dependencies are grouped into
 * configurations (see {@link org.gradle.api.artifacts.Configuration}).</p>
 *
 * <p>To declare a specific dependency for a configuration you can use the following syntax:</p>
 *
 * <pre>
 * dependencies {
 *     <i>configurationName</i> <i>dependencyNotation1</i>, <i>dependencyNotation2</i>, ...
 * }
 * </pre>
 *
 * <p>Example shows a basic way of declaring dependencies.
 * <pre autoTested=''>
 * apply plugin: 'java'
 * //so that we can use 'compile', 'testCompile' for dependencies
 *
 * dependencies {
 *   //for dependencies found in artifact repositories you can use
 *   //the group:name:version notation
 *   compile 'commons-lang:commons-lang:2.6'
 *   testCompile 'org.mockito:mockito:1.9.0-rc1'
 *
 *   //map-style notation:
 *   compile group: 'com.google.code.guice', name: 'guice', version: '1.0'
 *
 *   //declaring arbitrary files as dependencies
 *   compile files('hibernate.jar', 'libs/spring.jar')
 *
 *   //putting all jars from 'libs' onto compile classpath
 *   compile fileTree('libs')
 * }
 * </pre>
 *
 * <h2>Advanced dependency configuration</h2>
 * <p>To do some advanced configuration on a dependency when it is declared, you can additionally pass a configuration closure:</p>
 *
 * <pre>
 * dependencies {
 *     <i>configurationName</i>(<i>dependencyNotation</i>){
 *         <i>configStatement1</i>
 *         <i>configStatement2</i>
 *     }
 * }
 * </pre>
 *
 * Examples of advanced dependency declaration including:
 * <ul>
 * <li>Forcing certain dependency version in case of the conflict.</li>
 * <li>Excluding certain dependencies by name, group or both.
 *      More details about per-dependency exclusions can be found in
 *      docs for {@link org.gradle.api.artifacts.ModuleDependency#exclude(java.util.Map)}.</li>
 * <li>Avoiding transitive dependencies for certain dependency.</li>
 * </ul>
 *
 * <pre autoTested=''>
 * apply plugin: 'java' //so that I can declare 'compile' dependencies
 *
 * dependencies {
 *   compile('org.hibernate:hibernate:3.1') {
 *     //in case of versions conflict '3.1' version of hibernate wins:
 *     force = true
 *
 *     //excluding a particular transitive dependency:
 *     exclude module: 'cglib' //by artifact name
 *     exclude group: 'org.jmock' //by group
 *     exclude group: 'org.unwanted', module: 'iAmBuggy' //by both name and group
 *
 *     //disabling all transitive dependencies of this dependency
 *     transitive = false
 *   }
 * }
 * </pre>
 *
 * More examples of advanced configuration, useful when dependency module has multiple artifacts:
 * <ul>
 *   <li>Declaring dependency to a specific configuration of the module.</li>
 *   <li>Explicit specification of the artifact. See also {@link org.gradle.api.artifacts.ModuleDependency#artifact(groovy.lang.Closure)}.</li>
 * </ul>
 *
 * <pre autoTested=''>
 * apply plugin: 'java' //so that I can declare 'compile' dependencies
 *
 * dependencies {
 *   //configuring dependency to specific configuration of the module
 *   compile configuration: 'someConf', group: 'org.someOrg', name: 'someModule', version: '1.0'
 *
 *   //configuring dependency on 'someLib' module
 *   compile(group: 'org.myorg', name: 'someLib', version:'1.0') {
 *     //explicitly adding the dependency artifact:
 *     artifact {
 *       //useful when some artifact properties unconventional
 *       name = 'someArtifact' //artifact name different than module name
 *       extension = 'someExt'
 *       type = 'someType'
 *       classifier = 'someClassifier'
 *     }
 *   }
 * }
 * </pre>
 *
 * <h2>Dependency notations</h2>
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
 * <h3>External dependencies</h3>
 *
 * <p>There are two notations supported for declaring a dependency on an external module.
 * One is a string notation formatted this way:</p>
 *
 * <code><i>configurationName</i> "<i>group</i>:<i>name</i>:<i>version</i>:<i>classifier</i>@<i>extension</i>"</code>
 *
 * <p>The other is a map notation:</p>
 *
 * <code><i>configurationName</i> group: <i>group</i>:, name: <i>name</i>, version: <i>version</i>, classifier:
 * <i>classifier</i>, ext: <i>extension</i></code>
 *
 * <p>In both notations, all properties, except name, are optional.</p>
 *
 * <p>External dependencies are represented by a {@link
 * org.gradle.api.artifacts.ExternalModuleDependency}.</p>
 *
 * <pre autoTested=''>
 * apply plugin: 'java'
 * //so that we can use 'compile', 'testCompile' for dependencies
 *
 * dependencies {
 *   //for dependencies found in artifact repositories you can use
 *   //the string notation, e.g. group:name:version
 *   compile 'commons-lang:commons-lang:2.6'
 *   testCompile 'org.mockito:mockito:1.9.0-rc1'
 *
 *   //map notation:
 *   compile group: 'com.google.code.guice', name: 'guice', version: '1.0'
 * }
 * </pre>
 *
 * <h3>Project dependencies</h3>
 *
 * <p>To add a project dependency, you use the following notation:
 * <p><code><i>configurationName</i> project(':someProject')</code>
 *
 * <p>The notation <code>project(':projectA')</code> is similar to the syntax you use
 * when configuring a projectA in a multi-module gradle project.
 *
 * <p>By default, when you declare dependency to projectA, you actually declare dependency to the 'default' configuration of the projectA.
 * If you need to depend on a specific configuration of projectA, use map notation for projects:
 * <p><code><i>configurationName</i> project(path: ':projectA', configuration: 'someOtherConfiguration')</code>
 *
 * <p>Project dependencies are represented using a {@link org.gradle.api.artifacts.ProjectDependency}.
 *
 * <h3>File dependencies</h3>
 *
 * <p>You can also add a dependency using a {@link org.gradle.api.file.FileCollection}:</p>
 * <code><i>configurationName</i> files('a file')</code>
 *
 * <pre autoTested=''>
 * apply plugin: 'java'
 * //so that we can use 'compile', 'testCompile' for dependencies
 *
 * dependencies {
 *   //declaring arbitrary files as dependencies
 *   compile files('hibernate.jar', 'libs/spring.jar')
 *
 *   //putting all jars from 'libs' onto compile classpath
 *   compile fileTree('libs')
 * }
 * </pre>
 *
 * <p>File dependencies are represented using a {@link org.gradle.api.artifacts.SelfResolvingDependency}.</p>
 * 
 * <h3>Dependencies to other configurations</h3>
 * 
 * <p>You can add a dependency using a {@link org.gradle.api.artifacts.Configuration}.</p>
 *
 * <p>When the configuration is from the same project as the target configuration, the target configuration is changed
 * to extend from the provided configuration.</p>
 *
 * <p>When the configuration is from a different project, a project dependency is added.</p>
 *
 * <h3>Gradle distribution specific dependencies</h3>
 *
 * <p>It is possible to depend on certain Gradle APIs or libraries that Gradle ships with.
 * It is particularly useful for Gradle plugin development. Example:</p>
 *
 * <pre autoTested=''>
 * //Our Gradle plugin is written in groovy
 * apply plugin: 'groovy'
 * //now we can use the 'compile' configuration for declaring dependencies
 *
 * dependencies {
 *   //we will use the Groovy version that ships with Gradle:
 *   compile localGroovy()
 *
 *   //our plugin requires Gradle API interfaces and classes to compile:
 *   compile gradleApi()
 * }
 * </pre>
 *
 * <h3>Client module dependencies</h3>
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
 */
public interface DependencyHandler {
    /**
     * Adds a dependency to the given configuration.
     *
     * @param configurationName The name of the configuration.
     * @param dependencyNotation
     *
     * The dependency notation, in one of the notations described above.
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
     * Creates a dependency without adding it to a configuration.
     *
     * @param dependencyNotation The dependency notation, in one of the notations described above.
     * @return The dependency.
     */
    Dependency create(Object dependencyNotation);

    /**
     * Creates a dependency without adding it to a configuration, and configures the dependency using
     * the given closure.
     *
     * @param dependencyNotation The dependency notation, in one of the notations described above.
     * @param configureClosure The closure to use to configure the dependency.
     * @return The dependency.
     */
    Dependency create(Object dependencyNotation, Closure configureClosure);

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
    Dependency project(Map<String, ?> notation);
    
    /**
     * Creates a dependency on the API of the current version of Gradle.
     *
     * @return The dependency.
     */
    Dependency gradleApi();
    
    /**
     * Creates a dependency on the Groovy that is distributed with the current version of Gradle.
     * 
     * @return The dependency.
     */
    Dependency localGroovy();

    /**
     * Returns the component metadata handler for this project. The returned handler can be used for adding rules
     * that modify the metadata of depended-on software components.
     *
     * @return the component metadata handler for this project
     * @since 1.8
     */
    @Incubating
    ComponentMetadataHandler getComponents();

    /**
     * Configures component metadata for this project.
     *
     * <p>This method executes the given action against the {@link org.gradle.api.artifacts.dsl.ComponentMetadataHandler} for this project.
     *
     * @param configureAction the action to use to configure module metadata
     * @since 1.8
     */
    @Incubating
    void components(Action<? super ComponentMetadataHandler> configureAction);

    /**
     * Returns the component module metadata handler for this project. The returned handler can be used for adding rules
     * that modify the metadata of depended-on software components.
     *
     * @return the component module metadata handler for this project
     * @since 2.2
     */
    @Incubating
    ComponentModuleMetadataHandler getModules();

    /**
     * Configures module metadata for this project.
     *
     * <p>This method executes the given action against the {@link org.gradle.api.artifacts.dsl.ComponentModuleMetadataHandler} for this project.
     *
     * @param configureAction the action to use to configure module metadata
     * @since 2.2
     */
    @Incubating
    void modules(Action<? super ComponentModuleMetadataHandler> configureAction);

    /**
     * Creates an artifact resolution query.
     *
     * @since 2.0
     */
    @Incubating
    ArtifactResolutionQuery createArtifactResolutionQuery();
}
