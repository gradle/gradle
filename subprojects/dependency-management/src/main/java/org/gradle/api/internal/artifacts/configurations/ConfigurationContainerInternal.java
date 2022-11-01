/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts.configurations;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.UnknownConfigurationException;

public interface ConfigurationContainerInternal extends ConfigurationContainer {
    @Override
    ConfigurationInternal getByName(String name) throws UnknownConfigurationException;
    @Override
    ConfigurationInternal detachedConfiguration(Dependency... dependencies);

    /**
     * Creates a new configuration in the same manner as {@link #create(String)}, and then
     * immediately assigns it a role by setting internal status flags to mark possible usage options
     * for the configuration.
     */
    default ConfigurationInternal createWithRole(String name, ConfigurationRole role) {
        ConfigurationInternal configuration = (ConfigurationInternal) create(name);
        return DefaultConfigurationFactory.assignRole(configuration, role);
    }

    /**
     * Creates a new configuration in the same manner as {@link #maybeCreate(String)}, and then
     * immediately assigns it a role by setting internal status flags to mark possible usage options
     * for the configuration.
     *
     * If the configuration already exists, this method will <strong>NOT</strong>> change anything about it,
     * including its role.
     */
    default ConfigurationInternal maybeCreateWithRole(String name, ConfigurationRole role) {
        ConfigurationInternal configuration = (ConfigurationInternal) findByName(name);
        if (configuration == null) {
            return createWithRole(name, role);
        } else {
            return DefaultConfigurationFactory.assertInRole(configuration, role);
        }
    }

    /**
     * Creates a new configuration in the same manner as {@link #create(String, Closure)}, and then
     * immediately assigns it a role by setting internal status flags to mark possible usage options
     * for the configuration.
     */
    default ConfigurationInternal createWithRole(String name, ConfigurationRole role, Closure<? super Configuration> configureClosure) throws InvalidUserDataException {
        ConfigurationInternal configuration = (ConfigurationInternal) create(name, configureClosure);
        return DefaultConfigurationFactory.assignRole(configuration, role);
    }

    /**
     * Creates a new configuration in the same manner as {@link #create(String, Action)}, and then
     * immediately assigns it a role by setting internal status flags to mark possible usage options
     * for the configuration.
     */
    default ConfigurationInternal createWithRole(String name, ConfigurationRole role, Action<? super Configuration> configureAction) throws InvalidUserDataException {
        ConfigurationInternal configuration = (ConfigurationInternal) create(name, configureAction);
        return DefaultConfigurationFactory.assignRole(configuration, role);
    }
}
