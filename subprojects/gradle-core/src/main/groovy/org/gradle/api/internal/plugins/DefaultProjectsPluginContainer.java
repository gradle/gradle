/*
 * Copyright 2009 the original author or authors.
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
import org.gradle.api.Project;
import org.gradle.api.plugins.PluginContainer;
import org.gradle.api.plugins.UnknownPluginException;

/**
 * @author Hans Dockter
 */
public class DefaultProjectsPluginContainer extends DefaultPluginCollection<Plugin> implements PluginContainer {
    private PluginRegistry pluginRegistry;
    private final Project project;

    public DefaultProjectsPluginContainer(PluginRegistry pluginRegistry, Project project) {
        super(Plugin.class);
        this.pluginRegistry = pluginRegistry;
        this.project = project;
    }

    public Plugin usePlugin(String id) {
        return addPluginInternal(getTypeForId(id), id);
    }

    public <T extends Plugin> T usePlugin(Class<T> type) {
        return addPluginInternal(type, getNameForType(type));
    }

    public boolean hasPlugin(String name) {
        return findPlugin(name) != null;
    }

    public boolean hasPlugin(Class<? extends Plugin> type) {
        return findPlugin(type) != null;
    }

    public Plugin findPlugin(String name) {
        return findByName(name);
    }

    public Plugin findPlugin(Class<? extends Plugin> type) {
        return findByName(getNameForType(type));
    }

    private <T extends Plugin> T addPluginInternal(Class<T> type, String name) {
        if (findByName(name) == null) {
            Plugin plugin = providePlugin(type);
            addObject(name, plugin);
        }
        return (T) findByName(name);
    }

    public Plugin getPlugin(String id) {
        Plugin plugin = findByName(id);
        if (plugin == null) {
            throw new UnknownPluginException("Plugin with id " + id + " has not been used.");
        }
        return plugin;
    }

    public Plugin getPlugin(Class<? extends Plugin> type) {
        Plugin plugin = findByName(getNameForType(type));
        if (plugin == null) {
            throw new UnknownPluginException("Plugin with type " + type + " has not been used.");
        }
        return plugin;
    }

    protected String getNameForType(Class<? extends Plugin> type) {
        return pluginRegistry.getNameForType(type);
    }

    protected Class<? extends Plugin> getTypeForId(String id) {
        return pluginRegistry.getTypeForId(id);
    }

    private Plugin<Project> providePlugin(Class<? extends Plugin> type) {
        Plugin<Project> plugin = pluginRegistry.loadPlugin(type);
        plugin.use(project);
        return plugin;
    }
}
