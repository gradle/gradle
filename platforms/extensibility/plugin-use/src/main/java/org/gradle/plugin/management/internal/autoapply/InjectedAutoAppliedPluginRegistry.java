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
import org.gradle.api.internal.BuildDefinition;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.plugin.management.internal.PluginRequests;

public class InjectedAutoAppliedPluginRegistry implements AutoAppliedPluginRegistry {

    private final BuildDefinition buildDefinition;

    public InjectedAutoAppliedPluginRegistry(BuildDefinition buildDefinition) {
        this.buildDefinition = buildDefinition;
    }

    @Override
    public PluginRequests getAutoAppliedPlugins(Project target) {
        return PluginRequests.EMPTY;
    }

    @Override
    public PluginRequests getAutoAppliedPlugins(Settings target) {
        if (((StartParameterInternal) target.getStartParameter()).isUseEmptySettings()) {
            return PluginRequests.EMPTY;
        }

        return buildDefinition.getInjectedPluginRequests();
    }

}
