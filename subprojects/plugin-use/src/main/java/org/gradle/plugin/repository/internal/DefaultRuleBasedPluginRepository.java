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

package org.gradle.plugin.repository.internal;

import org.gradle.api.Action;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.plugin.repository.RuleBasedPluginRepository;
import org.gradle.plugin.repository.rules.PluginDependencyHandler;
import org.gradle.plugin.use.resolve.internal.PluginResolver;
import org.gradle.plugin.use.resolve.service.internal.ResolutionServiceResolver;
import org.gradle.plugin.use.resolve.service.internal.RulesBasedPluginResolver;

public class DefaultRuleBasedPluginRepository implements RuleBasedPluginRepository, PluginRepositoryInternal, BackedByArtifactRepositories {


    private final ResolutionServiceResolver resolutionServiceResolver;

    private String description = "Default rule based plugin repository";
    private Action<? super PluginDependencyHandler> ruleBasedPluginResolution = new EmptyAction<PluginDependencyHandler>();
    private Action<? super RepositoryHandler> repositoriesAction = new EmptyAction<RepositoryHandler>();

    public DefaultRuleBasedPluginRepository(ResolutionServiceResolver resolutionServiceResolver) {
        this.resolutionServiceResolver = resolutionServiceResolver;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public void artifactRepositories(Action<? super RepositoryHandler> repositoriesAction) {
        this.repositoriesAction = repositoriesAction;
    }

    @Override
    public Action<? super RepositoryHandler> getArtifactRepositories() {
        return this.repositoriesAction;
    }

    @Override
    public void pluginResolution(Action<? super PluginDependencyHandler> resolution) {
        this.ruleBasedPluginResolution = resolution;
    }

    @Override
    public Action<? super PluginDependencyHandler> getPluginResolution() {
        return this.ruleBasedPluginResolution;
    }

    @Override
    public PluginResolver asResolver() {
        return new RulesBasedPluginResolver(this, resolutionServiceResolver);
    }

    @Override
    public void createArtifactRepositories(RepositoryHandler repositoryHandler) {
        repositoriesAction.execute(repositoryHandler);

    }

    static class EmptyAction<T> implements Action<T> {
        @Override
        public void execute(T action) {
        }
    }
}
