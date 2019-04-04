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
package org.gradle.api.publish.ivy.internal.versionmapping;

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.publish.internal.versionmapping.VariantVersionMappingStrategyInternal;

import java.util.Set;

public class DefaultVariantVersionMappingStrategy implements VariantVersionMappingStrategyInternal {
    private final ConfigurationContainer configurations;
    private boolean usePublishedVersions;
    private Configuration targetConfiguration;

    DefaultVariantVersionMappingStrategy(ConfigurationContainer configurations) {
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
    public String maybeResolveVersion(String group, String module) {
        if (usePublishedVersions && targetConfiguration != null) {
            Set<? extends ResolvedComponentResult> resolvedComponentResults = targetConfiguration.getIncoming().getResolutionResult().getAllComponents();
            for (ResolvedComponentResult selected : resolvedComponentResults) {
                ModuleVersionIdentifier moduleVersion = selected.getModuleVersion();
                if (moduleVersion != null && group.equals(moduleVersion.getGroup()) && module.equals(moduleVersion.getName())) {
                    return moduleVersion.getVersion();
                }
            }
        }
        return null;
    }

    public void setTargetConfiguration(Configuration target) {
        targetConfiguration = target;
    }
}
