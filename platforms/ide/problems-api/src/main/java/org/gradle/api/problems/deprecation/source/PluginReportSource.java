/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.problems.deprecation.source;

import org.gradle.api.Incubating;

/**
 * If this class is used, it means that the deprecated item comes from a certain plugin.
 *
 * @since 8.14
 */
@Incubating
public class PluginReportSource extends ReportSource {

    private final String pluginId;

    /**
     * Protected constructor to prevent direct instantiation.
     *
     * @param pluginId the plugin ID where the deprecated item comes from
     */
    PluginReportSource(String pluginId) {
        this.pluginId = pluginId;
    }

    @Override
    public String getId() {
        return "plugin";
    }

    /**
     * Returns the plugin ID where the deprecated item comes from.
     *
     * @since 8.14
     */
    public String getPluginId() {
        return pluginId;
    }

}
