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

package org.gradle.api.internal.artifacts.dsl.dependencies;

import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.artifacts.dsl.DependencyFactory;
import org.gradle.plugin.use.PluginDependency;

public class PluginDependencyMarkerCoordinates {
    private PluginDependencyMarkerCoordinates() {}

    static ExternalModuleDependency setVersion(DependencyFactory dependencyFactory, PluginDependency pluginDependency) {
        String pluginNotation = pluginNotation(pluginDependency.getPluginId());
        ExternalModuleDependency externalModuleDependency = dependencyFactory.create(pluginNotation);
        externalModuleDependency.version(versionConstraint -> {
            VersionConstraint constraint = pluginDependency.getVersion();
            versionConstraint.setBranch(constraint.getBranch());
            versionConstraint.require(constraint.getRequiredVersion());
            versionConstraint.prefer(constraint.getPreferredVersion());
            versionConstraint.strictly(constraint.getStrictVersion());
            versionConstraint.reject(constraint.getRejectedVersions().toArray(new String[0]));
        });
        return externalModuleDependency;
    }

    static String pluginNotation(String id) {
        return id + ":" + id + ".gradle.plugin";
    }
}
