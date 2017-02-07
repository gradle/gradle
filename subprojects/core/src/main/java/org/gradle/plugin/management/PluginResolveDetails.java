package org.gradle.plugin.management;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.plugin.use.PluginId;

@Incubating
public interface PluginResolveDetails {

    PluginRequest getRequestedPlugin();

    /**
     * Allows user to specify which artifact should be used for a given {@link PluginId}
     *
     * @param notation the notation that gets parsed into an instance of {@link ModuleVersionSelector}.
     * You can pass Strings like 'org.gradle:gradle-core:1.4',
     * Maps like [group: 'org.gradle', name: 'gradle-core', version: '1.4'],
     * or instances of ModuleVersionSelector.
     *
     * @since 3.4
     */
    void useTarget(Object notation);

    /**
     * Allows user to specify which artifact should be used for a given {@link PluginId}
     *
     * @param notation the notation that gets parsed into an instance of {@link ModuleVersionSelector}.
     * You can pass Strings like 'org.gradle:gradle-core:1.4',
     * Maps like [group: 'org.gradle', name: 'gradle-core', version: '1.4'],
     * or instances of ModuleVersionSelector.
     * @param details Configure the details of the plugin.
     *
     * @since 3.4
     */
    void useTarget(Object notation, Action<? super PluginResolutionSpec> details);
}
