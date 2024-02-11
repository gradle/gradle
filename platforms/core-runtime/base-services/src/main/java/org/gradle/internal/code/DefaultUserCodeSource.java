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

import javax.annotation.Nullable;

/**
 * Default implementation of {@link UserCodeSource}.
 */
public class DefaultUserCodeSource implements UserCodeSource {

    private final DisplayName displayName;
    private final String pluginId;

    public DefaultUserCodeSource(DisplayName displayName, @Nullable String pluginId) {
        this.displayName = displayName;
        this.pluginId = pluginId;
    }

    @Override
    public DisplayName getDisplayName() {
        return displayName;
    }

    @Nullable
    @Override
    public String getPluginId() {
        return pluginId;
    }
}
