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
import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
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
 *   apply plugin: 'java' //so that I can use 'compile' configuration
 *
 *   //copying all dependencies attached to 'compile' into a specific folder
 *   task copyAllDependencies(type: Copy) {
 *     //referring to the 'compile' configuration
 *     from configurations.compile
 *     into 'allLibs'
 *   }
 * </pre>
 *
 * An example showing how to declare and configure configurations
 * <pre class='autoTested'>
 * apply plugin: 'java' //so that I can use 'compile', 'testCompile' configurations
 *
 * configurations {
 *   //adding a configuration:
 *   myConfiguration
 *
 *   //adding a configuration that extends existing configuration:
 *   //(testCompile was added by the java plugin)
 *   myIntegrationTestsCompile.extendsFrom(testCompile)
 *
 *   //configuring existing configurations not to put transitive dependencies on the compile classpath
 *   //this way you can avoid issues with implicit dependencies to transitive libraries
 *   compile.transitive = false
 *   testCompile.transitive = false
 * }
 * </pre>
 *
 * Examples on configuring the <b>resolution strategy</b> - see docs for {@link ResolutionStrategy}
 */
@HasInternalProtocol
public interface ConfigurationContainer extends NamedDomainObjectContainer<Configuration> {
    /**
     * {@inheritDoc}
     */
    Configuration getByName(String name) throws UnknownConfigurationException;

    /**
     * {@inheritDoc}
     */
    Configuration getAt(String name) throws UnknownConfigurationException;

    /**
     * {@inheritDoc}
     */
    Configuration getByName(String name, Closure configureClosure) throws UnknownConfigurationException;

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
}
