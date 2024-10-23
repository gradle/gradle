/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.internal.artifacts.DefaultProjectDependencyFactory;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectPublicationRegistry;
import org.gradle.api.internal.plugins.PluginManagerInternal;
import org.gradle.api.internal.project.HoldsProjectState;
import org.gradle.internal.build.BuildIncluder;
import org.gradle.internal.build.IncludedBuildState;
import org.gradle.plugin.management.internal.PluginRequestInternal;
import org.gradle.plugin.use.PluginId;
import org.gradle.plugin.use.resolve.internal.PluginResolution;
import org.gradle.plugin.use.resolve.internal.PluginResolutionResult;
import org.gradle.plugin.use.resolve.internal.PluginResolutionVisitor;
import org.gradle.plugin.use.resolve.internal.PluginResolver;
import org.gradle.plugin.use.resolve.internal.PluginResolverContributor;
import org.gradle.plugin.use.resolve.internal.local.PluginPublication;
import org.gradle.util.Path;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CompositeBuildPluginResolverContributor implements PluginResolverContributor, HoldsProjectState {

    private static final String SOURCE_DESCRIPTION = "Included Builds";

    private final CompositeBuildPluginResolver resolver;

    @Inject
    public CompositeBuildPluginResolverContributor(
        BuildIncluder buildIncluder,
        ProjectPublicationRegistry publicationRegistry,
        DefaultProjectDependencyFactory projectDependencyFactory
    ) {
        this.resolver = new CompositeBuildPluginResolver(
            buildIncluder,
            publicationRegistry,
            projectDependencyFactory
        );
    }

    @Override
    public void discardAll() {
        resolver.discardAll();
    }

    @Override
    public void collectResolversInto(Collection<PluginResolver> resolvers) {
        resolvers.add(resolver);
    }

    private abstract static class PluginResult {
        static final PluginResult NOT_FOUND_IN_ANY_BUILD = new PluginResult() {};
        static final PluginResult NO_INCLUDED_BUILDS = new PluginResult() {};
    }

    private static class ResolvedPlugin extends PluginResult {
        final PluginResolution resolution;

        public ResolvedPlugin(PluginResolution resolution) {
            this.resolution = resolution;
        }
    }

    private static class CompositeBuildPluginResolver implements PluginResolver {

        private final BuildIncluder buildIncluder;
        private final ProjectPublicationRegistry publicationRegistry;
        private final DefaultProjectDependencyFactory projectDependencyFactory;

        private final Map<PluginId, PluginResult> results = new ConcurrentHashMap<>();

        private CompositeBuildPluginResolver(
            BuildIncluder buildIncluder,
            ProjectPublicationRegistry publicationRegistry,
            DefaultProjectDependencyFactory projectDependencyFactory
        ) {
            this.buildIncluder = buildIncluder;
            this.publicationRegistry = publicationRegistry;
            this.projectDependencyFactory = projectDependencyFactory;
        }

        @Override
        public PluginResolutionResult resolve(PluginRequestInternal pluginRequest) {
            PluginResult resolutionResult = results.computeIfAbsent(pluginRequest.getId(), this::doResolve);
            if (resolutionResult == PluginResult.NOT_FOUND_IN_ANY_BUILD) {
                return PluginResolutionResult.notFound(SOURCE_DESCRIPTION, "None of the included builds contain this plugin");
            } else if (resolutionResult instanceof ResolvedPlugin) {
                return PluginResolutionResult.found(((ResolvedPlugin) resolutionResult).resolution);
            }

            return PluginResolutionResult.notFound(SOURCE_DESCRIPTION, "No included builds contain this plugin");
        }

        private PluginResult doResolve(PluginId pluginId) {
            PluginResolution earlyResolution = resolveFromIncludedPluginBuilds(pluginId);
            if (earlyResolution != null) {
                return new ResolvedPlugin(earlyResolution);
            }
            return resolvePluginFromIncludedBuilds(pluginId);
        }

        private PluginResult resolvePluginFromIncludedBuilds(PluginId requestedPluginId) {
            Collection<IncludedBuildState> includedBuilds = buildIncluder.getIncludedBuildsForPluginResolution();
            if (includedBuilds.isEmpty()) {
                return PluginResult.NO_INCLUDED_BUILDS;
            }
            for (IncludedBuildState build : includedBuilds) {
                PluginResolution pluginResolution = resolvePlugin(requestedPluginId, build.getBuildIdentifier());
                if (pluginResolution != null) {
                    return new ResolvedPlugin(pluginResolution);
                }
            }
            return PluginResult.NOT_FOUND_IN_ANY_BUILD;
        }

        private PluginResolution resolveFromIncludedPluginBuilds(PluginId requestedPluginId) {
            for (IncludedBuildState build : buildIncluder.getRegisteredPluginBuilds()) {
                buildIncluder.prepareForPluginResolution(build);
                PluginResolution pluginResolution = resolvePlugin(requestedPluginId, build.getBuildIdentifier());
                if (pluginResolution != null) {
                    return pluginResolution;
                }
            }
            return null;
        }

        @Nullable
        private PluginResolution resolvePlugin(PluginId requestedPluginId, BuildIdentifier buildIdentity) {
            Collection<ProjectPublicationRegistry.PublicationForProject<PluginPublication>> publicationsForBuild =
                publicationRegistry.getPublicationsForBuild(PluginPublication.class, buildIdentity);

            for (ProjectPublicationRegistry.PublicationForProject<PluginPublication> publication : publicationsForBuild) {
                PluginId pluginId = publication.getPublication().getPluginId();
                if (pluginId.equals(requestedPluginId)) {
                    return new LocalPluginResolution(pluginId, publication.getProducingProjectId().getBuildTreePath(), projectDependencyFactory);
                }
            }

            return null;
        }

        public void discardAll() {
            results.clear();
        }
    }

    private static class LocalPluginResolution implements PluginResolution {

        private final PluginId pluginId;
        private final Path producingProjectIdentityPath;
        private final DefaultProjectDependencyFactory projectDependencyFactory;

        public LocalPluginResolution(
            PluginId pluginId,
            Path producingProjectIdentityPath,
            DefaultProjectDependencyFactory projectDependencyFactory
        ) {
            this.pluginId = pluginId;
            this.producingProjectIdentityPath = producingProjectIdentityPath;
            this.projectDependencyFactory = projectDependencyFactory;
        }

        @Override
        public PluginId getPluginId() {
            return pluginId;
        }

        @Override
        public void accept(PluginResolutionVisitor visitor) {
            visitor.visitDependency(projectDependencyFactory.create(producingProjectIdentityPath));
        }

        @Override
        public void applyTo(PluginManagerInternal pluginManager) {
            pluginManager.apply(pluginId.getId());
        }
    }

}
