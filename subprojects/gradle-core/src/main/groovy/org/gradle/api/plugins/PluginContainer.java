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

import org.gradle.api.Plugin;

/**
 * <p>A {@code PluginContainer} is used to manage a set of {@link org.gradle.api.Plugin} instances applied to a
 * particular project.</p>
 *
 * <p>Plugins can be specified using either an id or type. The id of a plugin is specified using a
 * META-INF/gradle-plugins.properties resource.</p>
 *
 * <p>The name of a plugin is its id. In the case a plugin does not has an id, its name is the fully qualified class
 * name.</p>
 *
 * @author Hans Dockter
 */
public interface PluginContainer extends PluginCollection<Plugin> {
    /**
     * Has the same behavior as {@link #usePlugin(Class)} except that the the plugin is specified via its id. Not all
     * plugins have an id.
     *
     * @param id The id of the plugin to be used
     * @return The plugin which has been used against the project.
     */
    Plugin usePlugin(String id);

    /**
     * Uses a plugin against the project. This usually means that the plugin uses the project API to add and modify the
     * state of the project. This method can be called an arbitrary number of time for a particular plugin type. The
     * plugin will be actually used only the first time this method is called.
     *
     * @param type The type of the plugin to be used
     * @return The plugin which has been used against the project.
     */
    <T extends Plugin> T usePlugin(Class<T> type);

    /**
     * Returns true if the container has a plugin with the given name, false otherwise.
     *
     * @param name The name of the plugin
     */
    boolean hasPlugin(String name);

    /**
     * Returns true if the container has a plugin with the given type, false otherwise.
     *
     * @param type The type of the plugin
     */
    boolean hasPlugin(Class<? extends Plugin> type);

    /**
     * Returns the plugin for the given name.
     *
     * @param name The name of the plugin
     * @return the plugin or null if no plugin for the given name exists.
     */
    Plugin findPlugin(String name);

    /**
     * Returns the plugin for the given type.
     *
     * @param type The type of the plugin
     * @return the plugin or null if no plugin for the given type exists.
     */
    Plugin findPlugin(Class<? extends Plugin> type);

    /**
     * Returns a plugin with the specified id if this plugin has been used in the project.
     *
     * @param id The id of the plugin
     */
    Plugin getPlugin(String id) throws UnknownPluginException;

    /**
     * Returns a plugin with the specified type if this plugin has been used in the project.
     *
     * @param type The type of the plugin
     */
    Plugin getPlugin(Class<? extends Plugin> type) throws UnknownPluginException;
}
