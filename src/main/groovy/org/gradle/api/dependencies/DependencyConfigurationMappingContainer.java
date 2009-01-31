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
package org.gradle.api.dependencies;

import org.gradle.api.internal.dependencies.DefaultConfiguration;
import org.gradle.api.internal.dependencies.DefaultConfigurationContainer;

import java.util.Map;
import java.util.List;
import java.util.Set;

/**
 * @author Hans Dockter
 */
public interface DependencyConfigurationMappingContainer {
    Configuration WILDCARD = new DefaultConfiguration("*", new DefaultConfigurationContainer());

    void add(String... dependencyConfigurations);
    void add(Map<Configuration, List<String>> dependencyConfigurations);
    Map<Configuration, List<String>> getMappings();

    void addMasters(Configuration... masterConfigurations);

    Set<Configuration> getMasterConfigurations();

    List<String> getDependencyConfigurations(String configuration);

    void setDependencyConfigurations(String... dependencyConfigurations);
}
