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
import org.gradle.api.NonNullApi;
import org.gradle.api.Plugin;
import org.gradle.api.internal.tasks.properties.InspectionScheme;
import org.gradle.api.internal.plugins.software.RegistersSoftwareType;
import org.gradle.api.tasks.Nested;
import org.gradle.configuration.ConfigurationTargetIdentifier;
import org.gradle.internal.properties.annotations.PropertyMetadata;
import org.gradle.internal.properties.annotations.TypeMetadata;
import org.gradle.internal.properties.annotations.TypeMetadataWalker;
import org.gradle.plugin.software.internal.SoftwareTypeRegistry;

import javax.annotation.Nullable;
import java.util.Optional;

@NonNullApi
public class SoftwareTypeRegistrationPluginTarget implements PluginTarget {
    private final PluginTarget delegate;
    private final SoftwareTypeRegistry softwareTypeRegistry;
    private final InspectionScheme inspectionScheme;
    private final RegisterSoftwareTypeVisitor registerSoftwareTypeVisitor = new RegisterSoftwareTypeVisitor();

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
        TypeMetadataWalker.typeWalker(inspectionScheme.getMetadataStore(), Nested.class).walk(pluginType, registerSoftwareTypeVisitor);
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

    @NonNullApi
    private class RegisterSoftwareTypeVisitor implements TypeMetadataWalker.StaticMetadataVisitor {
        @Override
        public void visitRoot(TypeMetadata typeMetadata, TypeToken<?> value) {
            Optional<RegistersSoftwareType> registersSoftwareType = typeMetadata.getTypeAnnotationMetadata().getAnnotation(RegistersSoftwareType.class);
            registersSoftwareType.ifPresent(registration -> softwareTypeRegistry.register(registration.value()));
        }

        @Override
        public void visitNested(TypeMetadata typeMetadata, String qualifiedName, PropertyMetadata propertyMetadata, TypeToken<?> value) {

        }
    }
}
