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

package org.gradle.plugin.use.resolve.internal;

import org.gradle.api.internal.plugins.PluginManagerInternal;
import org.gradle.plugin.use.PluginId;

import javax.annotation.Nullable;

/**
 * The result of attempting to resolve a plugin to a classpath.
 */
public interface PluginResolution {

    /**
     * The ID of the resolved plugin.
     */
    PluginId getPluginId();

    /**
     * Accepts a visitor and visits the resolved plugin.
     */
    default void accept(PluginResolutionVisitor visitor) { }

    /**
     * Apply the plugin to the provided plugin manager.
     */
    void applyTo(PluginManagerInternal pluginManager);

    /**
     * The resolved plugin version, if known.
     *
     * @return The resolved plugin version, or null if the plugin version is not known.
     */
    @Nullable
    default String getPluginVersion() {
        return null;
    }

    /**
     * Returns {@code true}, if the plugin is from a local build.
     */
    default boolean isLocal() {
        return false;
    }
}
