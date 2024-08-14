/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.plugin.management.internal.autoapply;

import org.gradle.api.Project;
import org.gradle.plugin.management.PluginRequest;
import org.gradle.plugin.management.internal.ArgumentLoadedPluginRequest;
import org.gradle.plugin.management.internal.PluginRequestInternal;
import org.gradle.plugin.management.internal.PluginRequests;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 * Certain plugins are important enough that Gradle should auto-apply them when it is clear
 * that the user is trying to use it. For instance, when the user uses the <code>--scan</code> option, it
 * is clear they expect the build scan plugin to be applied.
 * </p>
 * Auto-application of a plugin is skipped in the following situations, so the user can adjust the version they want:
 * <ul>
 * <li> The plugin was already applied (e.g. through an init script)
 * <li> The plugin was already requested in the <code>plugins {}</code> block </li>
 * <li> The plugin was already requested in the <code>buildscript {}</code> block </li>
 * </ul>
 * <p>
 * Gradle also allows for automatically loading plugins via a system property, this class
 * understands how to discover these plugin requests as well.
 */
public interface AutoAppliedPluginHandler {
    String INIT_PROJECT_SPEC_SUPPLIERS_PROP = "org.gradle.internal.buildinit.projectspecs";

    /**
     * Returns plugin requests that should be auto-applied
     * based on user requests, the current build invocation and the given target.
     */
    PluginRequests getAutoAppliedPlugins(PluginRequests initialRequests, Object pluginTarget);

    /**
     * A static util class responsible for gathering {@link PluginRequest}s added outside of any build script.
     * <p>
     * These originate from an id + version pair in the form of {@code id:version}.  These are currently
     * parsed from a system property {@link #INIT_PROJECT_SPEC_SUPPLIERS_PROP}.
     */
    static PluginRequests getArgumentLoadedPlugins() {
        String propValue = System.getProperty(INIT_PROJECT_SPEC_SUPPLIERS_PROP);
        if (propValue == null) {
            return PluginRequests.EMPTY;
        } else {
            String[] pluginRequests = propValue.split(",");
            List<PluginRequestInternal> templatePluginRequests = Arrays.stream(pluginRequests)
                .map(ArgumentLoadedPluginRequest::parsePluginRequest)
                .collect(Collectors.toList());
            return PluginRequests.of(templatePluginRequests);
        }
    }

    /**
     * Returns all plugin requests that should be applied to the given target, including
     * implicit and external requests.
     * <p>
     * This includes:
     * <ul>
     *     <li>The initial plugin requests, explicitly declared by the user in a script</li>
     *     <li>The auto-applied plugins, based on user requests, the current build invocation and the given target</li>
     *     <li>The plugins loaded via the System Property (for loading additional project types for use by the {@code init} task)</li>
     * </ul>
     *
     * @param initialPluginRequests the initial plugin requests, explicitly declared by the user in a script
     * @param pluginTarget the target object to apply the plugins to
     */
    default PluginRequests getAllPluginRequests(PluginRequests initialPluginRequests, Object pluginTarget) {
        PluginRequests autoAppliedPlugins = getAutoAppliedPlugins(initialPluginRequests, pluginTarget);
        PluginRequests argumentLoadedPlugins = pluginTarget instanceof Project ? getArgumentLoadedPlugins() : PluginRequests.EMPTY;
        return initialPluginRequests.mergeWith(autoAppliedPlugins).mergeWith(argumentLoadedPlugins);
    }
}
