package org.gradle.plugin.management.internal;

import org.gradle.api.Action;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.plugin.repository.PluginRepositoriesSpec;

public class DefaultPluginManagementSpec implements InternalPluginManagementSpec {

    private final PluginRepositoriesSpec delegate;
    private final InternalPluginResolutionStrategy pluginResolutionStrategy = new DefaultPluginResolutionStrategy();

    public DefaultPluginManagementSpec(PluginRepositoriesSpec delegate) {
        this.delegate = delegate;
    }

    @Override
    public void repositories(Action<? super PluginRepositoriesSpec> repositoriesAction) {
        repositoriesAction.execute(delegate);
    }

    @Override
    public InternalPluginResolutionStrategy getPluginResolutionStrategy() {
        return pluginResolutionStrategy;
    }

}
