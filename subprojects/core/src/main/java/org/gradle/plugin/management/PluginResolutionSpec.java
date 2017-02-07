package org.gradle.plugin.management;

import org.gradle.api.Incubating;

/**
 * This needs a better name.
 *
 * Allows user to specify details about the plugin that needs
 * to be loaded.
 */
@Incubating
public interface PluginResolutionSpec {

    void usePluginName(String name);
}
