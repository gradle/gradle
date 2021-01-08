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
import org.gradle.api.Action;
import org.gradle.internal.HasInternalProtocol;

import java.util.Map;

/**
 * Something that can have plugins applied to it.
 * <p>
 * The {@link #getPluginManager() plugin manager} can be used for applying and detecting whether plugins have been applied.
 * <p>
 * For more on writing and applying plugins, see {@link org.gradle.api.Plugin}.
 */
@HasInternalProtocol
public interface PluginAware {

    /**
     * The container of plugins that have been applied to this object.
     * <p>
     * While not deprecated, it is preferred to use the methods of this interface or the {@link #getPluginManager() plugin manager} than use the plugin container.
     * <p>
     * Use one of the 'apply' methods on this interface or on the {@link #getPluginManager() plugin manager} to apply plugins instead of applying via the plugin container.
     * <p>
     * Use {@link PluginManager#hasPlugin(String)} or similar to query for the application of plugins instead of doing so via the plugin container.
     *
     * @return the plugin container
     * @see #apply
     * @see PluginManager#hasPlugin(String)
     */
    PluginContainer getPlugins();

    /**
     * Applies zero or more plugins or scripts.
     * <p>
     * The given closure is used to configure an {@link ObjectConfigurationAction}, which “builds” the plugin application.
     * <p>
     * This method differs from {@link #apply(java.util.Map)} in that it allows methods of the configuration action to be invoked more than once.
     *
     * @param closure the closure to configure an {@link ObjectConfigurationAction} with before “executing” it
     * @see #apply(java.util.Map)
     */
    void apply(Closure closure);

    /**
     * Applies zero or more plugins or scripts.
     * <p>
     * The given closure is used to configure an {@link ObjectConfigurationAction}, which “builds” the plugin application.
     * <p>
     * This method differs from {@link #apply(java.util.Map)} in that it allows methods of the configuration action to be invoked more than once.
     *
     * @param action the action to configure an {@link ObjectConfigurationAction} with before “executing” it
     * @see #apply(java.util.Map)
     */
    void apply(Action<? super ObjectConfigurationAction> action);

    /**
     * Applies a plugin or script, using the given options provided as a map. Does nothing if the plugin has already been applied.
     * <p>
     * The given map is applied as a series of method calls to a newly created {@link ObjectConfigurationAction}.
     * That is, each key in the map is expected to be the name of a method {@link ObjectConfigurationAction} and the value to be compatible arguments to that method.
     *
     * <p>The following options are available:</p>
     *
     * <ul><li>{@code from}: A script to apply. Accepts any path supported by {@link org.gradle.api.Project#uri(Object)}.</li>
     *
     * <li>{@code plugin}: The id or implementation class of the plugin to apply.</li>
     *
     * <li>{@code to}: The target delegate object or objects. The default is this plugin aware object. Use this to configure objects other than this object.</li></ul>
     *
     * @param options the options to use to configure and {@link ObjectConfigurationAction} before “executing” it
     */
    void apply(Map<String, ?> options);

    /**
     * The plugin manager for this plugin aware object.
     *
     * @return the plugin manager
     * @since 2.3
     */
    PluginManager getPluginManager();

}
