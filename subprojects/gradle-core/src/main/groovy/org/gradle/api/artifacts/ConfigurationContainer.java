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
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.NamedDomainObjectCollection;

/**
 * <p>A {@code ConfigurationContainer} is responsible for managing a set of {@link Configuration} instances.</p>
 *
 * <p>You can obtain a {@code ConfigurationContainer} instance by calling {@link org.gradle.api.Project#getConfigurations()},
 * or using the {@code configurations} property in your build script.</p>
 *
 * <p>The configurations in a container are accessable as read-only properties of the container, using the name of the
 * configuration as the property name. For example:</p>
 *
 * <pre>
 * configurations.add('myConfiguration')
 * configurations.myConfiguration.transitive = false
 * </pre>
 *
 * <p>A dynamic method is added for each configuration which takes a configuration closure. This is equivalent to
 * calling {@link #getByName(String, groovy.lang.Closure)}. For example:</p>
 *
 * <pre>
 * configurations.add('myConfiguration')
 * configurations.myConfiguration {
 *     transitive = false
 * }
 * </pre>
 *
 * @author Hans Dockter
 */
public interface ConfigurationContainer extends NamedDomainObjectContainer<Configuration>, NamedDomainObjectCollection<Configuration> {
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
