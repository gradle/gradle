/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.plugin.software.internal;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class DefaultSoftwareTypeImplementation implements SoftwareTypeImplementation {
    private final String softwareType;
    private final String pluginId;
    private final Class<?> modelPublicType;
    private final Class<?> modelImplementationType;
    private final Class<? extends Plugin<?>> pluginClass;

    public DefaultSoftwareTypeImplementation(String softwareType, String pluginId, Class<?> modelPublicType, Class<?> modelImplementationType, Class<? extends Plugin<Project>> pluginClass) {
        this.softwareType = softwareType;
        this.pluginId = pluginId;
        this.modelPublicType = modelPublicType;
        this.modelImplementationType = modelImplementationType;
        this.pluginClass = pluginClass;
    }

    @Override
    public String getSoftwareType() {
        return softwareType;
    }

    @Override
    public String getPluginId() {
        return pluginId;
    }

    @Override
    public Class<?> getModelPublicType() {
        return modelPublicType;
    }

    @Override
    public Class<?> getModelImplementationType() {
        return modelImplementationType;
    }

    @Override
    public Class<? extends Plugin<?>> getPluginClass() {
        return pluginClass;
    }
}
