package org.gradle.plugin.management.internal;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.plugin.repository.PluginRepositoriesSpec;
import org.gradle.util.ConfigureUtil;

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
    public void repositories(Closure closure) {
        ConfigureUtil.configure(closure, delegate);
    }

    @Override
    public InternalPluginResolutionStrategy getPluginResolutionStrategy() {
        return pluginResolutionStrategy;
    }

}
