package org.gradle.plugin.management.internal;

import org.gradle.api.Action;
import org.gradle.plugin.management.PluginResolveDetails;
import org.gradle.plugin.use.internal.InternalPluginRequest;

public class DefaultPluginResolutionStrategy implements InternalPluginResolutionStrategy {

    private final PluginResolutions pluginResolutions = new PluginResolutions();

    @Override
    public void eachPlugin(Action<? super PluginResolveDetails> rule) {
        pluginResolutions.add(rule);
    }

    @Override
    public void resolvePluginRequest(InternalPluginRequest pluginRequest) {
        pluginResolutions.resolveRequest(pluginRequest);
    }
}
