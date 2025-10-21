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

import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.TypeToken;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.initialization.Settings;
import org.gradle.api.internal.plugins.software.SoftwareType;
import org.gradle.api.internal.tasks.properties.InspectionScheme;
import org.gradle.internal.Cast;
import org.gradle.internal.properties.annotations.PropertyMetadata;
import org.gradle.internal.properties.annotations.TypeMetadata;
import org.gradle.internal.properties.annotations.TypeMetadataWalker;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Set;

public class LegacyProjectTypeDiscovery {
    private final InspectionScheme inspectionScheme;

    public LegacyProjectTypeDiscovery(InspectionScheme inspectionScheme) {
        this.inspectionScheme = inspectionScheme;
    }

    public Set<LegacyProjectTypeImplementation<?>> discoverSoftwareTypeImplementations(
        Map<String, Class<? extends Plugin<Project>>> registeredTypes,
        Map<DefaultProjectFeatureDeclarations.RegisteringPluginKey, Set<Class<? extends Plugin<Project>>>> pluginClasses
    ) {
        final ImmutableSet.Builder<LegacyProjectTypeImplementation<?>> softwareTypeImplementationsBuilder = ImmutableSet.builder();
        pluginClasses.forEach((registeringPluginClass, pluginClassSet) ->
            pluginClassSet.forEach(pluginClass -> {
                TypeToken<?> pluginType = TypeToken.of(pluginClass);
                TypeMetadataWalker.typeWalker(inspectionScheme.getMetadataStore(), SoftwareType.class)
                    .walk(pluginType, new ProjectTypeImplementationRecordingVisitor(
                        pluginClass,
                        registeringPluginClass.pluginClass, registeringPluginClass.pluginId,
                        registeredTypes,
                        softwareTypeImplementationsBuilder));
            }));
        return softwareTypeImplementationsBuilder.build();
    }


    private static class ProjectTypeImplementationRecordingVisitor implements TypeMetadataWalker.StaticMetadataVisitor {
        private final Class<? extends Plugin<Project>> pluginClass;
        @Nullable private final String registeringPluginId;
        private final Class<? extends Plugin<Settings>> registeringPluginClass;
        private final Map<String, Class<? extends Plugin<Project>>> registeredTypes;
        private final ImmutableSet.Builder<LegacyProjectTypeImplementation<?>> projectTypeImplementationsBuilder;

        public ProjectTypeImplementationRecordingVisitor(
            Class<? extends Plugin<Project>> pluginClass,
            Class<? extends Plugin<Settings>> registeringPluginClass,
            @Nullable String pluginId,
            Map<String, Class<? extends Plugin<Project>>> registeredTypes,
            ImmutableSet.Builder<LegacyProjectTypeImplementation<?>> projectTypeImplementationsBuilder
        ) {
            this.pluginClass = pluginClass;
            this.registeringPluginId = pluginId;
            this.registeringPluginClass = registeringPluginClass;
            this.registeredTypes = registeredTypes;
            this.projectTypeImplementationsBuilder = projectTypeImplementationsBuilder;
        }

        @Override
        public void visitRoot(TypeMetadata typeMetadata, TypeToken<?> value) {
        }

        @Override
        public void visitNested(TypeMetadata typeMetadata, String qualifiedName, PropertyMetadata propertyMetadata, TypeToken<?> value) {
            propertyMetadata.getAnnotation(SoftwareType.class).ifPresent(softwareType -> {
                Class<? extends Plugin<Project>> existingPluginClass = registeredTypes.put(softwareType.name(), pluginClass);
                if (existingPluginClass != null && existingPluginClass != pluginClass) {
                    throw new IllegalArgumentException("Project type '" + softwareType.name() + "' is registered by both '" + pluginClass.getName() + "' and '" + existingPluginClass.getName() + "'");
                }

                projectTypeImplementationsBuilder.add(
                    new DefaultLegacyProjectTypeImplementation<>(
                        softwareType.name(),
                        publicTypeOf(propertyMetadata, softwareType),
                        Cast.uncheckedNonnullCast(pluginClass),
                        registeringPluginClass,
                        registeringPluginId
                    )
                );
            });
        }

        private static Class<?> publicTypeOf(PropertyMetadata propertyMetadata, SoftwareType softwareType) {
            return softwareType.modelPublicType() == Void.class ? propertyMetadata.getDeclaredType().getRawType() : softwareType.modelPublicType();
        }
    }

}
