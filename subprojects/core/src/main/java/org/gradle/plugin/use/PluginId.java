/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.plugin.use;

import org.gradle.api.Nullable;

/**
 * A description of a plugin.
 */
public interface PluginId {
    /**
     * Denotes whether this plugin id is fully qualified.
     *
     * @return true when plugin name has a dot in it.
     * @since 3.4
     */
    boolean isQualified();

    /**
     * Takes an existing plugin, and add a qualifier.
     *
     * @param qualification the qualifier to add.
     * @return a new PluginId when this is not qualified, otherwise this.
     * @since 3.4
     */
    PluginId maybeQualify(String qualification);

    /**
     * Plugin id namespace.
     *
     * @return the substring of the plugin if before the last dot. null when unqualified.
     * @since 3.4
     */
    @Nullable
    String getNamespace();

    /**
     * Checks if this plugin is inside of a namespace.
     *
     * @param namespace the namespace to check
     * @return true when the namespaces match.
     * @since 3.4
     */
    boolean inNamespace(String namespace);

    /**
     * Plugin name without any qualifier.
     *
     * @return The name of the plugin, without any qualifier.
     * @since 3.4
     */
    String getName();

    /**
     * If this is not qualified, then this, otherwise a new instance of PluginId without the qualification.
     *
     * @return unqualified PluginId
     * @since 3.4
     */
    PluginId getUnqualified();

    /**
     * The fully qualified (if applicable) plugin.
     *
     * @return Fully qualified (if applicable) plugin id as a String.
     * @since 3.4
     */
    String asString();
}
