package org.gradle.plugin.management.internal;

import org.gradle.api.Action;
import org.gradle.api.Nullable;
import org.gradle.plugin.management.PluginRequest;
import org.gradle.plugin.management.PluginResolveDetails;

public class DefaultPluginResolutionStrategy implements InternalPluginResolutionStrategy {

    private final PluginResolutions pluginResolutions = new PluginResolutions();

    @Override
    public void eachPlugin(Action<? super PluginResolveDetails> rule) {
        pluginResolutions.add(rule);
    }

    @Override
    @Nullable
    public ResolvedPluginRequest resolvePluginRequest(PluginRequest pluginRequest) {
        return pluginResolutions.resolveRequest(pluginRequest);
    }
}
