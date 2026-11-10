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

package org.gradle.tooling.events.problems.internal;

import org.gradle.tooling.events.problems.Locality;
import org.gradle.tooling.events.problems.PluginClassFrame;
import org.gradle.tooling.events.problems.Position;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public class DefaultPluginClassFrame extends DefaultFrame implements PluginClassFrame {

    private final String className;
    @Nullable
    private final String pluginId;

    public DefaultPluginClassFrame(String displayName, Locality locality, @Nullable Position position, String className, @Nullable String pluginId) {
        super(displayName, locality, position);
        this.className = className;
        this.pluginId = pluginId;
    }

    @Override
    public String getClassName() {
        return className;
    }

    @Nullable
    @Override
    public String getPluginId() {
        return pluginId;
    }
}
