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
package org.gradle.api.internal.dependencies;

import groovy.lang.Closure;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.dependencies.Configuration;
import org.gradle.api.dependencies.UnknownConfigurationException;
import org.gradle.api.filter.FilterSpec;
import org.gradle.api.filter.Filters;
import org.gradle.util.ConfigureUtil;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Hans Dockter
 */
public class DefaultConfigurationContainer implements ConfigurationContainer {
    private Map<String, Configuration> configurations = new HashMap<String, Configuration>();

    public DefaultConfigurationContainer() {
    }

    public DefaultConfigurationContainer(Set<? extends Configuration> configurations) {
        for (Configuration configuration : configurations) {
            this.configurations.put(configuration.getName(), configuration);
        }
    }

    public Configuration add(String name, Closure configureClosure) {
        if (configurations.containsKey(name)) {
            throw new InvalidUserDataException(String.format("Cannot add configuration '%s' as a configuration with that name already exists.",
                    name));
        }
        DefaultConfiguration configuration = new DefaultConfiguration(name, this);
        configurations.put(name, configuration);
        ConfigureUtil.configure(configureClosure, configuration);
        return configuration;
    }

    public Configuration add(String name) {
        return add(name, null);
    }

    public Configuration find(String name) {
        return configurations.get(name);
    }

    public Configuration get(String name, Closure configureClosure) throws UnknownConfigurationException {
        Configuration configuration = find(name);
        if (configuration == null) {
            throw new UnknownConfigurationException(String.format("Configuration with name '%s' not found.", name));
        }
        ConfigureUtil.configure(configureClosure, configuration);
        return configuration;
    }

    public Configuration get(String name) throws UnknownConfigurationException {
        return get(name, null);
    }

    public Set<Configuration> get() {
        return new HashSet<Configuration>(configurations.values());
    }

    public Set<Configuration> get(FilterSpec<Configuration> filter) {
        return new HashSet<Configuration>(Filters.filterIterable(configurations.values(), filter));
    }

    public void setConfigurations(Map<String, Configuration> configurations) {
        this.configurations = configurations;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DefaultConfigurationContainer that = (DefaultConfigurationContainer) o;

        if (configurations != null ? !configurations.equals(that.configurations) : that.configurations != null)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        return configurations != null ? configurations.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "DefaultConfigurationContainer{" +
                "configurations=" + configurations +
                '}';
    }
}
