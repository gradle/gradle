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

import javax.annotation.Nullable;

public class DefaultVariantVersionMappingStrategy implements VariantVersionMappingStrategyInternal {
    private final ConfigurationContainer configurations;
    private boolean enabled;
    private Configuration userConfiguration;
    private Configuration defaultConfiguration;

    public DefaultVariantVersionMappingStrategy(ConfigurationContainer configurations) {
        this.configurations = configurations;
    }

    @Override
    public void fromResolutionResult() {
        enabled = true;
    }

    @Override
    public void fromResolutionOf(Configuration configuration) {
        enabled = true;
        userConfiguration = configuration;
    }

    @Override
    public void fromResolutionOf(String configurationName) {
        fromResolutionOf(configurations.getByName(configurationName));
    }

    public void setDefaultResolutionConfiguration(@Nullable Configuration defaultConfiguration) {
        this.defaultConfiguration = defaultConfiguration;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Nullable
    @Override
    public Configuration getUserResolutionConfiguration() {
        return userConfiguration;
    }

    @Nullable
    @Override
    public Configuration getDefaultResolutionConfiguration() {
        return defaultConfiguration;
    }
}
