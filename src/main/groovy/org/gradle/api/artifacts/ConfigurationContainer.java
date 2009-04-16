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

/**
 * @author Hans Dockter
 */
public interface ConfigurationContainer {
    Set<Configuration> getAll();

    Set<Configuration> get(Spec<Configuration> spec);

    Configuration add(String configuration, Closure configureClosure) throws InvalidUserDataException;

    Configuration find(String name);

    Configuration get(String name, Closure configureClosure) throws UnknownConfigurationException;

    Configuration add(String name);

    Configuration get(String name) throws UnknownConfigurationException;

    Configuration detachedConfiguration(Dependency... dependencies);
}
