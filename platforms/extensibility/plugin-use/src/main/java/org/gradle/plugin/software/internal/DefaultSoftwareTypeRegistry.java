/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.plugin.software.internal;

import com.google.common.collect.ImmutableList;
import org.gradle.api.Project;
import org.gradle.api.initialization.Settings;
import org.gradle.plugin.management.internal.DefaultPluginRequest;
import org.gradle.plugin.management.internal.PluginRequestInternal;
import org.gradle.plugin.management.internal.PluginRequests;
import org.gradle.plugin.use.internal.DefaultPluginId;

import java.util.List;
import java.util.stream.Collectors;

public class DefaultSoftwareTypeRegistry implements SoftwareTypeRegistry {
    private final ImmutableList.Builder<String> registeredPluginIds = new ImmutableList.Builder<>();
    private List<String> pluginIds;

    @Override
    public void register(String pluginId) {
        if (pluginIds != null) {
            throw new IllegalStateException("Cannot register software types after they have been resolved.");
        } else {
            registeredPluginIds.add(pluginId);
        }
    }

    @Override
    public PluginRequests getAutoAppliedPlugins(Project target) {
        // Auto-apply the software type plugins to the root project
        if (target.getParent() == null) {
            return PluginRequests.of(getRegisteredPluginIds().stream().map(pluginId ->
                    new DefaultPluginRequest(
                        DefaultPluginId.of(pluginId),
                        false,
                        PluginRequestInternal.Origin.OTHER,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                    )
                ).collect(Collectors.toList())
            );
        } else {
            return PluginRequests.EMPTY;
        }
    }

    @Override
    public List<String> getRegisteredPluginIds() {
        if (pluginIds == null) {
            pluginIds = registeredPluginIds.build();
        }
        return pluginIds;
    }

    @Override
    public PluginRequests getAutoAppliedPlugins(Settings target) {
        return PluginRequests.EMPTY;
    }
}
