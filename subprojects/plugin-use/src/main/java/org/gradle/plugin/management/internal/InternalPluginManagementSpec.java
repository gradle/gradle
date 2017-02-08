package org.gradle.plugin.management.internal;

import org.gradle.plugin.management.PluginManagementSpec;

public interface InternalPluginManagementSpec extends PluginManagementSpec {

    InternalPluginResolutionStrategy getPluginResolutionStrategy();
}
