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


package org.gradle.api.problems.internal.location;

import org.gradle.internal.code.Locality;
import org.jspecify.annotations.Nullable;

/**
 * The application of a plugin to a target.
 */
public final class PluginApplicationFrame extends Frame {

    private final @Nullable String pluginId;
    private final String className;

    /**
     * @param pluginId the id the plugin was applied by, or null when it was applied by class
     */
    public PluginApplicationFrame(@Nullable String pluginId, String className, Locality locality) {
        super(pluginId != null ? "plugin application " + pluginId : "plugin application class " + className, locality);
        this.pluginId = pluginId;
        this.className = className;
    }

    public @Nullable String getPluginId() {
        return pluginId;
    }

    public String getClassName() {
        return className;
    }

    @Override
    public boolean isAuthored() {
        return false;
    }
}
