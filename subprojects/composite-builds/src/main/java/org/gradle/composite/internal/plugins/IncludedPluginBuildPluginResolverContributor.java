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
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.build.IncludedBuildState;
import org.gradle.plugin.management.internal.InvalidPluginRequestException;
import org.gradle.plugin.management.internal.PluginRequestInternal;
import org.gradle.plugin.use.PluginId;
import org.gradle.plugin.use.resolve.internal.FallbackPluginResolverContributor;
import org.gradle.plugin.use.resolve.internal.PluginResolution;
import org.gradle.plugin.use.resolve.internal.PluginResolutionResult;
import org.gradle.plugin.use.resolve.internal.PluginResolver;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class IncludedPluginBuildPluginResolverContributor implements FallbackPluginResolverContributor {

    private static final String SOURCE_DESCRIPTION = "Included Plugin Builds";

    private final PluginResolver resolver;

    public IncludedPluginBuildPluginResolverContributor(BuildStateRegistry buildRegistry, BuildIncluder buildIncluder) {
        this.resolver = new IncludedPluginBuildPluginResolver(buildRegistry, buildIncluder);
    }

    @Override
    public void collectResolversInto(Collection<PluginResolver> resolvers) {
        resolvers.add(resolver);
    }

    private static class IncludedPluginBuildPluginResolver implements PluginResolver {

        private final BuildStateRegistry buildRegistry;
        private final BuildIncluder buildIncluder;

        private final Map<PluginId, PluginResolution> results = new HashMap<>();

        private IncludedPluginBuildPluginResolver(BuildStateRegistry buildRegistry, BuildIncluder buildIncluder) {
            this.buildRegistry = buildRegistry;
            this.buildIncluder = buildIncluder;
        }

        @Override
        public void resolve(PluginRequestInternal pluginRequest, PluginResolutionResult result) throws InvalidPluginRequestException {
            PluginResolution resolution = results.computeIfAbsent(pluginRequest.getId(), this::resolvePluginFromIncludedBuilds);
            if (resolution != null) {
                result.found(SOURCE_DESCRIPTION, resolution);
            }
        }

        private PluginResolution resolvePluginFromIncludedBuilds(PluginId requestedPluginId) {
            for (IncludedBuildState build : buildIncluder.includeRegisteredPluginBuilds()) {
                buildRegistry.ensureConfigured(build);

                Optional<PluginResolution> pluginResolution = build.withState(gradleInternal -> LocalPluginResolution.resolvePlugin(gradleInternal, requestedPluginId));
                if (pluginResolution.isPresent()) {
                    return pluginResolution.get();
                }
            }
            return null;
        }
    }
}
