/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.composite.internal.plugins;

import org.gradle.internal.build.BuildIncluder;
import org.gradle.internal.build.IncludedBuildState;
import org.gradle.plugin.management.internal.InvalidPluginRequestException;
import org.gradle.plugin.management.internal.PluginRequestInternal;
import org.gradle.plugin.use.PluginId;
import org.gradle.plugin.use.resolve.internal.PluginResolution;
import org.gradle.plugin.use.resolve.internal.PluginResolutionResult;
import org.gradle.plugin.use.resolve.internal.PluginResolver;
import org.gradle.plugin.use.resolve.internal.PluginResolverContributor;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class EarlyCompositeBuildPluginResolverContributor implements PluginResolverContributor {

    private static final String SOURCE_DESCRIPTION = "Early configured Included Builds";

    private final BuildIncluder buildIncluder;
    private final Map<PluginId, PluginResolution> results = new HashMap<>();

    public EarlyCompositeBuildPluginResolverContributor(BuildIncluder buildIncluder) {
        this.buildIncluder = buildIncluder;
    }

    @Override
    public void collectResolversInto(Collection<PluginResolver> resolvers) {
        resolvers.add(new CompositeBuildPluginResolver());
    }

    @Override
    public boolean isFallback() {
        return true;
    }

    private class CompositeBuildPluginResolver implements PluginResolver {
        @Override
        public void resolve(PluginRequestInternal pluginRequest, PluginResolutionResult result) throws InvalidPluginRequestException {
            PluginResolution resolution = results.computeIfAbsent(pluginRequest.getId(), this::resolvePluginFromIncludedBuilds);
            if (resolution != null) {
                result.found(SOURCE_DESCRIPTION, resolution);
            }
        }

        private PluginResolution resolvePluginFromIncludedBuilds(PluginId requestedPluginId) {
            for (IncludedBuildState build : buildIncluder.includeRegisteredBuildLogicBuilds()) {
                // ensure the build is configured - this finds and registers any plugin publications the build may have
                build.getConfiguredBuild();
                Optional<PluginResolution> pluginResolution = build.withState(gradleInternal -> LocalPluginResolution.resolvePlugin(gradleInternal, requestedPluginId));
                if (pluginResolution.isPresent()) {
                    return pluginResolution.get();
                }
            }
            return null;
        }
    }
}
