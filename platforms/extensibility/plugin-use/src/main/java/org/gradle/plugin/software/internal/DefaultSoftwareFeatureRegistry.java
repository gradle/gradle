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
import org.gradle.api.internal.plugins.BindsSoftwareFeature;
import org.gradle.api.internal.plugins.BindsSoftwareType;
import org.gradle.api.internal.plugins.BuildModel;
import org.gradle.api.internal.plugins.HasBuildModel;
import org.gradle.api.internal.plugins.SoftwareFeatureBinding;
import org.gradle.api.internal.plugins.SoftwareFeatureBindingBuilderInternal;
import org.gradle.api.internal.plugins.SoftwareFeatureBindingRegistration;
import org.gradle.api.internal.plugins.SoftwareTypeBindingBuilderInternal;
import org.gradle.api.internal.plugins.SoftwareTypeBindingRegistration;
import org.gradle.api.internal.tasks.properties.InspectionScheme;
import org.gradle.api.reflect.TypeOf;
import org.gradle.internal.Cast;
import org.gradle.internal.properties.annotations.TypeMetadata;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.reflect.annotations.TypeAnnotationMetadata;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Default implementation of {@link SoftwareFeatureRegistry} that registers software types.
 */
public class DefaultSoftwareFeatureRegistry extends CompatibleSoftwareFeatureRegistry {
    private final Map<RegisteringPluginKey, Set<Class<? extends Plugin<Project>>>> pluginClasses = new LinkedHashMap<>();
    private final Map<String, Class<? extends Plugin<Project>>> registeredTypes = new HashMap<>();

    @Nullable
    private Map<String, SoftwareFeatureImplementation<?, ?>> softwareFeatureImplementations;

    @SuppressWarnings("unused")
    private final InspectionScheme inspectionScheme;
    private final Instantiator instantiator;
    private final LegacySoftwareTypeDiscovery legacySoftwareTypeDiscovery;

    public DefaultSoftwareFeatureRegistry(InspectionScheme inspectionScheme, Instantiator instantiator) {
        this.inspectionScheme = inspectionScheme;
        this.instantiator = instantiator;
        legacySoftwareTypeDiscovery = new LegacySoftwareTypeDiscovery(inspectionScheme);
    }

    @Override
    public void register(@Nullable String pluginId, Class<? extends Plugin<Project>> pluginClass, Class<? extends Plugin<Settings>> registeringPluginClass) {
        if (softwareFeatureImplementations != null) {
            throw new IllegalStateException("Cannot register a plugin after software types have been discovered");
        }
        RegisteringPluginKey pluginKey = new RegisteringPluginKey(registeringPluginClass, pluginId);
        pluginClasses.computeIfAbsent(pluginKey, k -> new LinkedHashSet<>()).add(pluginClass);
    }

    private Map<String, SoftwareFeatureImplementation<?, ?>> discoverSoftwareFeatureImplementations() {
        final ImmutableMap.Builder<String, SoftwareFeatureImplementation<?, ?>> softwareFeatureImplementationsBuilder = ImmutableMap.builder();
        pluginClasses.forEach((registeringPluginClass, registeredPluginClasses) ->
            registeredPluginClasses.forEach(pluginClass -> {
                TypeMetadata pluginClassTypeMetadata = inspectionScheme.getMetadataStore().getTypeMetadata(pluginClass);
                TypeAnnotationMetadata pluginClassAnnotationMetadata = pluginClassTypeMetadata.getTypeAnnotationMetadata();
                registerSoftwareTypeIfPresent(registeringPluginClass, pluginClass, pluginClassAnnotationMetadata, softwareFeatureImplementationsBuilder);
                registerSoftwareFeatureIfPresent(registeringPluginClass, pluginClass, pluginClassAnnotationMetadata, softwareFeatureImplementationsBuilder);
            })
        );
        legacySoftwareTypeDiscovery.discoverSoftwareTypeImplementations(registeredTypes, pluginClasses).forEach(implementation -> {
            softwareFeatureImplementationsBuilder.put(implementation.getFeatureName(), implementation);
        });
        return softwareFeatureImplementationsBuilder.build();
    }

    private <T extends HasBuildModel<V>, V extends BuildModel> void registerFeature(
        RegisteringPluginKey registeringPlugin,
        Class<? extends Plugin<Project>> pluginClass,
        SoftwareFeatureBinding<T, V> binding,
        ImmutableMap.Builder<String, SoftwareFeatureImplementation<?, ?>> softwareFeatureImplementationsBuilder
    ) {
        String softwareFeatureName = binding.getName();

        Class<? extends Plugin<Project>> existingPluginClass = registeredTypes.put(softwareFeatureName, pluginClass);
        if (existingPluginClass != null && existingPluginClass != pluginClass) {
            throw new IllegalArgumentException("Software type '" + softwareFeatureName + "' is registered by both '" + pluginClass.getName() + "' and '" + existingPluginClass.getName() + "'");
        }

        softwareFeatureImplementationsBuilder.put(
            softwareFeatureName,
            new DefaultBoundSoftwareFeatureImplementation<>(
                softwareFeatureName,
                binding.getDslType(),
                binding.getDslImplementationType().orElse(binding.getDslType()),
                binding.targetDefinitionType(),
                binding.getBuildModelType(),
                binding.getBuildModelImplementationType().orElse(binding.getBuildModelType()),
                pluginClass,
                registeringPlugin.pluginClass,
                registeringPlugin.pluginId,
                Cast.uncheckedCast(binding.getTransform())
            )
        );
    }

    private void registerSoftwareFeatureIfPresent(
        RegisteringPluginKey registeringPluginClass,
        Class<? extends Plugin<Project>> pluginClass,
        TypeAnnotationMetadata pluginClassAnnotationMetadata,
        ImmutableMap.Builder<String, SoftwareFeatureImplementation<?, ?>> softwareFeatureImplementationsBuilder
    ) {
        Optional<BindsSoftwareFeature> bindsSoftwareTypeAnnotation = pluginClassAnnotationMetadata.getAnnotation(BindsSoftwareFeature.class);
        if (bindsSoftwareTypeAnnotation.isPresent()) {
            BindsSoftwareFeature bindsSoftwareType = bindsSoftwareTypeAnnotation.get();
            Class<? extends SoftwareFeatureBindingRegistration> bindingRegistrationClass = bindsSoftwareType.value();
            SoftwareFeatureBindingRegistration bindingRegistration = instantiator.newInstance(bindingRegistrationClass);
            SoftwareFeatureBindingBuilderInternal builder = new DefaultSoftwareFeatureBindingBuilder();
            bindingRegistration.register(builder);
            builder.build().forEach(binding ->
                registerFeature(registeringPluginClass, pluginClass, binding, softwareFeatureImplementationsBuilder)
            );
        }
    }

    private void registerSoftwareTypeIfPresent(RegisteringPluginKey registeringPluginKey, Class<? extends Plugin<Project>> pluginClass, TypeAnnotationMetadata pluginClassAnnotationMetadata, ImmutableMap.Builder<String, SoftwareFeatureImplementation<?, ?>> softwareFeatureImplementationsBuilder) {
        Optional<BindsSoftwareType> bindsSoftwareTypeAnnotation = pluginClassAnnotationMetadata.getAnnotation(BindsSoftwareType.class);
        if (bindsSoftwareTypeAnnotation.isPresent()) {
            BindsSoftwareType bindsSoftwareType = bindsSoftwareTypeAnnotation.get();
            Class<? extends SoftwareTypeBindingRegistration> bindingRegistrationClass = bindsSoftwareType.value();
            SoftwareTypeBindingRegistration bindingRegistration = instantiator.newInstance(bindingRegistrationClass);
            SoftwareTypeBindingBuilderInternal builder = new DefaultSoftwareTypeBindingBuilder();
            bindingRegistration.register(builder);
            builder.build().forEach(binding ->
                registerFeature(registeringPluginKey, pluginClass, binding, softwareFeatureImplementationsBuilder)
            );
        }
    }

    @Override
    public Map<String, SoftwareFeatureImplementation<?, ?>> getSoftwareFeatureImplementations() {
        if (softwareFeatureImplementations == null) {
            softwareFeatureImplementations = discoverSoftwareFeatureImplementations();
        }
        return softwareFeatureImplementations;
    }

    @Override
    public NamedDomainObjectCollectionSchema getSchema() {
        return () -> Iterables.transform(
            () -> getSoftwareFeatureImplementations().entrySet().iterator(),
            entry -> new SoftwareFeatureSchema(entry.getKey(), entry.getValue().getDefinitionPublicType())
        );
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

    public static class RegisteringPluginKey {
        public final Class<? extends Plugin<Settings>> pluginClass;
        public final @Nullable String pluginId;

        public RegisteringPluginKey(Class<? extends Plugin<Settings>> pluginClass, @Nullable String pluginId) {
            this.pluginClass = pluginClass;
            this.pluginId = pluginId;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof RegisteringPluginKey)) {
                return false;
            }
            RegisteringPluginKey pluginKey = (RegisteringPluginKey) o;
            return Objects.equals(pluginClass, pluginKey.pluginClass) && Objects.equals(pluginId, pluginKey.pluginId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(pluginClass, pluginId);
        }
    }
}

@SuppressWarnings("deprecation")
abstract class CompatibleSoftwareFeatureRegistry implements SoftwareFeatureRegistry, org.gradle.plugin.software.internal.SoftwareTypeRegistry {
}
