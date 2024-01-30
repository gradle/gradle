/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.plugin.use.resolve.internal;

import org.gradle.api.internal.plugins.PluginImplementation;
import org.gradle.api.internal.plugins.PluginManagerInternal;
import org.gradle.plugin.use.PluginId;

import javax.annotation.Nullable;

public class ClassPathPluginResolution implements PluginResolution {

    private final PluginId pluginId;
    private final String pluginVersion;
    private final PluginImplementation<?> plugin;

    public ClassPathPluginResolution(PluginId pluginId, @Nullable String pluginVersion, PluginImplementation<?> plugin) {
        this.pluginId = pluginId;
        this.pluginVersion = pluginVersion;
        this.plugin = plugin;
    }

    @Override
    public PluginId getPluginId() {
        return pluginId;
    }

    @Nullable
    @Override
    public String getPluginVersion() {
        return pluginVersion;
    }

    @Override
    public void applyTo(PluginManagerInternal pluginManager) {
        pluginManager.apply(plugin);
    }
}
