/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.plugins;

import org.gradle.api.Nullable;
import org.gradle.internal.progress.NoResultBuildOperationDetails;
import org.gradle.plugin.use.PluginId;

/**
 * Details about a plugin being applied.
 *
 * This class is intentionally internal and consumed by the build scan plugin.
 *
 * @since 4.0
 */
public final class ApplyPluginBuildOperationDetails implements NoResultBuildOperationDetails {
    private PluginId pluginId;
    private String className;

    ApplyPluginBuildOperationDetails(@Nullable PluginId pluginId, String className) {
        this.pluginId = pluginId;
        this.className = className;
    }

    @Nullable
    public PluginId getPluginId() {
        return pluginId;
    }

    public String getClassName() {
        return className;
    }

}
