/*
 * Copyright 2024 the original author or authors.
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
import org.gradle.api.Project;
import org.gradle.api.reflect.TypeOf;

import java.util.Map;

public interface DeclarativeExtensionRegistry {
    /**
     * Registers a new extension to this container.
     *
     * The extension will not be created unless it is actually referenced.
     *
     * The extension will be exposed as {@code type} unless the extension itself declares
     * a preferred public type via the {@link org.gradle.api.reflect.HasPublicType}
     * protocol.
     *
     * @param name The name for the extension
     * @param type The extension instance type
     * @param pluginType The plugin type to apply when the extension is referenced
     */
    <T> void register(String name, Class<T> type, Class<? extends Plugin<Project>> pluginType);

    // should be internal
    void initialize(String name);

    // should be internal
    Map<String, TypeOf<?>> getNamesToPublicTypes();
}
