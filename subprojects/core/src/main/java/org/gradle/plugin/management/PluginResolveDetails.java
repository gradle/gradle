/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.plugin.management;

import org.gradle.api.Action;
import org.gradle.api.Incubating;

/**
 * Details on a Plugin that's been requested.
 *
 * @since 3.5
 */
@Incubating
public interface PluginResolveDetails {

    /**
     * Get the plugin that was requested. Chained calls will see the change from previous mutator.
     *
     * @return the requested plugin.
     */
    PluginRequest getRequested();

    /**
     * Allows user to specify which artifact should be used for a give {@link org.gradle.plugin.use.PluginId}
     *
     * @param action the notation that gets parsed into an instance of {@link org.gradle.api.artifacts.ModuleVersionSelector}.
     * You can pass Strings like 'org.gradle:gradle-core:1.4',
     * Maps like [group: 'org.gradle', name: 'gradle-core', version: '1.4'],
     * or instances of ModuleVersionSelector.
     *
     * @since 3.5
     */
    void useTarget(Action<? super ConfigurablePluginRequest> action);

    PluginRequest getTarget();

}
