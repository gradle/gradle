/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.internal.configuration;

import org.gradle.api.configuration.BuildFeature;
import org.gradle.api.configuration.BuildFeatures;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.api.internal.provider.ProviderInternal;
import org.gradle.api.internal.provider.Providers;
import org.gradle.api.provider.Provider;
import org.gradle.internal.buildoption.Option;
import org.gradle.internal.buildtree.BuildModelParameters;

import javax.inject.Inject;

public class DefaultBuildFeatures implements BuildFeatures {

    private final BuildFeature configurationCache;
    private final BuildFeature isolatedProjects;

    @Inject
    public DefaultBuildFeatures(StartParameterInternal startParameter, BuildModelParameters buildModelParameters) {
        this.configurationCache = createConfigurationCache(startParameter, buildModelParameters);
        this.isolatedProjects = createIsolatedProjects(startParameter, buildModelParameters);
    }

    @Override
    public BuildFeature getConfigurationCache() {
        return configurationCache;
    }

    @Override
    public BuildFeature getIsolatedProjects() {
        return isolatedProjects;
    }

    private static BuildFeature createConfigurationCache(StartParameterInternal startParameter, BuildModelParameters buildModelParameters) {
        Provider<Boolean> isRequested = getRequestedProvider(startParameter.getConfigurationCache());
        Provider<Boolean> isActive = Providers.of(buildModelParameters.isConfigurationCache());
        return new DefaultBuildFeature(isRequested, isActive);
    }

    private static BuildFeature createIsolatedProjects(StartParameterInternal startParameter, BuildModelParameters buildModelParameters) {
        Provider<Boolean> isRequested = getRequestedProvider(startParameter.getIsolatedProjects());
        Provider<Boolean> isActive = Providers.of(buildModelParameters.isIsolatedProjects());
        return new DefaultBuildFeature(isRequested, isActive);
    }

    private static ProviderInternal<Boolean> getRequestedProvider(Option.Value<Boolean> configurationCacheOption) {
        return Providers.ofNullable(configurationCacheOption.isExplicit() ? configurationCacheOption.get() : null);
    }
}
