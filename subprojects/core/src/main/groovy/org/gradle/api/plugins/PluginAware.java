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
 * An interface implemented by something that can be extended by plugins.
 * <p>
 * Plugins can be applied by one of the {@link #apply} methods.
 * The {@link #getAppliedPlugins()} method can be used for detecting whether plugins have been applied.
 * <p>
 * For more on writing and applying plugins, see {@link org.gradle.api.Plugin}.
 */
@HasInternalProtocol
public interface PluginAware {

    /**
     * The container of plugins.
     * <p>
     * While not deprecated, it is preferred to use the methods of this interface than use the plugin container directly.
     * <p>
     * Use {@link #apply(java.util.Map)} to apply plugins instead of applying via the plugin container.
     * <p>
     * Use {@link AppliedPlugins#hasPlugin(String) getAppliedPlugins().hasPlugin(String)} or similar to query for the application of plugins instead of doing so via the plugin container.
     *
     * @return the plugin container
     * @see #apply
     * @see AppliedPlugins#hasPlugin(String)
     */
    PluginContainer getPlugins();

    /**
     * Applies one or more plugins.
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
     * Applies one or more plugins.
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
     * Applies one or more plugins.
     * <p>
     * The given map is applied as a series of method calls to a newly created {@link ObjectConfigurationAction}.
     * That is, each key in the map is expected to be the name of a method {@link ObjectConfigurationAction} and the value to be compatible arguments to that method.
     *
     * @param options the options to use to configure and {@link ObjectConfigurationAction} before “executing” it
     */
    void apply(Map<String, ?> options);

    /**
     * Allows determining whether a plugin has been applied.
     *
     * @return the applied plugins
     */
    AppliedPlugins getAppliedPlugins();

}
