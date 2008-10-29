/*
 * Copyright 2007 the original author or authors.
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

import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;

import java.util.Map;
import java.util.List;

/**
* @author Hans Dockter
*/
public interface Dependency {
    public static final String DEFAULT_CONFIGURATION = "default";
    public static final String MASTER_CONFIGURATION = "master";

    DependencyDescriptor createDependencyDescriptor(ModuleDescriptor parent);

    /**
     * Adds an exclude rule to exclude transitive dependencies of this dependency.
     *
     * @param excludeProperties the properties to define the exclude rule.
     * @return this
     * @see org.gradle.api.dependencies.ExcludeRuleContainer#add(java.util.Map)
     * @see #exclude(java.util.Map, java.util.List)
     */
    Dependency exclude(Map<String, String> excludeProperties);

    /**
     * Adds an exclude rule to exclude transitive dependencies of this dependency.
     *
     * @param excludeProperties the properties to define the exclude rule.
     * @param confs The confs against which the exclude rule should be applied.
     * @return this
     * @see org.gradle.api.dependencies.ExcludeRuleContainer#add(java.util.Map, java.util.List)
     * @see #exclude(java.util.Map)
     */
    Dependency exclude(Map<String, String> excludeProperties, List<String> confs);

    /**
     * Returns the container with all the added exclude rules.
     */
    ExcludeRuleContainer getExcludeRules();

    DependencyConfigurationMappingContainer getDependencyConfigurationMappings();

    void dependencyConfigurations(String... dependencyConfigurations);

    void dependencyConfigurations(Map<String, List<String>> dependencyConfigurations);

    void setExcludeRules(ExcludeRuleContainer excludeRules);

    void setDependencyConfigurationMappings(DependencyConfigurationMappingContainer dependencyConfigurationMappings);
}
