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
import org.gradle.api.DomainObjectContainer;

/**
 * <p>A {@code PluginContainer} is used to manage a set of {@link org.gradle.api.Plugin} instances.</p>
 *
 * @author Hans Dockter
 */
public interface PluginContainer extends PluginCollection<Plugin>, DomainObjectContainer<Plugin> {
    /**
     * Returns true if the container has a plugin with the given name, false otherwise.
     *
     * @param name The name of the plugin
     */
    boolean hasPlugin(String name);

    /**
     * Returns true if the container has a plugin with the given type, false otherwise.
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
}
