/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.api.internal.artifacts.dependencies;

import org.gradle.api.artifacts.MutableVersionConstraint;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.plugin.use.PluginDependency;

public class DefaultPluginDependency implements PluginDependency {
    private final String pluginId;
    private final MutableVersionConstraint versionConstraint;

    public DefaultPluginDependency(String pluginId, MutableVersionConstraint versionConstraint) {
        this.pluginId = pluginId;
        this.versionConstraint = versionConstraint;
    }

    @Override
    public String getPluginId() {
        return pluginId;
    }

    @Override
    public VersionConstraint getVersion() {
        return versionConstraint;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultPluginDependency that = (DefaultPluginDependency) o;

        if (!pluginId.equals(that.pluginId)) {
            return false;
        }
        return versionConstraint.equals(that.versionConstraint);
    }

    @Override
    public int hashCode() {
        int result = pluginId.hashCode();
        result = 31 * result + versionConstraint.hashCode();
        return result;
    }

    @Override
    public String toString() {
        String versionConstraintAsString = versionConstraint.toString();
        return versionConstraintAsString.isEmpty()
            ? pluginId
            : pluginId + ":" + versionConstraintAsString;
    }
}
