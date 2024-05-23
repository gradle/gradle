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

import com.google.common.collect.ImmutableList;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.initialization.Settings;
import org.gradle.internal.declarative.dsl.model.conventions.Convention;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a resolved software type implementation.  Used by declarative DSL to understand which model types should be exposed for
 * which software types.
 */
public class DefaultSoftwareTypeImplementation<T> implements SoftwareTypeImplementation<T> {
    private final String softwareType;
    private final Class<? extends T> modelPublicType;
    private final Class<? extends Plugin<Project>> pluginClass;
    private final Class<? extends Plugin<Settings>> registeringPluginClass;

    private final List<Convention<?>> conventionRules = new ArrayList<>();

    public DefaultSoftwareTypeImplementation(String softwareType,
                                             Class<? extends T> modelPublicType,
                                             Class<? extends Plugin<Project>> pluginClass,
                                             Class<? extends Plugin<Settings>> registeringPluginClass) {
        this.softwareType = softwareType;
        this.modelPublicType = modelPublicType;
        this.pluginClass = pluginClass;
        this.registeringPluginClass = registeringPluginClass;
    }

    @Override
    public String getSoftwareType() {
        return softwareType;
    }

    @Override
    public Class<? extends T> getModelPublicType() {
        return modelPublicType;
    }

    @Override
    public Class<? extends Plugin<Project>> getPluginClass() {
        return pluginClass;
    }

    @Override
    public Class<? extends Plugin<Settings>> getRegisteringPluginClass() {
        return registeringPluginClass;
    }

    @Override
    public void addConvention(Convention<?> rule) {
        conventionRules.add(rule);
    }

    @Override
    public List<Convention<?>> getConventions() {
        return ImmutableList.copyOf(conventionRules);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultSoftwareTypeImplementation<?> that = (DefaultSoftwareTypeImplementation<?>) o;
        return Objects.equals(softwareType, that.softwareType) && Objects.equals(modelPublicType, that.modelPublicType) && Objects.equals(pluginClass, that.pluginClass);
    }

    @Override
    public int hashCode() {
        return Objects.hash(softwareType, modelPublicType, pluginClass);
    }
}
