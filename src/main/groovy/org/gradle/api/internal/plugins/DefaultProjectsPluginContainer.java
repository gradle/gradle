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
import org.gradle.api.plugins.ProjectPluginsContainer;

/**
 * @author Hans Dockter
 */
public class DefaultProjectsPluginContainer extends AbstractPluginContainer implements ProjectPluginsContainer {
    private PluginRegistry pluginRegistry;

    public DefaultProjectsPluginContainer(final PluginRegistry pluginRegistry) {
        super(Plugin.class);
        this.pluginRegistry = pluginRegistry;
    }

    public Plugin usePlugin(String id, Project project) {
        return addPlugin(id, createPluginProvider(project));
    }

    public <T extends Plugin> T usePlugin(Class<T> type, Project project) {
        return addPlugin(type, createPluginProvider(project));
    }

    public Plugin getPlugin(String id) {
        return findByName(id);
    }

    public Plugin getPlugin(Class<? extends Plugin> type) {
        return findByName(getNameForType(type));
    }

    protected String getNameForType(Class<? extends Plugin> type) {
        return pluginRegistry.getNameForType(type);
    }

    protected Class<? extends Plugin> getTypeForId(String id) {
        return pluginRegistry.getTypeForId(id);
    }

    PluginProvider createPluginProvider(final Project project) {
        return new PluginProvider() {
            public Plugin providePlugin(Class<? extends Plugin> type) {
                Plugin plugin = pluginRegistry.loadPlugin(type);
                plugin.use(project, DefaultProjectsPluginContainer.this);
                return plugin;
            }
        };
    }
}
