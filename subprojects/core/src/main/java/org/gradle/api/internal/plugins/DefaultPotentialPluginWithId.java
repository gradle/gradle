/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.plugins;

import org.gradle.plugin.use.PluginId;

public class DefaultPotentialPluginWithId<T> implements PluginImplementation<T> {

    private final PluginId pluginId;
    private final PotentialPlugin<? extends T> potentialPlugin;

    public static <T> DefaultPotentialPluginWithId<T> of(PluginId pluginId, PotentialPlugin<T> potentialPlugin) {
        return new DefaultPotentialPluginWithId<T>(pluginId, potentialPlugin);
    }

    protected DefaultPotentialPluginWithId(PluginId pluginId, PotentialPlugin<? extends T> potentialPlugin) {
        this.pluginId = pluginId;
        this.potentialPlugin = potentialPlugin;
    }

    @Override
    public String getDisplayName() {
        if (pluginId == null) {
            return "plugin class '" + asClass().getName() + "'";
        }
        return "plugin '" + pluginId + "'";
    }

    public PluginId getPluginId() {
        return pluginId;
    }

    public Class<? extends T> asClass() {
        return potentialPlugin.asClass();
    }

    public boolean isImperative() {
        return potentialPlugin.isImperative();
    }

    public boolean isHasRules() {
        return potentialPlugin.isHasRules();
    }

    public Type getType() {
        return potentialPlugin.getType();
    }

    @Override
    public boolean isAlsoKnownAs(PluginId id) {
        return false;
    }
}
