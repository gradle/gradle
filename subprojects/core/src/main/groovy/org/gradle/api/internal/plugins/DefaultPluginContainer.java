/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.api.Plugin;
import org.gradle.api.plugins.PluginAware;
import org.gradle.api.plugins.PluginContainer;
import org.gradle.api.plugins.UnknownPluginException;

public class DefaultPluginContainer<T extends PluginAware> extends DefaultPluginCollection<Plugin> implements PluginContainer {
    private PluginRegistry pluginRegistry;
    private final T pluginAware;

    public DefaultPluginContainer(PluginRegistry pluginRegistry, T pluginAware) {
        super(Plugin.class);
        this.pluginRegistry = pluginRegistry;
        this.pluginAware = pluginAware;
    }

    public Plugin apply(String id) {
        return addPluginInternal(getTypeForId(id));
    }

    public <T extends Plugin> T apply(Class<T> type) {
        return addPluginInternal(type);
    }

    public boolean hasPlugin(String id) {
        return findPlugin(id) != null;
    }

    public boolean hasPlugin(Class<? extends Plugin> type) {
        return findPlugin(type) != null;
    }

    public Plugin findPlugin(String id) {
        try {
            return findPlugin(getTypeForId(id));
        } catch (UnknownPluginException e) {
            return null;
        }
    }

    public <T extends Plugin> T findPlugin(Class<T> type) {
        for (Plugin plugin : this) {
            if (plugin.getClass().equals(type)) {
                return type.cast(plugin);
            }
        }
        return null;
    }

    private <T extends Plugin> T addPluginInternal(Class<T> type) {
        if (findPlugin(type) == null) {
            Plugin plugin = providePlugin(type);
            add(plugin);
        }
        return type.cast(findPlugin(type));
    }

    public Plugin getPlugin(String id) {
        Plugin plugin = findPlugin(id);
        if (plugin == null) {
            throw new UnknownPluginException("Plugin with id " + id + " has not been used.");
        }
        return plugin;
    }

    public Plugin getAt(String id) throws UnknownPluginException {
        return getPlugin(id);
    }

    public <T extends Plugin> T getAt(Class<T> type) throws UnknownPluginException {
        return getPlugin(type);
    }

    public <T extends Plugin> T getPlugin(Class<T> type) throws UnknownPluginException {
        Plugin plugin = findPlugin(type);
        if (plugin == null) {
            throw new UnknownPluginException("Plugin with type " + type + " has not been used.");
        }
        return type.cast(plugin);
    }

    protected Class<? extends Plugin> getTypeForId(String id) {
        return pluginRegistry.getTypeForId(id);
    }

    private Plugin<T> providePlugin(Class<? extends Plugin> type) {
        Plugin<T> plugin = pluginRegistry.loadPlugin(type);
        plugin.apply(pluginAware);
        return plugin;
    }
}
