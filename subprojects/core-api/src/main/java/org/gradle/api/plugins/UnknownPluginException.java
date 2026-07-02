/*
 * Copyright 2009 the original author or authors.
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

import org.gradle.api.Incubating;
import org.gradle.api.InvalidUserDataException;
import org.jspecify.annotations.Nullable;

/**
 * A {@code UnknownPluginException} is thrown when an unknown plugin id is provided.
 */
public class UnknownPluginException extends InvalidUserDataException {

    private final @Nullable String pluginId;

    public UnknownPluginException(String message) {
        this(message, null);
    }

    /**
     * Creates an exception with the given message, carrying the id of the plugin that could not be found.
     *
     * @param message the exception message
     * @param pluginId the id of the plugin that could not be found, or {@code null} if it is not known
     *
     * @since 9.7.0
     */
    @Incubating
    public UnknownPluginException(String message, @Nullable String pluginId) {
        super(message);
        this.pluginId = pluginId;
    }

    /**
     * Returns the id of the plugin that could not be found, if it is known.
     *
     * @return the missing plugin id, or {@code null} if it is not known
     *
     * @since 9.7.0
     */
    @Incubating
    public @Nullable String getPluginId() {
        return pluginId;
    }
}
