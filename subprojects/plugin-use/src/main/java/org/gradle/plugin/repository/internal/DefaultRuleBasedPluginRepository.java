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
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.plugin.repository.RuleBasedPluginRepository;
import org.gradle.plugin.repository.rules.PluginDependency;
import org.gradle.plugin.repository.rules.PluginRequest;
import org.gradle.plugin.repository.rules.RuleBasedPluginResolution;
import org.gradle.plugin.repository.rules.RuleBasedArtifactRepositories;
import org.gradle.plugin.use.resolve.internal.PluginResolver;
import org.gradle.plugin.use.resolve.service.internal.ResolutionServiceResolver;
import org.gradle.plugin.use.resolve.service.internal.RulesBasedPluginResolver;

import java.util.List;

public class DefaultRuleBasedPluginRepository implements RuleBasedPluginRepository, PluginRepositoryInternal, BackedByArtifactRepositories {


    private final ResolutionServiceResolver resolutionServiceResolver;

    private String description = "Default rule based plugin repository";
    private RuleBasedPluginResolution ruleBasedPluginResolution = new EmptyRuleBasedPluginResolution();
    private Action<RuleBasedArtifactRepositories> repositoriesAction = new EmptyRuleBasedArtifactRepositoriesAction();

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
    public void artifactRepositories(Action<RuleBasedArtifactRepositories> repositoriesAction) {
        this.repositoriesAction = repositoriesAction;
    }

    @Override
    public void pluginResolution(RuleBasedPluginResolution resolution) {
        this.ruleBasedPluginResolution = resolution;
    }

    @Override
    public PluginResolver asResolver() {
        return new RulesBasedPluginResolver(ruleBasedPluginResolution, getDescription(), resolutionServiceResolver);
    }

    @Override
    public List<ArtifactRepository> createArtifactRepositories(RepositoryHandler repositoryHandler) {
        DefaultRuleBasedArtifactRepositories repositories = new DefaultRuleBasedArtifactRepositories(repositoryHandler);
        repositoriesAction.execute(repositories);
        return repositories.getArtifactRepositories();
    }

    static class EmptyRuleBasedPluginResolution implements RuleBasedPluginResolution {
        @Override
        public void findPlugin(PluginRequest plugin, PluginDependency target) {
        }
    }

    static class EmptyRuleBasedArtifactRepositoriesAction implements Action<RuleBasedArtifactRepositories> {
        @Override
        public void execute(RuleBasedArtifactRepositories ruleBasedArtifactRepositories) {
        }
    }
}
