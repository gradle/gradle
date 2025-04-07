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
import com.google.common.collect.Iterables;
import org.gradle.api.NamedDomainObjectCollectionSchema;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.initialization.Settings;
import org.gradle.api.internal.plugins.SoftwareFeatureDslBinding;
import org.gradle.api.internal.plugins.software.SoftwareFeature;
import org.gradle.api.internal.tasks.properties.InspectionScheme;
import org.gradle.api.internal.plugins.software.SoftwareType;
import org.gradle.api.reflect.TypeOf;
import org.gradle.internal.Cast;

import javax.annotation.Nullable;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Default implementation of {@link SoftwareFeatureRegistry} that registers software types.
 */
public class DefaultSoftwareFeatureRegistry implements SoftwareFeatureRegistry {
    private final Map<Class<? extends Plugin<Settings>>, Set<Class<? extends Plugin<Project>>>> pluginClasses = new LinkedHashMap<>();
    private final Map<String, Class<? extends Plugin<Project>>> registeredTypes = new HashMap<>();

    @Nullable
    private Map<String, SoftwareFeatureImplementation<?>> softwareFeatureImplementations;

    @SuppressWarnings("unused")
    private final InspectionScheme inspectionScheme;

    public DefaultSoftwareFeatureRegistry(InspectionScheme inspectionScheme) {
        this.inspectionScheme = inspectionScheme;
    }

    @Override
    public void register(Class<? extends Plugin<Project>> pluginClass, Class<? extends Plugin<Settings>> registeringPluginClass) {
        if (softwareFeatureImplementations != null) {
            throw new IllegalStateException("Cannot register a plugin after software types have been discovered");
        }
        pluginClasses.computeIfAbsent(registeringPluginClass, k -> new LinkedHashSet<>()).add(pluginClass);
    }

    private Map<String, SoftwareFeatureImplementation<?>> discoverSoftwareTypeImplementations() {
        final ImmutableMap.Builder<String, SoftwareFeatureImplementation<?>> softwareTypeImplementationsBuilder = ImmutableMap.builder();
        pluginClasses.forEach((registeringPluginClass, registeredPluginClasses) ->
            registeredPluginClasses.forEach(pluginClass ->
                registerSoftwareTypeImplementations(pluginClass, registeringPluginClass, softwareTypeImplementationsBuilder))
        );
        return softwareTypeImplementationsBuilder.build();
    }

    @Override
    public Map<String, SoftwareFeatureImplementation<?>> getSoftwareFeatureImplementations() {
        if (softwareFeatureImplementations == null) {
            softwareFeatureImplementations = discoverSoftwareTypeImplementations();
        }
        return softwareFeatureImplementations;
    }

    @Override
    public Optional<SoftwareFeatureImplementation<?>> implementationFor(Class<? extends Plugin<Project>> pluginClass) {
        return getSoftwareFeatureImplementations().values().stream()
            .filter(softwareFeatureImplementation -> softwareFeatureImplementation.getPluginClass().isAssignableFrom(pluginClass))
            .findFirst();
    }

    @Override
    public NamedDomainObjectCollectionSchema getSchema() {
        return () -> Iterables.transform(
            () -> getSoftwareFeatureImplementations().entrySet().iterator(),
            entry -> new SoftwareFeatureSchema(entry.getKey(), entry.getValue().getModelPublicType())
        );
    }

    private void registerSoftwareTypeImplementations(Class<? extends Plugin<Project>> pluginClass, Class<? extends Plugin<Settings>> registeringPluginClass, ImmutableMap.Builder<String, SoftwareFeatureImplementation<?>> softwareFeatureImplementationBuilder) {
        Arrays.stream(pluginClass.getDeclaredFields())
            .filter(field -> field.isAnnotationPresent(SoftwareType.class) || field.isAnnotationPresent(SoftwareFeature.class))
            .forEach(field -> {

            if (field.getType().isAssignableFrom(SoftwareFeatureDslBinding.class)) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    // throw new IllegalArgumentException("Method annotated with @SoftwareType must be static: " + method);
                    return;
                }
                SoftwareFeatureDslBinding<?> softwareFeatureDslBinding;
                try {
                    softwareFeatureDslBinding = Cast.uncheckedNonnullCast(field.get(pluginClass));
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }

                String softwareTypeName = softwareFeatureDslBinding.getPath().getName();
                Class<? extends Plugin<Project>> existingPluginClass = registeredTypes.put(softwareTypeName, pluginClass);
                if (existingPluginClass != null && existingPluginClass != pluginClass) {
                    throw new IllegalArgumentException("Software type '" + softwareTypeName + "' is registered by both '" + pluginClass.getName() + "' and '" + existingPluginClass.getName() + "'");
                }

                softwareFeatureImplementationBuilder.put(
                    softwareTypeName,
                    new DefaultSoftwareFeatureImplementation<>(
                        softwareFeatureDslBinding.getPath().getName(),
                        softwareFeatureDslBinding.getDslType(),
                        softwareFeatureDslBinding.getBindingTargetType(),
                        softwareFeatureDslBinding.getBuildModelType(),
                        pluginClass,
                        registeringPluginClass,
                        Cast.uncheckedCast(softwareFeatureDslBinding.getTransform())
                    )
                );
            }
        });
    }

    private static class SoftwareFeatureSchema implements NamedDomainObjectCollectionSchema.NamedDomainObjectSchema {
        private final String name;
        private final Class<?> modelPublicType;

        public SoftwareFeatureSchema(String name, Class<?> modelPublicType) {
            this.name = name;
            this.modelPublicType = modelPublicType;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public TypeOf<?> getPublicType() {
            return TypeOf.typeOf(modelPublicType);
        }
    }
}
