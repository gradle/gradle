/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.tooling.events.problems;

import org.gradle.api.Incubating;
import org.jspecify.annotations.Nullable;

/**
 * The class of a plugin: code someone wrote. The application of that plugin to a project or build is a {@link PluginApplicationFrame}.
 *
 * @since 9.10.0
 */
@Incubating
public interface PluginClassFrame extends Frame {

    /**
     * Returns the fully qualified name of the plugin class.
     *
     * @return the class name
     * @since 9.10.0
     */
    String getClassName();

    /**
     * Returns the id of the plugin, if it has one.
     *
     * @return the plugin id, or {@code null} if the plugin was applied by class
     * @since 9.10.0
     */
    @Nullable
    String getPluginId();
}
