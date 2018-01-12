/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal;

import org.gradle.StartParameter;
import org.gradle.plugin.management.internal.DefaultPluginRequests;
import org.gradle.plugin.management.internal.PluginRequests;

public class BuildDefinition {
    private final StartParameter startParameter;
    private final PluginRequests injectedSettingsPlugins;

    public BuildDefinition(StartParameter startParameter, PluginRequests injectedSettingsPlugins) {
        this.startParameter = startParameter;
        this.injectedSettingsPlugins = injectedSettingsPlugins;
    }

    public StartParameter getStartParameter() {
        return startParameter;
    }

    public PluginRequests getInjectedPluginRequests() {
        return injectedSettingsPlugins;
    }

    public static BuildDefinition fromStartParameter(StartParameter startParameter) {
        return new BuildDefinition(startParameter, DefaultPluginRequests.EMPTY);
    }
}
