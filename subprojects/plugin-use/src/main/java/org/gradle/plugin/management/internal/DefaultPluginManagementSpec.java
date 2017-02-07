package org.gradle.plugin.management.internal;

import org.gradle.api.Action;
import org.gradle.api.artifacts.dsl.RepositoryHandler;

import java.util.LinkedHashSet;
import java.util.Set;

public class DefaultPluginManagementSpec implements InternalPluginManagementSpec {

    private final InternalPluginResolutionStrategy pluginResolutionStrategy = new DefaultPluginResolutionStrategy();
    private final Set<Action<? super RepositoryHandler>> repositorySet = new LinkedHashSet<Action<? super RepositoryHandler>>();

    @Override
    public void repositories(Action<? super RepositoryHandler> repositoriesAction) {
        repositorySet.add(repositoriesAction);
    }

    @Override
    public void createArtifactRepositories(RepositoryHandler repositoryHandler) {
        for (Action<? super RepositoryHandler> action : repositorySet) {
            action.execute(repositoryHandler);
        }
    }

    @Override
    public InternalPluginResolutionStrategy getPluginResolutionStrategy() {
        return pluginResolutionStrategy;
    }

    @Override
    public PluginManagementPluginResolver getResolver() {
        return new DefaultPluginManagementPluginResolver(pluginResolutionStrategy);
    }
}
