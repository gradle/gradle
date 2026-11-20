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

package org.gradle.api.problems.internal;

import com.google.common.base.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Represents an applied plugin ID.
 */
public class DefaultPluginIdLocation implements PluginIdLocation {

    private final @Nullable String pluginId;

    public DefaultPluginIdLocation(@Nullable String pluginId) {
        this.pluginId = pluginId;
    }

    @Override
    // The location can be deserialized without a plugin ID, see ValidationProblemSerialization
    @SuppressWarnings("NullAway")
    public String getPluginId() {
        return pluginId;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (!(o instanceof DefaultPluginIdLocation)) {
            return false;
        }
        DefaultPluginIdLocation that = (DefaultPluginIdLocation) o;
        return Objects.equal(pluginId, that.pluginId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(pluginId);
    }
}
