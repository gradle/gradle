/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.tasks.diagnostics.internal;

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.UnresolvableConfigurationResult;
import org.gradle.internal.deprecation.DeprecatableConfiguration;

import javax.annotation.Nullable;

public class ConfigurationDetails {

    public static ConfigurationDetails of(Configuration configuration) {
        boolean canBeResolved = canBeResolved(configuration);
        return new ConfigurationDetails(
            configuration.getName(),
            configuration.getDescription(),
            canBeResolved,
            canBeResolved ? configuration.getIncoming().getResolutionResult().getRootComponent() : null,
            canBeResolved ? null : UnresolvableConfigurationResult.of(configuration)
        );
    }

    private static boolean canBeResolved(Configuration configuration) {
        boolean isDeprecatedForResolving = ((DeprecatableConfiguration) configuration).isDeprecatedForResolution();
        return configuration.isCanBeResolved() && !isDeprecatedForResolving;
    }

    private final String name;

    @Nullable
    private final String description;

    private final boolean canBeResolved;

    @Nullable
    private final Provider<ResolvedComponentResult> resolutionResultRoot;

    @Nullable
    private final UnresolvableConfigurationResult unresolvableResult;

    private ConfigurationDetails(
        String name,
        @Nullable String description,
        boolean canBeResolved,
        @Nullable Provider<ResolvedComponentResult> resolutionResultRoot,
        @Nullable UnresolvableConfigurationResult unresolvableResult
    ) {
        this.name = name;
        this.description = description;
        this.canBeResolved = canBeResolved;
        this.resolutionResultRoot = resolutionResultRoot;
        this.unresolvableResult = unresolvableResult;
    }

    public String getName() {
        return name;
    }

    @Nullable
    public String getDescription() {
        return description;
    }

    public boolean isCanBeResolved() {
        return canBeResolved;
    }

    @Nullable
    public Provider<ResolvedComponentResult> getResolutionResultRoot() {
        return resolutionResultRoot;
    }

    @Nullable
    public UnresolvableConfigurationResult getUnresolvableResult() {
        return unresolvableResult;
    }
}
