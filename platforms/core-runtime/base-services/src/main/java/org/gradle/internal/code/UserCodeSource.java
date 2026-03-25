/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.code;

import org.gradle.internal.DisplayName;
import org.jspecify.annotations.Nullable;

import java.net.URI;

/**
 * Describes the source of code being applied.
 */
public interface UserCodeSource {

    /**
     * Returns the display name of the user code.
     */
    DisplayName getDisplayName();

    /**
     * User code that is applied as a binary plugin.
     */
    class Binary implements UserCodeSource {

        private final DisplayName displayName;
        private final String className;
        private final @Nullable String pluginId;

        public Binary(DisplayName displayName, String className, @Nullable String pluginId) {
            this.displayName = displayName;
            this.className = className;
            this.pluginId = pluginId;
        }

        @Override
        public DisplayName getDisplayName() {
            return displayName;
        }

        /**
         * The ID of the binary plugin applying user code, if available.
         */
        public @Nullable String getPluginId() {
            return pluginId;
        }

        /**
         * Get the fully qualified name of the class applying user code.
         */
        public String getClassName() {
            return className;
        }

    }

    /**
     * User code that is applied as a script plugin.
     */
    class Script implements UserCodeSource {

        private final DisplayName displayName;
        private final @Nullable URI uri;

        public Script(DisplayName displayName, @Nullable URI uri) {
            this.displayName = displayName;
            this.uri = uri;
        }

        @Override
        public DisplayName getDisplayName() {
            return displayName;
        }

        /**
         * Get the URI of the script plugin applying user code, if available.
         */
        public @Nullable URI getUri() {
            return uri;
        }

    }

}
