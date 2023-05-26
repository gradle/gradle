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
package org.gradle.api.artifacts;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.internal.HasInternalProtocol;

/**
 * <p>A {@code ConfigurationContainer} is responsible for declaring and managing configurations. See also {@link Configuration}.</p>
 *
 * <p>You can obtain a {@code ConfigurationContainer} instance by calling {@link org.gradle.api.Project#getConfigurations()},
 * or using the {@code configurations} property in your build script.</p>
 *
 * <p>The configurations in a container are accessible as read-only properties of the container, using the name of the
 * configuration as the property name. For example:</p>
 *
 * <pre class='autoTested'>
 * configurations.create('myConfiguration')
 * configurations.myConfiguration.transitive = false
 * </pre>
 *
 * <p>A dynamic method is added for each configuration which takes a configuration closure. This is equivalent to
 * calling {@link #getByName(String, groovy.lang.Closure)}. For example:</p>
 *
 * <pre class='autoTested'>
 * configurations.create('myConfiguration')
 * configurations.myConfiguration {
 *     transitive = false
 * }
 * </pre>
 *
 * <h2>Examples</h2>
 *
 * An example showing how to refer to a given configuration by name
 * in order to get hold of all dependencies (e.g. jars, but only)
 * <pre class='autoTested'>
 *   plugins {
 *       id 'java' //so that I can use 'implementation', 'compileClasspath' configuration
 *   }
 *
 *   dependencies {
 *       implementation 'org.slf4j:slf4j-api:1.7.26'
 *   }
 *
 *   //copying all dependencies attached to 'compileClasspath' into a specific folder
 *   task copyAllDependencies(type: Copy) {
 *     //referring to the 'compileClasspath' configuration
 *     from configurations.compileClasspath
 *     into 'allLibs'
 *   }
 * </pre>
 *
 * An example showing how to declare and configure configurations
 * <pre class='autoTested'>
 * plugins {
 *     id 'java' // so that I can use 'implementation', 'testImplementation' configurations
 * }
 *
 * configurations {
 *   //adding a configuration:
 *   myConfiguration
 *
 *   //adding a configuration that extends existing configuration:
 *   //(testImplementation was added by the java plugin)
 *   myIntegrationTestsCompile.extendsFrom(testImplementation)
 *
 *   //configuring existing configurations not to put transitive dependencies on the compile classpath
 *   //this way you can avoid issues with implicit dependencies to transitive libraries
 *   compileClasspath.transitive = false
 *   testCompileClasspath.transitive = false
 * }
 * </pre>
 *
 * Examples on configuring the <b>resolution strategy</b> - see docs for {@link ResolutionStrategy}
 *
 * Please see the <a href="https://docs.gradle.org/current/userguide/declaring_dependencies.html#sec:what-are-dependency-configurations" target="_top">Managing Dependency Configurations</a> User Manual chapter for more information.
 */
@HasInternalProtocol
public interface ConfigurationContainer extends NamedDomainObjectContainer<Configuration> {
    /**
     * {@inheritDoc}
     */
    @Override
    Configuration getByName(String name) throws UnknownConfigurationException;

    /**
     * {@inheritDoc}
     */
    @Override
    Configuration getAt(String name) throws UnknownConfigurationException;

    /**
     * {@inheritDoc}
     */
    @Override
    Configuration getByName(String name, @DelegatesTo(Configuration.class) Closure configureClosure) throws UnknownConfigurationException;

    /**
     * {@inheritDoc}
     */
    @Override
    Configuration getByName(String name, Action<? super Configuration> configureAction) throws UnknownConfigurationException;

    /**
     * Creates a configuration, but does not add it to this container.
     *
     * @param dependencies The dependencies of the configuration.
     * @return The configuration.
     */
    Configuration detachedConfiguration(Dependency... dependencies);

    /**
     * Registers a {@link ResolvableConfiguration} with an immutable role. Resolvable configurations
     * are meant to resolve dependency graphs and their artifacts.
     *
     * @param name The name of the configuration to register.
     *
     * @return A provider which creates a new resolvable configuration.
     *
     * @throws InvalidUserDataException If a configuration with the given {@code name} already exists in this container.
     *
     * @since 8.3
     */
    @Incubating
    NamedDomainObjectProvider<ResolvableConfiguration> resolvable(String name);

    /**
     * Registers a {@link ResolvableConfiguration} via {@link #resolvable(String)} and then executes
     * the provided action against it.
     *
     * @param name The name of the configuration to register.
     * @param action The action to execute against the new configuration.
     *
     * @return A provider which creates a new resolvable configuration.
     *
     * @throws InvalidUserDataException If a configuration with the given {@code name} already exists in this container.
     *
     * @since 8.3
     */
    @Incubating
    NamedDomainObjectProvider<ResolvableConfiguration> resolvable(String name, Action<? super ResolvableConfiguration> action);

    /**
     * Registers a new {@link ConsumableConfiguration} with an immutable role. Consumable configurations
     * are meant to act as a variant in the context of Dependency Management and Publishing.
     *
     * @param name The name of the configuration to register.
     *
     * @return A provider which creates a new consumable configuration.
     *
     * @throws InvalidUserDataException If a configuration with the given {@code name} already exists in this container.
     *
     * @since 8.3
     */
    @Incubating
    NamedDomainObjectProvider<ConsumableConfiguration> consumable(String name);

    /**
     * Registers a {@link ConsumableConfiguration} via {@link #consumable(String)} and then executes
     * the provided action against it.
     *
     * @param name The name of the configuration to register.
     * @param action The action to execute against the new configuration.
     *
     * @return A provider which creates a new consumable configuration.
     *
     * @throws InvalidUserDataException If a configuration with the given {@code name} already exists in this container.
     *
     * @since 8.3
     */
    @Incubating
    NamedDomainObjectProvider<ConsumableConfiguration> consumable(String name, Action<? super ConsumableConfiguration> action);

    /**
     * Registers a new {@link DependenciesConfiguration} with an immutable role. Dependency configurations
     * collect dependencies, dependency constraints, and exclude rules to be used by both resolvable
     * and consumable configurations.
     *
     * @param name The name of the configuration to register.
     *
     * @return A provider which creates a new dependencies configuration.
     *
     * @throws InvalidUserDataException If a configuration with the given {@code name} already exists in this container.
     *
     * @since 8.3
     */
    @Incubating
    NamedDomainObjectProvider<DependenciesConfiguration> dependencies(String name);

    /**
     * Registers a {@link DependenciesConfiguration} via {@link #dependencies(String)} and then executes
     * the provided action against it.
     *
     * @param name The name of the configuration to register.
     * @param action The action to execute against the new configuration.
     *
     * @return A provider which creates a new dependencies configuration.
     *
     * @throws InvalidUserDataException If a configuration with the given {@code name} already exists in this container.
     *
     * @since 8.3
     */
    @Incubating
    NamedDomainObjectProvider<DependenciesConfiguration> dependencies(String name, Action<? super DependenciesConfiguration> action);

}
