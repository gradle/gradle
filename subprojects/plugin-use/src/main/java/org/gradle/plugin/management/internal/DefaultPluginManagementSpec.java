/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.plugin.management.internal;

import org.gradle.api.Action;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.initialization.ConfigurableIncludedPluginBuild;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderConvertible;
import org.gradle.initialization.IncludedBuildSpec;
import org.gradle.internal.Actions;
import org.gradle.internal.build.BuildIncluder;
import org.gradle.plugin.management.PluginResolutionStrategy;
import org.gradle.plugin.use.PluginDependenciesSpec;
import org.gradle.plugin.use.PluginDependency;
import org.gradle.plugin.use.PluginDependencySpec;
import org.gradle.plugin.use.PluginId;
import org.gradle.plugin.use.internal.DefaultPluginId;
import org.gradle.plugin.use.internal.PluginRepositoryHandlerProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DefaultPluginManagementSpec implements PluginManagementSpecInternal {

    private final PluginRepositoryHandlerProvider pluginRepositoryHandlerProvider;
    private final PluginResolutionStrategyInternal pluginResolutionStrategy;
    private final PluginDependenciesSpec pluginDependenciesSpec;
    private final FileResolver fileResolver;
    private final BuildIncluder buildIncluder;
    private final GradleInternal gradle;

    private final List<IncludedBuildSpec> includedBuildSpecs = new ArrayList<>();

    public DefaultPluginManagementSpec(PluginRepositoryHandlerProvider pluginRepositoryHandlerProvider, PluginResolutionStrategyInternal pluginResolutionStrategy, FileResolver fileResolver, BuildIncluder buildIncluder, GradleInternal gradle) {
        this.pluginRepositoryHandlerProvider = pluginRepositoryHandlerProvider;
        this.pluginResolutionStrategy = pluginResolutionStrategy;
        this.fileResolver = fileResolver;
        this.buildIncluder = buildIncluder;
        this.gradle = gradle;
        this.pluginDependenciesSpec = new PluginDependenciesSpecImpl();
    }

    @Override
    public void repositories(Action<? super RepositoryHandler> repositoriesAction) {
        repositoriesAction.execute(getRepositories());
    }

    @Override
    public RepositoryHandler getRepositories() {
        return pluginRepositoryHandlerProvider.getPluginRepositoryHandler();
    }

    @Override
    public void resolutionStrategy(Action<? super PluginResolutionStrategy> action) {
        action.execute(pluginResolutionStrategy);
    }

    @Override
    public PluginResolutionStrategyInternal getResolutionStrategy() {
        return pluginResolutionStrategy;
    }

    @Override
    public void plugins(Action<? super PluginDependenciesSpec> action) {
        action.execute(pluginDependenciesSpec);
    }

    @Override
    public PluginDependenciesSpec getPlugins() {
        return pluginDependenciesSpec;
    }

    @Override
    public void includeBuild(String rootProject) {
        includeBuild(rootProject, Actions.doNothing());
    }

    @Override
    public void includeBuild(String rootProject, Action<ConfigurableIncludedPluginBuild> configuration) {
        File projectDir = fileResolver.resolve(rootProject);
        IncludedBuildSpec buildSpec = IncludedBuildSpec.includedPluginBuild(projectDir, configuration);
        buildIncluder.registerPluginBuild(buildSpec, gradle);
        includedBuildSpecs.add(buildSpec);
    }

    @Override
    public List<IncludedBuildSpec> getIncludedBuilds() {
        return includedBuildSpecs;
    }

    private class PluginDependenciesSpecImpl implements PluginDependenciesSpec {
        @Override
        public PluginDependencySpec id(String id) {
            return new PluginDependencySpecImpl(DefaultPluginId.of(id));
        }

        @Override
        public PluginDependencySpec alias(Provider<PluginDependency> notation) {
            PluginDependency pluginDependency = notation.get();
            return id(pluginDependency.getPluginId()).version(pluginDependency.getVersion().getRequiredVersion());
        }

        @Override
        public PluginDependencySpec alias(ProviderConvertible<PluginDependency> notation) {
            return alias(notation.asProvider());
        }
    }

    private class PluginDependencySpecImpl implements PluginDependencySpec {
        private final PluginId id;

        private PluginDependencySpecImpl(PluginId id) {
            this.id = id;
        }

        @Override
        public PluginDependencySpec version(String version) {
            pluginResolutionStrategy.setDefaultPluginVersion(id, version);
            return this;
        }

        @Override
        public PluginDependencySpec apply(boolean apply) {
            if (apply) {
                throw new IllegalArgumentException("Cannot apply a plugin from within a pluginManagement block.");
            }
            return this;
        }
    }

}
