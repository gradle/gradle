package org.gradle.plugin.management.internal;

import org.gradle.plugin.management.ConfigurablePluginRequest;
import org.gradle.plugin.management.PluginRequest;
import org.gradle.plugin.use.PluginId;
import org.gradle.plugin.use.internal.DefaultPluginId;

public class DefaultConfigurablePluginRequest implements ConfigurablePluginRequest {

    private PluginId pluginId;
    private String version;
    private Object target;

    public DefaultConfigurablePluginRequest(PluginId pluginId, String version) {
        this.pluginId = pluginId;
        this.version = version;
    }

    @Override
    public void setVersion(String version) {
        this.version = version;
    }

    @Override
    public PluginId getId() {
        return pluginId;
    }

    public String getVersion() {
        return version;
    }

    @Override
    public void useTarget(String target) {
        this.target = target;
    }

    public Object getTarget() {
        return target;
    }

    public static ConfigurablePluginRequest from(PluginRequest pluginRequest) {
        return new DefaultConfigurablePluginRequest(pluginRequest.getId(), pluginRequest.getVersion());
    }
}
