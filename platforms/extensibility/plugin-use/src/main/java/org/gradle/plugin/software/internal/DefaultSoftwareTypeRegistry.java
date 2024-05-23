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

import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.TypeToken;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.initialization.Settings;
import org.gradle.api.internal.tasks.properties.InspectionScheme;
import org.gradle.api.internal.plugins.software.SoftwareType;
import org.gradle.internal.Cast;
import org.gradle.internal.properties.annotations.PropertyMetadata;
import org.gradle.internal.properties.annotations.TypeMetadata;
import org.gradle.internal.properties.annotations.TypeMetadataWalker;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Default implementation of {@link SoftwareTypeRegistry} that registers software types.
 */
public class DefaultSoftwareTypeRegistry implements SoftwareTypeRegistry {
    private final Map<Class<? extends Plugin<Settings>>, Set<Class<? extends Plugin<Project>>>> pluginClasses = new LinkedHashMap<>();
    private final Map<String, Class<? extends Plugin<Project>>> registeredTypes = new HashMap<>();
    private Set<SoftwareTypeImplementation<?>> softwareTypeImplementations;

    private final InspectionScheme inspectionScheme;

    public DefaultSoftwareTypeRegistry(InspectionScheme inspectionScheme) {
        this.inspectionScheme = inspectionScheme;
    }

    @Override
    public void register(Class<? extends Plugin<Project>> pluginClass, Class<? extends Plugin<Settings>> registeringPluginClass) {
        if (softwareTypeImplementations != null) {
            throw new IllegalStateException("Cannot register a plugin after software types have been discovered");
        }
        pluginClasses.computeIfAbsent(registeringPluginClass, k -> new LinkedHashSet<>(1)).add(pluginClass);
    }

    private Set<SoftwareTypeImplementation<?>> discoverSoftwareTypeImplementations() {
        final ImmutableSet.Builder<SoftwareTypeImplementation<?>> softwareTypeImplementationsBuilder = ImmutableSet.builder();
        pluginClasses.keySet().forEach(registeringPluginClass -> {
            pluginClasses.get(registeringPluginClass).forEach(pluginClass -> {
                TypeToken<?> pluginType = TypeToken.of(pluginClass);
                TypeMetadataWalker.typeWalker(inspectionScheme.getMetadataStore(), SoftwareType.class)
                    .walk(pluginType, new SoftwareTypeImplementationRecordingVisitor(pluginClass, registeringPluginClass, registeredTypes, softwareTypeImplementationsBuilder));
            });
        });
        return softwareTypeImplementationsBuilder.build();
    }

    @Override
    public Set<SoftwareTypeImplementation<?>> getSoftwareTypeImplementations() {
        if (softwareTypeImplementations == null) {
            softwareTypeImplementations = discoverSoftwareTypeImplementations();
        }
        return softwareTypeImplementations;
    }

    @Override
    public boolean isRegistered(Class<? extends Plugin<Project>> pluginClass) {
        return pluginClasses.values().stream().anyMatch(registeredPlugins -> registeredPlugins.stream().anyMatch(registeredPlugin -> registeredPlugin.isAssignableFrom(pluginClass)));
    }

    private static class SoftwareTypeImplementationRecordingVisitor implements TypeMetadataWalker.StaticMetadataVisitor {
        private final Class<? extends Plugin<Project>> pluginClass;
        private final Class<? extends Plugin<Settings>> registeringPluginClass;
        private final Map<String, Class<? extends Plugin<Project>>> registeredTypes;
        private final ImmutableSet.Builder<SoftwareTypeImplementation<?>> softwareTypeImplementationsBuilder;

        public SoftwareTypeImplementationRecordingVisitor(Class<? extends Plugin<Project>> pluginClass,
                                                          Class<? extends Plugin<Settings>> registeringPluginClass,
                                                          Map<String, Class<? extends Plugin<Project>>> registeredTypes, ImmutableSet.Builder<SoftwareTypeImplementation<?>> softwareTypeImplementationsBuilder) {
            this.pluginClass = pluginClass;
            this.registeringPluginClass = registeringPluginClass;
            this.registeredTypes = registeredTypes;
            this.softwareTypeImplementationsBuilder = softwareTypeImplementationsBuilder;
        }

        @Override
        public void visitRoot(TypeMetadata typeMetadata, TypeToken<?> value) {
        }

        @Override
        public void visitNested(TypeMetadata typeMetadata, String qualifiedName, PropertyMetadata propertyMetadata, TypeToken<?> value) {
            propertyMetadata.getAnnotation(SoftwareType.class).ifPresent(softwareType -> {
                Class<? extends Plugin<Project>> existingPluginClass = registeredTypes.put(softwareType.name(), pluginClass);
                if (existingPluginClass != null && existingPluginClass != pluginClass) {
                    throw new IllegalArgumentException("Software type '" + softwareType.name() + "' is registered by both '" + pluginClass.getName() + "' and '" + existingPluginClass.getName() + "'");
                }

                softwareTypeImplementationsBuilder.add(
                    new DefaultSoftwareTypeImplementation<>(
                        softwareType.name(),
                        publicTypeOf(propertyMetadata, softwareType),
                        Cast.uncheckedNonnullCast(pluginClass),
                        registeringPluginClass
                    )
                );
            });
        }

        private static Class<?> publicTypeOf(PropertyMetadata propertyMetadata, SoftwareType softwareType) {
            return softwareType.modelPublicType() == Void.class ? propertyMetadata.getDeclaredType().getRawType() : softwareType.modelPublicType();
        }
    }
}
