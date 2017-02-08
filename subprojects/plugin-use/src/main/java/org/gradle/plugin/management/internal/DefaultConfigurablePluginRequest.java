package org.gradle.plugin.management.internal;

import org.gradle.plugin.management.ConfigurablePluginRequest;
import org.gradle.plugin.use.PluginId;
import org.gradle.plugin.use.internal.InternalPluginRequest;

public class DefaultConfigurablePluginRequest implements ConfigurablePluginRequest {

    private final InternalPluginRequest origonalRequest;

    public DefaultConfigurablePluginRequest(InternalPluginRequest origonalRequest) {
        this.origonalRequest = origonalRequest;
    }

    @Override
    public void setVersion(String version) {
        this.origonalRequest.getConfiguredOptions().setVersion(version);
    }

    @Override
    public PluginId getId() {
        return this.origonalRequest.getId();
    }

    public String getVersion() {
        if(this.origonalRequest.getConfiguredOptions().isVersionSet()) {
            return origonalRequest.getConfiguredOptions().getVersion();
        } else {
            return origonalRequest.getVersion();
        }
    }

    @Override
    public void useTarget(String target) {
        origonalRequest.getConfiguredOptions().setTarget(target);
    }

    public Object getTarget() {
        return origonalRequest.getConfiguredOptions().getTarget();
    }

}
