/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.plugin.use.resolve.service.internal;

import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.internal.artifacts.DependencyResolutionServices;
import org.gradle.internal.Factories;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.plugin.repository.RuleBasedPluginRepository;
import org.gradle.plugin.repository.rules.PluginDependencyHandler;
import org.gradle.plugin.repository.rules.PluginModuleOptions;
import org.gradle.plugin.repository.rules.PluginRequest;
import org.gradle.plugin.use.PluginId;
import org.gradle.plugin.use.internal.DefaultPluginId;
import org.gradle.plugin.use.internal.InternalPluginRequest;
import org.gradle.plugin.use.internal.InvalidPluginRequestException;
import org.gradle.plugin.use.resolve.internal.PluginResolution;
import org.gradle.plugin.use.resolve.internal.PluginResolutionResult;
import org.gradle.plugin.use.resolve.internal.PluginResolveContext;
import org.gradle.plugin.use.resolve.internal.PluginResolver;

public class RulesBasedPluginResolver implements PluginResolver {

    private final RuleBasedPluginRepository ruleBasedPluginRepository;
    private final ResolutionServiceResolver resolutionServiceResolver;

    public RulesBasedPluginResolver(RuleBasedPluginRepository ruleBasedPluginRepository,
                                    ResolutionServiceResolver resolutionServiceResolver) {
        this.ruleBasedPluginRepository = ruleBasedPluginRepository;
        this.resolutionServiceResolver = resolutionServiceResolver;
    }

    @Override
    public void resolve(final InternalPluginRequest pluginRequest, PluginResolutionResult result) throws InvalidPluginRequestException {
        DefaultPluginDependencyHandler defaultPluginDependency = new DefaultPluginDependencyHandler(pluginRequest);
        ruleBasedPluginRepository.getPluginResolution().execute(defaultPluginDependency);
        String description = ruleBasedPluginRepository.getDescription();

        if (null == defaultPluginDependency.options) {
            result.notFound(description, defaultPluginDependency.failureReason);
            return;
        }

        final DefaultPluginModuleOptions pluginOptions = defaultPluginDependency.options;
        final PluginId pluginId = DefaultPluginId.of(pluginOptions.pluginName);

        if (pluginOptions.isolatedClasspath) {
            DependencyResolutionServices resolution = resolutionServiceResolver.getDependencyResolutionServices();
            RepositoryHandler resolveRepositoryHandler = resolution.getResolveRepositoryHandler();

            ruleBasedPluginRepository.getArtifactRepositories().execute(resolveRepositoryHandler);

            ClassPath classPath = ResolutionServiceResolver.resolvePluginDependencies(resolution, pluginOptions.dependencyNotation, description);
            PluginResolution pluginResolution = resolutionServiceResolver.buildPluginResolution(pluginId, Factories.constant(classPath));
            result.found(description, pluginResolution);
        } else {
            result.found(description, new PluginResolution() {
                @Override
                public PluginId getPluginId() {
                    return pluginId;
                }

                public void execute(PluginResolveContext context) {
                    context.addLegacy(pluginId, pluginOptions.dependencyNotation);
                }
            });
        }
    }

    private class DefaultPluginDependencyHandler implements PluginDependencyHandler {

        private DefaultPluginModuleOptions options;
        private final PluginRequest pluginRequest;
        private String failureReason = null;

        private DefaultPluginDependencyHandler(PluginRequest pluginRequest) {
            this.pluginRequest = pluginRequest;
        }

        @Override
        public PluginRequest getRequestedPlugin() {
            return pluginRequest;
        }

        @Override
        public PluginModuleOptions useModule(Object dependencyNotation) {
            options = new DefaultPluginModuleOptions(dependencyNotation, pluginRequest.getId().asString());
            return options;
        }

        @Override
        public void notFound(String reason) {
            this.failureReason = reason;
        }
    }

    private static class DefaultPluginModuleOptions implements PluginModuleOptions {

        private final Object dependencyNotation;
        private boolean isolatedClasspath = false;
        private String pluginName;

        private DefaultPluginModuleOptions(Object dependencyNotation, String pluginName) {
            this.dependencyNotation = dependencyNotation;
            this.pluginName = pluginName;
        }

        @Override
        public PluginModuleOptions withIsolatedClasspath() {
            this.isolatedClasspath = true;
            return this;
        }

        @Override
        public Object getDependencyNotation() {
            return dependencyNotation;
        }

        @Override
        public PluginModuleOptions withPluginName(String pluginName) {
            this.pluginName = pluginName;
            return this;
        }

        @Override
        public String getPluginName() {
            return pluginName;
        }
    }
}
