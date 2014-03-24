/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine;

import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.gradle.api.internal.artifacts.metadata.ComponentMetaData;
import org.gradle.api.internal.artifacts.metadata.ConfigurationMetaData;
import org.gradle.api.internal.artifacts.metadata.DependencyMetaData;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class DefaultDependencyToConfigurationResolver implements DependencyToConfigurationResolver {
    // TODO - don't pass in 'from' configuration - the dependency should have whatever context it needs
    public Set<ConfigurationMetaData> resolveTargetConfigurations(DependencyMetaData dependencyMetaData, ConfigurationMetaData fromConfiguration, ComponentMetaData targetComponent) {
        // TODO - resolve directly to config meta data
        ModuleDescriptor targetDescriptor = targetComponent.getDescriptor();
        DependencyDescriptor dependencyDescriptor = dependencyMetaData.getDescriptor();
        Set<String> targetConfigurationNames = new LinkedHashSet<String>();
        for (String config : dependencyDescriptor.getModuleConfigurations()) {
            if (config.equals("*") || config.equals("%")) {
                collectTargetConfiguration(dependencyDescriptor, fromConfiguration, fromConfiguration.getName(), targetDescriptor, targetConfigurationNames);
            } else if (fromConfiguration.getHierarchy().contains(config)) {
                collectTargetConfiguration(dependencyDescriptor, fromConfiguration, config, targetDescriptor, targetConfigurationNames);
            }
        }

        Set<ConfigurationMetaData> targets = new LinkedHashSet<ConfigurationMetaData>();
        for (String targetConfigurationName : targetConfigurationNames) {
            // TODO - move this down below
            if (targetDescriptor.getConfiguration(targetConfigurationName) == null) {
                throw new RuntimeException(String.format("Module version %s, configuration '%s' declares a dependency on configuration '%s' which is not declared in the module descriptor for %s",
                        fromConfiguration.getComponent().getId(), fromConfiguration.getName(),
                        targetConfigurationName, targetComponent.getId()));
            }
            ConfigurationMetaData targetConfiguration = targetComponent.getConfiguration(targetConfigurationName);
            targets.add(targetConfiguration);
        }
        return targets;
    }

    private void collectTargetConfiguration(DependencyDescriptor dependencyDescriptor, ConfigurationMetaData fromConfiguration, String mappingRhs, ModuleDescriptor targetModule, Collection<String> targetConfigs) {
        String[] dependencyConfigurations = dependencyDescriptor.getDependencyConfigurations(mappingRhs, fromConfiguration.getName());
        for (String target : dependencyConfigurations) {
            String candidate = target;
            int startFallback = candidate.indexOf('(');
            if (startFallback >= 0) {
                if (candidate.charAt(candidate.length() - 1) == ')') {
                    String preferred = candidate.substring(0, startFallback);
                    if (targetModule.getConfiguration(preferred) != null) {
                        targetConfigs.add(preferred);
                        continue;
                    }
                    candidate = candidate.substring(startFallback + 1, candidate.length() - 1);
                }
            }
            if (candidate.equals("*")) {
                Collections.addAll(targetConfigs, targetModule.getPublicConfigurationsNames());
                continue;
            }
            targetConfigs.add(candidate);
        }
    }
}
