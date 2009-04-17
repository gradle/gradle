/*
 * Copyright 2007-2008 the original author or authors.
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
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.UnknownConfigurationException;
import org.gradle.api.specs.Spec;

import java.util.Set;
import java.util.Map;

/**
 * <p>A {@code ConfigurationContainer} is responsible for managing a set of {@link Configuration} instances.</p>
 *
 * @author Hans Dockter
 */
public interface ConfigurationContainer extends Iterable<Configuration> {
    /**
     * Returns the configurations in this container.
     *
     * @return The configurations. Returns an empty set if this container is empty.
     */
    Set<Configuration> getAll();

    /**
     * Returns the configurations in this container, as a map from configuration name to {@code Configuration}
     * instance.
     *
     * @return The configurations. Returns an empty map if this container is empty.
     */
    Map<String, Configuration> getAsMap();

    /**
     * Returns the configurations in this container which meet the given criteria.
     *
     * @param spec The criteria to use.
     * @return The matching configurations. Returns an empty set if there are no such configurations in this container.
     */
    Set<Configuration> get(Spec<? super Configuration> spec);

    /**
     * Locates a configuration by name, returning null if there is no such configuration.
     *
     * @param name The configuration name
     * @return The configuration with the given name, or null if there is no such configuration in this container.
     */
    Configuration find(String name);

    /**
     * Locates a configuration by name, failing if there is no such configuration. You can call this method in your
     * build script by using the {@code .} operator:
     *
     * <pre>
     * println configurations.someConfig.asPath
     * </pre>
     *
     * @param name The configuration name
     * @return The configuration with the given name. Never returns null.
     * @throws UnknownConfigurationException when there is no such configuration in this container.
     */
    Configuration get(String name) throws UnknownConfigurationException;

    /**
     * Locates a configuration by name, failing if there is no such configuration. This method is identical to {@link
     * #get(String)}. You can call this method in your build script by using the groovy {@code []} operator:
     *
     * <pre>
     * println configurations['some-config'].asPath
     * </pre>
     *
     * @param name The configuration name
     * @return The configuration with the given name. Never returns null.
     * @throws UnknownConfigurationException when there is no such configuration in this container.
     */
    Configuration getAt(String name) throws UnknownConfigurationException;

    /**
     * Locates a configuration by name, failing if there is no such configuration. The given configuration closure is
     * executed against the configuration before it is returned from this method.
     *
     * @param name The configuration name
     * @param configureClosure The closure to use to configure the configuration.
     * @return The configuration with the given name. Never returns null.
     * @throws UnknownConfigurationException when there is no such configuration in this container.
     */
    Configuration get(String name, Closure configureClosure) throws UnknownConfigurationException;

    /**
     * Adds a configuration with the given name.
     *
     * @param name The name of the new configuration.
     * @return The newly added configuration.
     * @throws InvalidUserDataException when a configuration with the given name already exists in this container.
     */
    Configuration add(String name) throws InvalidUserDataException;

    /**
     * Adds a configuration with the given name. The given configuration closure is executed against the configuration
     * before it is returned from this method.
     *
     * @param name The name of the new configuration.
     * @param configureClosure The closure to use to configure the configuration.
     * @return The newly added configuration.
     * @throws InvalidUserDataException when a configuration with the given name already exists in this container.
     */
    Configuration add(String name, Closure configureClosure) throws InvalidUserDataException;

    /**
     * Creates a configuration, but does not add it to this container.
     *
     * @param dependencies The dependencies of the configuration.
     * @return The configuration.
     */
    Configuration detachedConfiguration(Dependency... dependencies);
}
