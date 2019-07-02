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

import org.gradle.api.Plugin;

/**
 * <p>An {@code ObjectConfigurationAction} allows you to apply {@link org.gradle.api.Plugin}s and scripts to an object
 * or objects.</p>
 */
public interface ObjectConfigurationAction {
    /**
     * <p>Specifies some target objects to be configured. Any collections or arrays in the given parameters will be
     * flattened, and the script applied to each object in the result, in the order given. Each call to this method adds
     * some additional target objects.</p>
     *
     * @param targets The target objects.
     * @return this
     */
    ObjectConfigurationAction to(Object... targets);

    /**
     * Adds a script to use to configure the target objects. You can call this method multiple times, to use multiple
     * scripts. Scripts and plugins are applied in the order that they are added.
     *
     * @param script The script. Evaluated as per {@link org.gradle.api.Project#file(Object)}. However, note that
     * a URL can also be used, allowing the script to be fetched using HTTP, for example.
     * @return this
     */
    ObjectConfigurationAction from(Object script);

    /**
     * Adds a {@link org.gradle.api.Plugin} to use to configure the target objects. You can call this method multiple
     * times, to use multiple plugins. Scripts and plugins are applied in the order that they are added.
     *
     * @param pluginClass The plugin to apply.
     * @return this
     */
    ObjectConfigurationAction plugin(Class<? extends Plugin> pluginClass);

    /**
     * Adds the plugin implemented by the given class to the target.
     * <p>
     * The class is expected to either implement {@link Plugin}, or extend {@link org.gradle.model.RuleSource}.
     * An exception will be thrown if the class is not a valid plugin implementation.
     *
     * @param pluginClass the plugin to apply
     * @return this
     */
    ObjectConfigurationAction type(Class<?> pluginClass);

    /**
     * Adds a {@link org.gradle.api.Plugin} to use to configure the target objects. You can call this method multiple
     * times, to use multiple plugins. Scripts and plugins are applied in the order that they are added.
     *
     * @param pluginId The id of the plugin to apply.
     * @return this
     */
    ObjectConfigurationAction plugin(String pluginId);
}
