/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.publish.internal.versionmapping;

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolutionResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;

import java.util.Set;

public class DefaultVariantVersionMappingStrategy implements VariantVersionMappingStrategyInternal {
    private final ConfigurationContainer configurations;
    private boolean usePublishedVersions;
    private Configuration targetConfiguration;

    public DefaultVariantVersionMappingStrategy(ConfigurationContainer configurations) {
        this.configurations = configurations;
    }

    @Override
    public void fromResolutionResult() {
        usePublishedVersions = true;
    }

    @Override
    public void fromResolutionOf(Configuration configuration) {
        usePublishedVersions = true;
        targetConfiguration = configuration;
    }

    @Override
    public void fromResolutionOf(String configurationName) {
        fromResolutionOf(configurations.getByName(configurationName));
    }

    @Override
    public ModuleVersionIdentifier maybeResolveVersion(String group, String module) {
        if (usePublishedVersions && targetConfiguration != null) {
            ResolutionResult resolutionResult = targetConfiguration
                .getIncoming()
                .getResolutionResult();
            Set<? extends ResolvedComponentResult> resolvedComponentResults = resolutionResult.getAllComponents();
            for (ResolvedComponentResult selected : resolvedComponentResults) {
                ModuleVersionIdentifier moduleVersion = selected.getModuleVersion();
                if (moduleVersion != null && group.equals(moduleVersion.getGroup()) && module.equals(moduleVersion.getName())) {
                    return moduleVersion;
                }
            }
            // If we reach this point it means we have a dependency which doesn't belong to the resolution result
            // Which can mean two things:
            // 1. the graph used to get the resolved version has nothing to do with the dependencies we're trying to get versions for (likely user error)
            // 2. the graph contains first-level dependencies which have been substituted (likely) so we're going to iterate on dependencies instead
            Set<? extends DependencyResult> allDependencies = resolutionResult.getAllDependencies();
            for (DependencyResult dependencyResult : allDependencies) {
                if (dependencyResult instanceof ResolvedDependencyResult) {
                    if (dependencyResult.getRequested() instanceof ModuleComponentSelector) {
                        ModuleComponentSelector requested = (ModuleComponentSelector) dependencyResult.getRequested();
                        if (requested.getGroup().equals(group) && requested.getModule().equals(module)) {
                            return ((ResolvedDependencyResult) dependencyResult).getSelected().getModuleVersion();
                        }
                    }
                }
            }
        }
        return null;
    }

    public void setTargetConfiguration(Configuration target) {
        targetConfiguration = target;
    }
}
