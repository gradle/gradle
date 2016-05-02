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

package org.gradle.api.plugins;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Plugin;

/**
 * <p>A {@code PluginContainer} is used to manage a set of {@link org.gradle.api.Plugin} instances applied to a
 * particular project.</p>
 *
 * <p>Plugins can be specified using either an id or type. The id of a plugin is specified using a
 * META-INF/gradle-plugins/${id}.properties classpath resource.</p>
 */
public interface PluginContainer extends PluginCollection<Plugin> {
    /**
     * Has the same behavior as {@link #apply(Class)} except that the plugin is specified via its id. Not all
     * plugins have an id.
     *
     * @param id The id of the plugin to be applied.
     * @return The plugin which has been used against the project.
     */
    Plugin apply(String id);

    /**
     * Applies a plugin to the project. This usually means that the plugin uses the project API to add and modify the
     * state of the project. This method can be called an arbitrary number of times for a particular plugin type. The
     * plugin will be actually used only the first time this method is called.
     *
     * @param type The type of the plugin to be used
     * @return The plugin which has been used against the project.
     */
    <T extends Plugin> T apply(Class<T> type);

    /**
     * Returns true if the container has a plugin with the given id, false otherwise.
     *
     * @param id The id of the plugin
     */
    boolean hasPlugin(String id);

    /**
     * Returns true if the container has a plugin with the given type, false otherwise.
     *
     * @param type The type of the plugin
     */
    boolean hasPlugin(Class<? extends Plugin> type);

    /**
     * Returns the plugin for the given id.
     *
     * @param id The id of the plugin
     * @return the plugin or null if no plugin for the given id exists.
     */
    Plugin findPlugin(String id);

    /**
     * Returns the plugin for the given type.
     *
     * @param type The type of the plugin
     * @return the plugin or null if no plugin for the given type exists.
     */
    <T extends Plugin> T findPlugin(Class<T> type);

    /**
     * Returns a plugin with the specified id if this plugin has been used in the project.
     *
     * @param id The id of the plugin
     * @throws UnknownPluginException When there is no plugin with the given id.
     */
    Plugin getPlugin(String id) throws UnknownPluginException;

    /**
     * Returns a plugin with the specified type if this plugin has been used in the project.
     *
     * @param type The type of the plugin
     * @throws UnknownPluginException When there is no plugin with the given type.
     */
    <T extends Plugin> T getPlugin(Class<T> type) throws UnknownPluginException;

    /**
     * Returns a plugin with the specified id if this plugin has been used in the project. You can use the Groovy
     * {@code []} operator to call this method from a build script.
     *
     * @param id The id of the plugin
     * @throws UnknownPluginException When there is no plugin with the given id.
     */
    Plugin getAt(String id) throws UnknownPluginException;

    /**
     * Returns a plugin with the specified type if this plugin has been used in the project. You can use the Groovy
     * {@code []} operator to call this method from a build script.
     *
     * @param type The type of the plugin
     * @throws UnknownPluginException When there is no plugin with the given type.
     */
    <T extends Plugin> T getAt(Class<T> type) throws UnknownPluginException;

    /**
     * Executes or registers an action for a plugin with given id.
     * If the plugin was already applied, the action is executed.
     * If the plugin is applied sometime later the action will be executed after the plugin is applied.
     * If the plugin is never applied, the action is never executed.
     * The behavior is similar to {@link #withType(Class, org.gradle.api.Action)}.
     *
     * @param pluginId the id of the plugin
     * @param action the action
     */
    @Incubating
    void withId(String pluginId, Action<? super Plugin> action);

}
