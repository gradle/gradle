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

package org.gradle.api.plugins;

import groovy.lang.Closure;

import java.util.Map;

/**
 * Objects a {@link org.gradle.api.Plugin} can be applied to.
 *
 * <p>
 * For more on writing and applying plugins, see {@link org.gradle.api.Plugin}.
 * </p>
 */
public interface PluginAware {
    /**
     * Returns the plugins container for this object. The returned container can be used to manage the plugins which
     * are used by this object.
     *
     * @return the plugin container. Never returns null.
     */
    PluginContainer getPlugins();

    /**
     * <p>Configures this object using plugins or scripts. The given closure is used to configure an {@link
     * ObjectConfigurationAction} which is then used to configure this object.</p>
     *
     * @param closure The closure to configure the {@link ObjectConfigurationAction}.
     */
    void apply(Closure closure);

    /**
     * <p>Configures this Object using plugins or scripts. The following options are available:</p>
     *
     * <ul><li>{@code from}: A script to apply to the object. Accepts any path supported by {@link org.gradle.api.Project#uri(Object)}.</li>
     *
     * <li>{@code plugin}: The id or implementation class of the plugin to apply to the object.</li>
     *
     * <li>{@code to}: The target delegate object or objects. Use this to configure objects other than this
     * object.</li></ul>
     *
     * <p>For more detail, see {@link ObjectConfigurationAction}.</p>
     *
     * @param options The options to use to configure the {@link ObjectConfigurationAction}.
     */
    void apply(Map<String, ?> options);
}
