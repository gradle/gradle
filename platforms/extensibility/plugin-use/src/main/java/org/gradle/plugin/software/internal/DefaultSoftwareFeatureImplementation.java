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

import com.google.common.collect.ImmutableMap;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.initialization.Settings;
import org.gradle.api.internal.plugins.SoftwareFeatureTransform;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a resolved software type implementation.  Used by declarative DSL to understand which model types should be exposed for
 * which software types.
 */
public class DefaultSoftwareFeatureImplementation<T> implements SoftwareFeatureImplementation<T> {
    private final String featureName;
    private final Class<T> definitionPublicType;
    private final Class<? extends T> definitionImplementationType;
    private final Class<?> bindingType;
    private final Class<?> buildModelType;
    private final Class<?> buildModelImplementationType;
    private final Class<? extends Plugin<Project>> pluginClass;
    private final Class<? extends Plugin<Settings>> registeringPluginClass;
    private final List<ModelDefault<?>> defaults = new ArrayList<>();
    private final SoftwareFeatureTransform<T, ?, ?> bindingTransform;
    private final Map<Class<?>, Class<?>> allBindings;

    public DefaultSoftwareFeatureImplementation(String featureName,
                                                Class<T> definitionPublicType,
                                                Class<? extends T> definitionImplementationType,
                                                Class<?> bindingType,
                                                Class<?> buildModelType,
                                                Class<?> buildModelImplementationType,
                                                Class<? extends Plugin<Project>> pluginClass,
                                                Class<? extends Plugin<Settings>> registeringPluginClass,
                                                SoftwareFeatureTransform<T, ?, ?> bindingTransform,
                                                Map<Class<?>, Class<?>> nestedBindings) {
        this.featureName = featureName;
        this.definitionPublicType = definitionPublicType;
        this.definitionImplementationType = definitionImplementationType;
        this.bindingType = bindingType;
        this.buildModelType = buildModelType;
        this.buildModelImplementationType = buildModelImplementationType;
        this.pluginClass = pluginClass;
        this.registeringPluginClass = registeringPluginClass;
        this.bindingTransform = bindingTransform;
        this.allBindings = ImmutableMap.<Class<?>, Class<?>>builder()
            .put(definitionPublicType, buildModelType)
            .putAll(nestedBindings)
            .build();;
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
    public Class<?> getBuildModelType() {
        return buildModelType;
    }

    @Override
    public Class<?> getBuildModelImplementationType() {
        return buildModelImplementationType;
    }

    @Override
    public Class<?> getBindingType() {
        return bindingType;
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
    public SoftwareFeatureTransform<T, ?, ?> getBindingTransform() {
        return bindingTransform;
    }

    @Override
    public void addModelDefault(ModelDefault<?> modelDefault) {
        defaults.add(modelDefault);
    }

    @Override
    public <V extends ModelDefault.Visitor<?>> void visitModelDefaults(Class<? extends ModelDefault<V>> type, V visitor) {
        defaults.stream()
            .filter(type::isInstance)
            .map(type::cast)
            .forEach(modelDefault -> modelDefault.visit(visitor));
    }

    @Override
    public boolean hasBindingFor(Class<?> receiverType, Class<?> buildModelType) {
        return allBindings.entrySet().stream().anyMatch(entry ->
            entry.getKey().isAssignableFrom(receiverType) && entry.getValue().isAssignableFrom(buildModelType));
    }

    @Override
    public Map<Class<?>, Class<?>> getAllDslBindings() {
        return allBindings;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultSoftwareFeatureImplementation<?> that = (DefaultSoftwareFeatureImplementation<?>) o;
        return Objects.equals(featureName, that.featureName) && Objects.equals(definitionPublicType, that.definitionPublicType) && Objects.equals(pluginClass, that.pluginClass);
    }

    @Override
    public int hashCode() {
        return Objects.hash(featureName, definitionPublicType, pluginClass);
    }
}
