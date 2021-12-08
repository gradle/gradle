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

package org.gradle.plugin.use.tracker.internal;

import org.gradle.api.internal.initialization.ClassLoaderScopeData;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

public class PluginVersionTracker implements ClassLoaderScopeData {
    private final PluginVersionTracker parent;
    private final Map<String, String> pluginVersions = new HashMap<>();

    public PluginVersionTracker(@Nullable PluginVersionTracker parent) {
        this.parent = parent;
    }

    public void setPluginVersion(String id, String version) {
        if (pluginVersions.containsKey(id)) {
            throw new IllegalStateException("Plugin version already set for " + id);
        }
        pluginVersions.put(id, version);
    }

    @Nullable
    public String getPluginVersion(String id) {
        String version = pluginVersions.get(id);
        if (version != null) {
            return version;
        }
        if (parent != null) {
            return parent.getPluginVersion(id);
        }
        return null;
    }

    @Nullable
    @Override
    public ClassLoaderScopeData createChild() {
        return new PluginVersionTracker(this);
    }
}
