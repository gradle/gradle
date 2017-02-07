package org.gradle.plugin.management.internal;

import org.gradle.plugin.management.PluginRequest;
import org.gradle.plugin.management.PluginResolutionStrategy;

public interface InternalPluginResolutionStrategy extends PluginResolutionStrategy {

    ResolvedPluginRequest resolvePluginRequest(PluginRequest pluginRequest);
}
