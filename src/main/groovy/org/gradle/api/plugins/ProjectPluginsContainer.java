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
package org.gradle.api.plugins;

import org.gradle.api.Project;
import org.gradle.api.Plugin;
import org.gradle.api.internal.DomainObjectContainer;

/**
 * This class is used by a project to use plugins against the project and manage the plugins that have been used.
 *
 * Plugins can be specified by id or type. The id of a plugin is specified in the plugin.properties file in GRADLE_HOME.
 * Only the plugin specified there have an id.
 *
 * The name of a plugin is either its id. In the case a plugin does not has an id, its name is the fully qualified
 * class name.
 *
 * @author Hans Dockter
 */
public interface ProjectPluginsContainer extends PluginContainer {
    /**
     * Has the same behavior as {@link #usePlugin(Class, org.gradle.api.Project)} except that the the plugin
     * is specified via its id. Not all plugins have an id.
     *
     * @param id The id of the plugin to be used
     * @param project The project against the plugin should be used
     * @return The plugin which has been used against the project.
     */
    Plugin usePlugin(String id, Project project);

    /**
     * Uses a plugin against a particular project. This usually means that the plugin uses the project API to add
     * and modify the state of the project. This method can be called an arbitrary number of time for a particular
     * plugin type. The plugin will be actually used only the first time this method is called.  
     *
     * @param type The type of the plugin to be used
     * @param project The project against the plugin should be used
     * @return The plugin which has been used against the project.
     */
    <T extends Plugin> T usePlugin(Class<T> type, Project project);

    /**
     * Returns a plugin with the specified id if this plugin has been used in the project.
     * 
     * @param id The id of the plugin
     */
    Plugin getPlugin(String id);

    /**
     * Returns a plugin with the specified type if this plugin has been used in the project.
     *
     * @param type The type of the plugin
     */
    Plugin getPlugin(Class<? extends Plugin> type);
}
