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
 * The application of a plugin to a project, a settings object or a build.
 *
 * @since 9.10.0
 */
@Incubating
public interface PluginApplicationFrame extends Frame {

    /**
     * Returns the id of the plugin.
     *
     * @return the plugin id, such as {@code com.acme.foo}, or null when the plugin was applied by its class
     * @since 9.10.0
     */
    @Nullable
    String getPluginId();

    /**
     * Returns the name of the plugin's class.
     *
     * @return the fully qualified class name
     * @since 9.10.0
     */
    String getClassName();
}
