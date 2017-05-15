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
import org.gradle.internal.operations.BuildOperationType;
import org.gradle.internal.scan.UsedByScanPlugin;
import org.gradle.plugin.use.PluginId;

/**
 * Details about a plugin being applied.
 *
 * @since 4.0
 */
public final class ApplyPluginBuildOperationType implements BuildOperationType<ApplyPluginBuildOperationType.Details, Void> {

    @UsedByScanPlugin
    public interface Details {

        @Nullable
        String getPluginId();

        String getClassName();

    }

    static class DetailsImpl implements Details {
        private PluginId pluginId;
        private String className;

        DetailsImpl(@Nullable PluginId pluginId, String className) {
            this.pluginId = pluginId;
            this.className = className;
        }

        @Nullable
        public String getPluginId() {
            return pluginId.getId();
        }

        public String getClassName() {
            return className;
        }
    }

    private ApplyPluginBuildOperationType() {
    }
}
