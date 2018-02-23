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

import org.gradle.api.Incubating;
import org.gradle.internal.HasInternalProtocol;

import javax.annotation.Nullable;

/**
 * A description of a plugin.
 *
 * @since 3.5
 */
@Incubating
@HasInternalProtocol
public interface PluginId {

    /**
     * The fully qualified plugin ID.
     */
    String getId();

    /**
     * The namespace of the plugin or {@code null} if the ID contains no {@code .}.
     */
    @Nullable
    String getNamespace();

    /**
     * The plugin name without the namespace.
     */
    String getName();

    /**
     * Takes this unqualified plugin ID and adds a namespace.
     *
     * @param namespace the namespace to add.
     * @return the plugin ID qualified with the given namespace
     * @throws IllegalArgumentException if the ID already had a namespace
     */
    PluginId withNamespace(String namespace);
}
