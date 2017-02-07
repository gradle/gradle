package org.gradle.plugin.management.internal;

import org.gradle.plugin.management.PluginResolutionSpec;
import org.gradle.plugin.use.PluginId;
import org.gradle.plugin.use.internal.DefaultPluginId;

public class ResolvedPluginRequest implements PluginResolutionSpec {

    private final Object target;

    private PluginId pluginId;

    ResolvedPluginRequest(Object target) {
        this.target = target;
    }

    @Override
    public void usePluginName(String name) {
        pluginId = DefaultPluginId.of(name);
    }

    public Object getTarget() {
        return target;
    }

    public PluginId getPluginId() {
        return pluginId;
    }
}
