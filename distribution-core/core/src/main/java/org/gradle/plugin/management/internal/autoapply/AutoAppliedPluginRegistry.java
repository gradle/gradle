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
import org.gradle.api.initialization.Settings;
import org.gradle.plugin.management.internal.PluginRequests;

/**
 * Provides a list of plugins that can be auto-applied to a certain Project.
 *
 * @since 4.3
 */
public interface AutoAppliedPluginRegistry {

    /**
     * Returns the plugins that should be auto-applied to the given
     * target, based on the current build invocation.
     */
    PluginRequests getAutoAppliedPlugins(Project target);

    PluginRequests getAutoAppliedPlugins(Settings target);
}
