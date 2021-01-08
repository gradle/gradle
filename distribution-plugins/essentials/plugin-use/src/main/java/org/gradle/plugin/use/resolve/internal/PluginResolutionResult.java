/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.plugin.use.resolve.internal;

/**
 * A write only object that {@link PluginResolver} implementations receive and work with to communicate their results.
 * <p>
 * It has a slightly peculiar interface as it is designed to “collect up” what happens as each resolver tries to resolve the plugin.
 * <p>
 * A “source” is some logical source of plugins (e.g. core plugin set, plugin resolution service instance)
 */
public interface PluginResolutionResult {

    /**
     * Record that the plugin was not found in some source of plugins.
     *
     * @param sourceDescription a description of the source of plugins, where the plugin requested could not be found
     * @param notFoundMessage message on why the plugin couldn't be found (e.g. it might be available by a different version)
     */
    void notFound(String sourceDescription, String notFoundMessage);

    /**
     * Record that the plugin was not found in some source of plugins.
     *
     * @param sourceDescription a description of the source of plugins, where the plugin requested could not be found
     * @param notFoundMessage message on why the plugin couldn't be found (e.g. it might be available by a different version)
     * @param notFoundDetail detail on how the plugin couldn't be found (e.g. searched locations)
     */
    void notFound(String sourceDescription, String notFoundMessage, String notFoundDetail);

    /**
     * Record that the plugin was found in some source of plugins.
     * <p>
     * If a plugin is found, no further resolvers will be queried.
     *
     * @param sourceDescription a description of the source of plugins, where the plugin requested could be found
     * @param pluginResolution the plugin resolution
     */
    void found(String sourceDescription, PluginResolution pluginResolution);

    /**
     * Whether the plugin has been found (i.e. has {@link #found(String, PluginResolution)} has been called)
     *
     * @return whether the plugin has been found
     */
    boolean isFound();

}
