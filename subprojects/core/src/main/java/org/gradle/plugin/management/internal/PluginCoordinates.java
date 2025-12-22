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

package org.gradle.plugin.management.internal;

import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.plugin.management.PluginRequest;
import org.gradle.plugin.use.PluginId;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class PluginCoordinates {

    private final PluginId id;
    private final @Nullable ComponentSelector selector;

    public PluginCoordinates(PluginId id, @Nullable ComponentSelector selector) {
        this.id = id;
        this.selector = selector;
    }

    public static PluginCoordinates from(PluginRequest pluginRequest) {
        return new PluginCoordinates(pluginRequest.getId(), pluginRequest.getSelector());
    }

    /**
     * {@link PluginRequest#getId()}
     */
    public PluginId getId() {
        return id;
    }

    /**
     * {@link PluginRequest#getSelector()}
     */
    @Nullable
    public ComponentSelector getSelector() {
        return selector;
    }

}
