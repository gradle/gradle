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

import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Collections.emptyMap;

/**
 * Tracks plugin versions available at different {@link org.gradle.api.internal.initialization.ClassLoaderScope scopes}.
 */
@ServiceScope(Scopes.Build.class)
public class PluginVersionTracker {

    final Map<ClassLoaderScope, Map<String, String>> pluginVersionsPerScope = new ConcurrentHashMap<>();

    public void setPluginVersionAt(ClassLoaderScope scope, String pluginId, String pluginVersion) {
        Map<String, String> pluginVersions = pluginVersionsPerScope.computeIfAbsent(scope.getOriginalScope(), ignored -> new ConcurrentHashMap<>());
        if (pluginVersions.containsKey(pluginId)) {
            throw new IllegalStateException("Plugin version already set for " + pluginId);
        }
        pluginVersions.put(pluginId, pluginVersion);
    }

    @Nullable
    public String findPluginVersionAt(ClassLoaderScope scope, String pluginId) {
        while (scope != null) {
            String pluginVersion = pluginVersionsPerScope.getOrDefault(scope.getOriginalScope(), emptyMap()).get(pluginId);
            if (pluginVersion != null) {
                return pluginVersion;
            }
            ClassLoaderScope parent = scope.getParent();
            if (scope == parent) {
                // See RootClassLoaderScope#getParent()
                break;
            }
            scope = parent;
        }
        return null;
    }
}
