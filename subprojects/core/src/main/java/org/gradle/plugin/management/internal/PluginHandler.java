/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.plugin.management.internal;

import org.gradle.api.initialization.Settings;
import org.gradle.plugin.management.internal.argumentloaded.ArgumentSourcedPluginHandler;
import org.gradle.plugin.management.internal.autoapply.AutoAppliedPluginHandler;

/**
 * Combines the functionality of {@link AutoAppliedPluginHandler} and {@link ArgumentSourcedPluginHandler}
 * and provides a single mechanism for consolidating all explicit and implicit plugin requests.
 */
public interface PluginHandler extends AutoAppliedPluginHandler, ArgumentSourcedPluginHandler {
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
        PluginRequests argumentLoadedPlugins = pluginTarget instanceof Settings ? getArgumentSourcedPlugins() : PluginRequests.EMPTY;
        return initialPluginRequests.mergeWith(autoAppliedPlugins).mergeWith(argumentLoadedPlugins);
    }
}
