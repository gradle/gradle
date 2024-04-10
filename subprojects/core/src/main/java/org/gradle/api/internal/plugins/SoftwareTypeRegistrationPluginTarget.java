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

package org.gradle.api.internal.plugins;

import com.google.common.reflect.TypeToken;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.NonNullApi;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.internal.plugins.software.SoftwareType;
import org.gradle.api.internal.tasks.properties.InspectionScheme;
import org.gradle.api.internal.plugins.software.RegistersSoftwareTypes;
import org.gradle.configuration.ConfigurationTargetIdentifier;
import org.gradle.internal.properties.annotations.PropertyMetadata;
import org.gradle.internal.properties.annotations.TypeMetadata;
import org.gradle.plugin.software.internal.SoftwareTypeRegistry;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * A {@link PluginTarget} that inspects the plugin for {@link RegistersSoftwareTypes} annotations and registers the
 * specified software type plugins with the {@link SoftwareTypeRegistry} prior to applying the plugin via the delegate.
 */
@NonNullApi
public class SoftwareTypeRegistrationPluginTarget implements PluginTarget {
    private final PluginTarget delegate;
    private final SoftwareTypeRegistry softwareTypeRegistry;
    private final InspectionScheme inspectionScheme;

    public SoftwareTypeRegistrationPluginTarget(PluginTarget delegate, SoftwareTypeRegistry softwareTypeRegistry, InspectionScheme inspectionScheme) {
        this.delegate = delegate;
        this.softwareTypeRegistry = softwareTypeRegistry;
        this.inspectionScheme = inspectionScheme;
    }

    @Override
    public ConfigurationTargetIdentifier getConfigurationTargetIdentifier() {
        return delegate.getConfigurationTargetIdentifier();
    }

    @Override
    public void applyImperative(@Nullable String pluginId, Plugin<?> plugin) {
        TypeToken<?> pluginType = TypeToken.of(plugin.getClass());
        TypeMetadata typeMetadata = inspectionScheme.getMetadataStore().getTypeMetadata(pluginType.getRawType());
        registerSoftwareTypes(typeMetadata);

        delegate.applyImperative(pluginId, plugin);
    }

    @Override
    public void applyRules(@Nullable String pluginId, Class<?> clazz) {
        delegate.applyRules(pluginId, clazz);
    }

    @Override
    public void applyImperativeRulesHybrid(@Nullable String pluginId, Plugin<?> plugin, Class<?> declaringClass) {
        delegate.applyImperativeRulesHybrid(pluginId, plugin, declaringClass);
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

    private void registerSoftwareTypes(TypeMetadata typeMetadata) {
        Optional<RegistersSoftwareTypes> registersSoftwareType = typeMetadata.getTypeAnnotationMetadata().getAnnotation(RegistersSoftwareTypes.class);
        registersSoftwareType.ifPresent(registration -> {
            for (Class<? extends Plugin<Project>> softwareTypeImplClass : registration.value()) {
                validateSoftwareTypePluginExposesSoftwareTypes(softwareTypeImplClass);
                softwareTypeRegistry.register(softwareTypeImplClass);
            }
        });
    }

    void validateSoftwareTypePluginExposesSoftwareTypes(Class<? extends Plugin<Project>> softwareTypePluginImplClass) {
        TypeToken<?> softwareTypePluginImplType = TypeToken.of(softwareTypePluginImplClass);
        TypeMetadata softwareTypePluginImplMetadata = inspectionScheme.getMetadataStore().getTypeMetadata(softwareTypePluginImplType.getRawType());

        for (PropertyMetadata propertyMetadata : softwareTypePluginImplMetadata.getPropertiesMetadata()) {
            Optional<SoftwareType> registersSoftwareType = propertyMetadata.getAnnotation(SoftwareType.class);
            if (registersSoftwareType.isPresent()) {
                return;
            }
        }

        throw new InvalidUserDataException("A plugin with type '" + softwareTypePluginImplClass.getName() + "' was registered as a software type plugin, but it does not expose any software types. Software type plugins must expose software types via properties with the @SoftwareType annotation.");
    }
}
