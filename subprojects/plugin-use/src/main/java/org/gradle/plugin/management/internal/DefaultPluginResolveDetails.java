package org.gradle.plugin.management.internal;

import org.gradle.api.Action;
import org.gradle.plugin.management.ConfigurablePluginRequest;
import org.gradle.plugin.management.PluginRequest;
import org.gradle.plugin.management.PluginResolveDetails;

public class DefaultPluginResolveDetails implements PluginResolveDetails {

    private final ConfigurablePluginRequest pluginRequest;

    DefaultPluginResolveDetails(ConfigurablePluginRequest pluginRequest) {
        this.pluginRequest = pluginRequest;
    }

    @Override
    public PluginRequest getRequestedPlugin() {
        return pluginRequest;
    }

    @Override
    public void useTarget(Action<? super ConfigurablePluginRequest> action) {
        action.execute(pluginRequest);
    }

}
