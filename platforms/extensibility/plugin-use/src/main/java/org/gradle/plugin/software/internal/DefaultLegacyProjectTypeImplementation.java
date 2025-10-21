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

package org.gradle.plugin.software.internal;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.initialization.Settings;
import org.gradle.api.internal.plugins.ProjectFeatureApplyAction;
import org.gradle.api.internal.plugins.TargetTypeInformation;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a resolved project type implementation.  Used by declarative DSL to understand which model types should be exposed for
 * which project types.
 */
public class DefaultLegacyProjectTypeImplementation<T> implements LegacyProjectTypeImplementation<T> {
    private final String softwareTypeName;
    private final Class<T> modelPublicType;
    private final Class<? extends Plugin<Project>> pluginClass;
    private final Class<? extends Plugin<Settings>> registeringPluginClass;
    private final @Nullable String registeringPluginId;
    private final List<ModelDefault<?>> defaults = new ArrayList<>();

    public DefaultLegacyProjectTypeImplementation(
        String softwareTypeName,
        Class<T> modelPublicType,
        Class<? extends Plugin<Project>> pluginClass,
        Class<? extends Plugin<Settings>> registeringPluginClass,
        @Nullable String registeringPluginId
    ) {
        this.softwareTypeName = softwareTypeName;
        this.modelPublicType = modelPublicType;
        this.pluginClass = pluginClass;
        this.registeringPluginClass = registeringPluginClass;
        this.registeringPluginId = registeringPluginId;
    }


    public String getTypeName() {
        return softwareTypeName;
    }

    @Override
    public String getFeatureName() {
        return softwareTypeName;
    }

    @Override
    public Class<T> getDefinitionPublicType() {
        return modelPublicType;
    }

    @Override
    public Class<? extends T> getDefinitionImplementationType() {
        return modelPublicType;
    }

    @Override
    public TargetTypeInformation<?> getTargetDefinitionType() {
        return new TargetTypeInformation.DefinitionTargetTypeInformation<>(Project.class);
    }

    @Override
    public Class<T> getBuildModelType() {
        return modelPublicType;
    }

    @Override
    public Class<T> getBuildModelImplementationType() {
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
    public ProjectFeatureApplyAction<T, T, ?> getBindingTransform() {
        return (context, t, v, u) -> { };
    }

    @Override
    public void addModelDefault(ModelDefault<?> modelDefault) {
        defaults.add(modelDefault);
    }

    @Override
    public <M extends ModelDefault.Visitor<?>> void visitModelDefaults(Class<? extends ModelDefault<M>> type, M visitor) {
        defaults.stream()
            .filter(type::isInstance)
            .map(type::cast)
            .forEach(modelDefault -> modelDefault.visit(visitor));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultLegacyProjectTypeImplementation<?> that = (DefaultLegacyProjectTypeImplementation<?>) o;
        return Objects.equals(softwareTypeName, that.softwareTypeName) && Objects.equals(modelPublicType, that.modelPublicType) && Objects.equals(pluginClass, that.pluginClass);
    }

    @Override
    public int hashCode() {
        return Objects.hash(softwareTypeName, modelPublicType, pluginClass);
    }

    @Nullable
    @Override
    public String getRegisteringPluginId() {
        return registeringPluginId;
    }
}
