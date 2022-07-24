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

package org.gradle.api.plugins;

import javax.annotation.Nullable;

/**
 * Represents a plugin that has been applied.
 * <p>
 * Currently just provides information about the ID of the plugin.
 *
 * @see org.gradle.api.plugins.PluginAware
 * @since 2.3
 */
public interface AppliedPlugin {

    /**
     * The ID of the plugin.
     * <p>
     * An example of a plugin ID would be {@code "org.gradle.java"}.
     * This method always returns the fully qualified ID, regardless of whether the fully qualified ID was used to apply the plugin or not.
     * <p>
     * This value is guaranteed to be unique, for a given {@link org.gradle.api.plugins.PluginAware}.
     *
     * @return the ID of the plugin
     */
    String getId();

    /**
     * The namespace of the plugin.
     * <p>
     * An example of a plugin namespace would be {@code "org.gradle"} for the plugin with ID {@code "org.gradle.java"}.
     * This method always returns the namespace, regardless of whether the fully qualified ID was used to apply the plugin or not.
     * <p>
     * If the plugin has an unqualified ID, this method will return {@code null}.
     *
     * @return the namespace of the plugin
     */
    @Nullable
    String getNamespace();

    /**
     * The name of the plugin.
     * <p>
     * An example of a plugin name would be {@code "java"} for the plugin with ID {@code "org.gradle.java"}.
     * This method always returns the name, regardless of whether the fully qualified ID was used to apply the plugin or not.
     * <p>
     * If the plugin has an unqualified ID, this method will return the same value as {@link #getId()}.
     *
     * @return the name of the plugin
     */
    String getName();

}
