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

import org.gradle.api.Nullable;

/**
 * Represents a plugin that has been applied to an object.
 *
 * @see AppliedPlugins
 */
public interface AppliedPlugin {

    // Internal note: when we have a canonical way of finding the ID for a plugin given just the class, we can de-@Nullable the ID methods because we will always know it

    /**
     * The ID of the plugin, if it was applied with an ID (opposed to being applied directly via type).
     * <p>
     * An example of a plugin ID would be {@code "org.gradle.java"}.
     * This method always returns the fully qualified ID, regardless of whether the fully qualified ID was used to apply the plugin or not.
     * <p>
     * This value is guaranteed to be unique, for a given {@link AppliedPlugins}.
     *
     * @return the ID of the plugin if known
     */
    @Nullable
    String getId();

    /**
     * The namespace of the plugin, if it was applied with an ID (opposed to being applied directly via type).
     * <p>
     * An example of a plugin namespace would be {@code "org.gradle"} for the plugin with ID {@code "org.gradle.java"}.
     * This method always returns the namespace, regardless of whether the fully qualified ID was used to apply the plugin or not.

     * @return the namespace of the plugin if known
     */
    @Nullable
    String getNamespace();

    /**
     * The name of the plugin, if it was applied with an ID (opposed to being applied directly via type).
     * <p>
     * An example of a plugin name would be {@code "java"} for the plugin with ID {@code "org.gradle.java"}.
     * This method always returns the name, regardless of whether the fully qualified ID was used to apply the plugin or not.

     * @return the name of the plugin if known
     */
    @Nullable
    String getName();

    /**
     * The class that implements this plugin, if it was implemented as a class.
     * <p>
     * This method will currently never return {@code null}, but may in future versions of Gradle when script plugins are also represented by this type.
     *
     * @return the class that implements this plugin
     */
    @Nullable
    Class<?> getImplementationClass();

}
