/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.tooling.events;

import javax.annotation.Nullable;

/**
 * Identifies a Gradle binary plugin.
 *
 * @since 5.1
 */
public interface BinaryPluginIdentifier extends PluginIdentifier {

    /**
     * Returns the fully-qualified class name of this plugin.
     */
    String getClassName();

    /**
     * Returns the plugin id of this plugin, if available.
     */
    @Nullable
    String getPluginId();

}
