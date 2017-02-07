package org.gradle.plugin.management.internal;

import org.gradle.api.Action;
import org.gradle.plugin.management.PluginRequest;
import org.gradle.plugin.management.PluginResolutionSpec;
import org.gradle.plugin.management.PluginResolveDetails;

public class DefaultPluginResolveDetails implements PluginResolveDetails {

    private ResolvedPluginRequest resolvedPluginRequest;
    private final PluginRequest pluginRequest;

    DefaultPluginResolveDetails(PluginRequest pluginRequest) {
        this.pluginRequest = pluginRequest;
    }

    @Override
    public PluginRequest getRequestedPlugin() {
        return pluginRequest;
    }

    @Override
    public void useTarget(Object notation) {
        resolvedPluginRequest = new ResolvedPluginRequest(notation);
    }

    @Override
    public void useTarget(Object notation, Action<? super PluginResolutionSpec> details) {
        resolvedPluginRequest = new ResolvedPluginRequest(notation);
        details.execute(resolvedPluginRequest);
    }

    ResolvedPluginRequest getResolvedPluginRequest() {
        return resolvedPluginRequest;
    }
}
