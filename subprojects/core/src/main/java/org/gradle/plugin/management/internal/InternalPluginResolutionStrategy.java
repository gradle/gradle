package org.gradle.plugin.management.internal;

import org.gradle.plugin.management.PluginResolutionStrategy;
import org.gradle.plugin.use.internal.InternalPluginRequest;

public interface InternalPluginResolutionStrategy extends PluginResolutionStrategy {

    void resolvePluginRequest(InternalPluginRequest pluginRequest);
}
