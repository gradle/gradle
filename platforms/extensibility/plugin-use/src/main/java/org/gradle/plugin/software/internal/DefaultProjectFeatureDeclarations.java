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
import org.gradle.api.internal.plugins.BindsProjectFeature;
import org.gradle.api.internal.plugins.BindsProjectType;
import org.gradle.api.internal.plugins.BuildModel;
import org.gradle.api.internal.plugins.Definition;
import org.gradle.api.internal.plugins.ProjectFeatureBinding;
import org.gradle.api.internal.plugins.ProjectFeatureBindingBuilderInternal;
import org.gradle.api.internal.plugins.ProjectFeatureBindingRegistration;
import org.gradle.api.internal.plugins.ProjectTypeBindingBuilderInternal;
import org.gradle.api.internal.plugins.ProjectTypeBindingRegistration;
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
 * Default implementation of {@link ProjectFeatureDeclarations} that registers project types.
 */
public class DefaultProjectFeatureDeclarations extends CompatibleProjectFeatureDeclarations {
    private final Map<RegisteringPluginKey, Set<Class<? extends Plugin<Project>>>> pluginClasses = new LinkedHashMap<>();
    private final Map<String, Class<? extends Plugin<Project>>> registeredTypes = new HashMap<>();

    @Nullable
    private Map<String, ProjectFeatureImplementation<?, ?>> projectFeatureImplementations;

    @SuppressWarnings("unused")
    private final InspectionScheme inspectionScheme;
    private final Instantiator instantiator;
    private final LegacyProjectTypeDiscovery legacyProjectTypeDiscovery;

    public DefaultProjectFeatureDeclarations(InspectionScheme inspectionScheme, Instantiator instantiator) {
        this.inspectionScheme = inspectionScheme;
        this.instantiator = instantiator;
        legacyProjectTypeDiscovery = new LegacyProjectTypeDiscovery(inspectionScheme);
    }

    @Override
    public void addDeclaration(@Nullable String pluginId, Class<? extends Plugin<Project>> pluginClass, Class<? extends Plugin<Settings>> registeringPluginClass) {
        if (projectFeatureImplementations != null) {
            throw new IllegalStateException("Cannot register a plugin after project types have been discovered");
        }
        RegisteringPluginKey pluginKey = new RegisteringPluginKey(registeringPluginClass, pluginId);
        pluginClasses.computeIfAbsent(pluginKey, k -> new LinkedHashSet<>()).add(pluginClass);
    }

    private Map<String, ProjectFeatureImplementation<?, ?>> discoverProjectFeatureImplementations() {
        final ImmutableMap.Builder<String, ProjectFeatureImplementation<?, ?>> projectFeatureImplementationsBuilder = ImmutableMap.builder();
        pluginClasses.forEach((registeringPluginClass, registeredPluginClasses) ->
            registeredPluginClasses.forEach(pluginClass -> {
                TypeMetadata pluginClassTypeMetadata = inspectionScheme.getMetadataStore().getTypeMetadata(pluginClass);
                TypeAnnotationMetadata pluginClassAnnotationMetadata = pluginClassTypeMetadata.getTypeAnnotationMetadata();
                registerTypeIfPresent(registeringPluginClass, pluginClass, pluginClassAnnotationMetadata, projectFeatureImplementationsBuilder);
                registerFeaturesIfPresent(registeringPluginClass, pluginClass, pluginClassAnnotationMetadata, projectFeatureImplementationsBuilder);
            })
        );
        legacyProjectTypeDiscovery.discoverSoftwareTypeImplementations(registeredTypes, pluginClasses).forEach(implementation -> {
            projectFeatureImplementationsBuilder.put(implementation.getFeatureName(), implementation);
        });
        return projectFeatureImplementationsBuilder.build();
    }

    private <T extends Definition<V>, V extends BuildModel> void registerFeature(
        RegisteringPluginKey registeringPlugin,
        Class<? extends Plugin<Project>> pluginClass,
        ProjectFeatureBinding<T, V> binding,
        ImmutableMap.Builder<String, ProjectFeatureImplementation<?, ?>> projectFeatureImplementationsBuilder
    ) {
        String projectFeatureName = binding.getName();

        Class<? extends Plugin<Project>> existingPluginClass = registeredTypes.put(projectFeatureName, pluginClass);
        if (existingPluginClass != null && existingPluginClass != pluginClass) {
            throw new IllegalArgumentException("Project feature '" + projectFeatureName + "' is registered by both '" + pluginClass.getName() + "' and '" + existingPluginClass.getName() + "'");
        }

        projectFeatureImplementationsBuilder.put(
            projectFeatureName,
            new DefaultBoundProjectFeatureImplementation<>(
                projectFeatureName,
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

    private void registerFeaturesIfPresent(
        RegisteringPluginKey registeringPluginClass,
        Class<? extends Plugin<Project>> pluginClass,
        TypeAnnotationMetadata pluginClassAnnotationMetadata,
        ImmutableMap.Builder<String, ProjectFeatureImplementation<?, ?>> projectFeatureImplementationsBuilder
    ) {
        Optional<BindsProjectFeature> bindsSoftwareTypeAnnotation = pluginClassAnnotationMetadata.getAnnotation(BindsProjectFeature.class);
        if (bindsSoftwareTypeAnnotation.isPresent()) {
            BindsProjectFeature bindsSoftwareType = bindsSoftwareTypeAnnotation.get();
            Class<? extends ProjectFeatureBindingRegistration> bindingRegistrationClass = bindsSoftwareType.value();
            ProjectFeatureBindingRegistration bindingRegistration = instantiator.newInstance(bindingRegistrationClass);
            ProjectFeatureBindingBuilderInternal builder = new DefaultProjectFeatureBindingBuilder();
            bindingRegistration.register(builder);
            builder.build().forEach(binding ->
                registerFeature(registeringPluginClass, pluginClass, binding, projectFeatureImplementationsBuilder)
            );
        }
    }

    private void registerTypeIfPresent(RegisteringPluginKey registeringPluginKey, Class<? extends Plugin<Project>> pluginClass, TypeAnnotationMetadata pluginClassAnnotationMetadata, ImmutableMap.Builder<String, ProjectFeatureImplementation<?, ?>> projectFeatureImplementationsBuilder) {
        Optional<BindsProjectType> bindsSoftwareTypeAnnotation = pluginClassAnnotationMetadata.getAnnotation(BindsProjectType.class);
        if (bindsSoftwareTypeAnnotation.isPresent()) {
            BindsProjectType bindsProjectType = bindsSoftwareTypeAnnotation.get();
            Class<? extends ProjectTypeBindingRegistration> bindingRegistrationClass = bindsProjectType.value();
            ProjectTypeBindingRegistration bindingRegistration = instantiator.newInstance(bindingRegistrationClass);
            ProjectTypeBindingBuilderInternal builder = new DefaultProjectTypeBindingBuilder();
            bindingRegistration.register(builder);
            builder.build().forEach(binding ->
                registerFeature(registeringPluginKey, pluginClass, binding, projectFeatureImplementationsBuilder)
            );
        }
    }

    @Override
    public Map<String, ProjectFeatureImplementation<?, ?>> getProjectFeatureImplementations() {
        if (projectFeatureImplementations == null) {
            projectFeatureImplementations = discoverProjectFeatureImplementations();
        }
        return projectFeatureImplementations;
    }

    @Override
    public NamedDomainObjectCollectionSchema getSchema() {
        return () -> Iterables.transform(
            () -> getProjectFeatureImplementations().entrySet().iterator(),
            entry -> new ProjectFeatureSchema(entry.getKey(), entry.getValue().getDefinitionPublicType())
        );
    }

    private static class ProjectFeatureSchema implements NamedDomainObjectCollectionSchema.NamedDomainObjectSchema {
        private final String name;
        private final Class<?> modelPublicType;

        public ProjectFeatureSchema(String name, Class<?> modelPublicType) {
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
abstract class CompatibleProjectFeatureDeclarations implements ProjectFeatureDeclarations, org.gradle.plugin.software.internal.SoftwareTypeRegistry {
    @Override
    public void register(@Nullable String pluginId, Class<? extends Plugin<Project>> pluginClass, Class<? extends Plugin<Settings>> registeringPluginClass) {
        addDeclaration(pluginId, pluginClass, registeringPluginClass);
    }
}
