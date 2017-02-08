package org.gradle.plugin.management.internal;

import org.gradle.plugin.management.ConfigurablePluginRequest;
import org.gradle.plugin.management.PluginRequest;
import org.gradle.plugin.management.PluginResolutionStrategy;

public interface InternalPluginResolutionStrategy extends PluginResolutionStrategy {

    ConfigurablePluginRequest resolvePluginRequest(PluginRequest pluginRequest);
}
