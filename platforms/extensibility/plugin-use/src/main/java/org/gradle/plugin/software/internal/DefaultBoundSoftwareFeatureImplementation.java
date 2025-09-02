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
import org.gradle.api.initialization.Settings;
import org.gradle.api.internal.plugins.BuildModel;
import org.gradle.api.internal.plugins.HasBuildModel;
import org.gradle.api.internal.plugins.SoftwareFeatureTransform;
import org.gradle.api.internal.plugins.TargetTypeInformation;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a resolved software type implementation.  Used by declarative DSL to understand which model types should be exposed for
 * which software types.
 */
public class DefaultBoundSoftwareFeatureImplementation<T extends HasBuildModel<V>, V extends BuildModel> implements BoundSoftwareFeatureImplementation<T, V> {
    private final String featureName;
    private final Class<T> definitionPublicType;
    private final Class<? extends T> definitionImplementationType;
    private final TargetTypeInformation<?> targetDefinitionType;
    private final Class<V> buildModelType;
    private final Class<? extends V> buildModelImplementationType;
    private final Class<? extends Plugin<Project>> pluginClass;
    private final Class<? extends Plugin<Settings>> registeringPluginClass;
    private final List<ModelDefault<?>> defaults = new ArrayList<>();
    @Nullable
    private final String registeringPluginId;
    private final SoftwareFeatureTransform<T, V, ?> bindingTransform;

    public DefaultBoundSoftwareFeatureImplementation(
        String featureName,
        Class<T> definitionPublicType,
        Class<? extends T> definitionImplementationType,
        TargetTypeInformation<?> targetDefinitionType,
        Class<V> buildModelType,
        Class<? extends V> buildModelImplementationType,
        Class<? extends Plugin<Project>> pluginClass,
        Class<? extends Plugin<Settings>> registeringPluginClass,
        @Nullable String registeringPluginId,
        SoftwareFeatureTransform<T, V, ?> bindingTransform
    ) {
        this.featureName = featureName;
        this.definitionPublicType = definitionPublicType;
        this.definitionImplementationType = definitionImplementationType;
        this.targetDefinitionType = targetDefinitionType;
        this.buildModelType = buildModelType;
        this.buildModelImplementationType = buildModelImplementationType;
        this.pluginClass = pluginClass;
        this.registeringPluginClass = registeringPluginClass;
        this.registeringPluginId = registeringPluginId;
        this.bindingTransform = bindingTransform;
    }

    @Override
    public String getFeatureName() {
        return featureName;
    }

    @Override
    public Class<T> getDefinitionPublicType() {
        return definitionPublicType;
    }

    @Override
    public Class<? extends T> getDefinitionImplementationType() {
        return definitionImplementationType;
    }

    @Override
    public Class<V> getBuildModelType() {
        return buildModelType;
    }

    @Override
    public Class<? extends V> getBuildModelImplementationType() {
        return buildModelImplementationType;
    }

    @Override
    public TargetTypeInformation<?> getTargetDefinitionType() {
        return targetDefinitionType;
    }

    @Override
    public Class<? extends Plugin<Project>> getPluginClass() {
        return pluginClass;
    }

    @Override
    public Class<? extends Plugin<Settings>> getRegisteringPluginClass() {
        return registeringPluginClass;
    }

    @Nullable
    @Override
    public String getRegisteringPluginId() {
        return registeringPluginId;
    }

    @Override
    public SoftwareFeatureTransform<T, V, ?> getBindingTransform() {
        return bindingTransform;
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
        DefaultBoundSoftwareFeatureImplementation<?, ?> that = (DefaultBoundSoftwareFeatureImplementation<?, ?>) o;
        return Objects.equals(featureName, that.featureName) && Objects.equals(definitionPublicType, that.definitionPublicType) && Objects.equals(pluginClass, that.pluginClass);
    }

    @Override
    public int hashCode() {
        return Objects.hash(featureName, definitionPublicType, pluginClass);
    }
}
