/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.plugin.management.internal;

import com.google.common.collect.Maps;
import org.gradle.api.Action;
import org.gradle.api.invocation.Gradle;
import org.gradle.internal.InternalBuildAdapter;
import org.gradle.internal.MutableActionSet;
import org.gradle.internal.event.ListenerManager;
import org.gradle.plugin.management.PluginResolveDetails;
import org.gradle.plugin.use.PluginId;

import java.util.Map;

public class DefaultPluginResolutionStrategy implements PluginResolutionStrategyInternal {

    private final MutableActionSet<PluginResolveDetails> resolutionRules = new MutableActionSet<PluginResolveDetails>();
    private final Map<PluginId, String> pluginVersions = Maps.newHashMap();
    private boolean locked;

    public DefaultPluginResolutionStrategy(ListenerManager listenerManager) {
        listenerManager.addListener(new InternalBuildAdapter(){
            @Override
            public void projectsLoaded(Gradle gradle) {
                locked = true;
            }
        });
    }

    @Override
    public void eachPlugin(Action<? super PluginResolveDetails> rule) {
        if (locked) {
            throw new IllegalStateException("Cannot change the plugin resolution strategy after projects have been loaded.");
        }
        resolutionRules.add(rule);
    }

    @Override
    public PluginRequestInternal applyTo(PluginRequestInternal pluginRequest) {
        DefaultPluginResolveDetails details = new DefaultPluginResolveDetails(pluginRequest);
        if (details.getRequested().getVersion() == null) {
            String version = pluginVersions.get(details.getRequested().getId());
            if (version != null) {
                details.useVersion(version);
            }
        }
        resolutionRules.execute(details);
        return details.getTarget();
    }

    @Override
    public void setDefaultPluginVersion(PluginId id, String version) {
        String existing = pluginVersions.get(id);
        if (existing != null && !existing.equals(version)) {
            throw new IllegalArgumentException("Cannot provide multiple default versions for the same plugin.");
        }
        pluginVersions.put(id, version);
    }
}
