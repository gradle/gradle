/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.internal.dependencies;

import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.dsl.DependencyFactory;
import org.gradle.plugin.use.PluginDependency;

import javax.annotation.Nullable;

public class PluginDependencyMarkerCoordinates {
    private PluginDependencyMarkerCoordinates() {}

    public static ExternalModuleDependency getExternalModuleDependency(DependencyFactory dependencyFactory, PluginDependency pluginDependency) {
        String pluginNotation = pluginNotation(pluginDependency.getPluginId(), null);
        ExternalModuleDependency externalModuleDependency = dependencyFactory.create(pluginNotation);
        externalModuleDependency.version(versionConstraint -> versionConstraint.copyFrom(pluginDependency.getVersion()));
        return externalModuleDependency;
    }

    public static String pluginNotation(String id, @Nullable String version) {
        String notation = id + ":" + pluginName(id);
        return version == null ? notation : notation + ":" + version;
    }

    public static String pluginName(String id) {
        return id + ".gradle.plugin";
    }
}
