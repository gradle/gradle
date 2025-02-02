/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.plugins.ide.idea.internal;

import org.gradle.api.NonNullApi;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.internal.project.ProjectInternal;

/**
 * Utilities that are introduced to aid with Isolated Projects adoption in the implementation.
 * <p>
 * This class is intended to be temporary and should be removed as soon as proper building blocks
 * are available to solve the use cases without the workarounds.
 */
@NonNullApi
class IdeaIsolatedProjectsWorkarounds {

    /**
     * Checks whether the project has the plugin applied.
     * <p>
     * The check is done bypassing the Isolated Projects validations.
     */
    public static boolean hasPlugin(Project project, Class<? extends Plugin<?>> pluginClass) {
        return ((ProjectInternal) project).getOwner().getMutableModel().getPluginManager().getPluginContainer()
            .hasPlugin(pluginClass);
    }

}
