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

import org.gradle.api.NonNullApi;
import org.gradle.api.Plugin;
import org.gradle.api.internal.plugins.software.SoftwareType;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.properties.InspectionScheme;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.configuration.ConfigurationTargetIdentifier;
import org.gradle.internal.Cast;
import org.gradle.internal.properties.PropertyValue;
import org.gradle.internal.properties.PropertyVisitor;
import org.gradle.internal.reflect.validation.TypeValidationContext;

import javax.annotation.Nullable;

@NonNullApi
public class AddSoftwareTypesAsExtensionsPluginTarget implements PluginTarget {
    private final AddSoftwareTypesAsExtensions addSoftwareTypesAsExtensions;
    private final PluginTarget delegate;
    private final InspectionScheme inspectionScheme;

    public AddSoftwareTypesAsExtensionsPluginTarget(ProjectInternal target, PluginTarget delegate, InspectionScheme inspectionScheme) {
        this.addSoftwareTypesAsExtensions = new AddSoftwareTypesAsExtensions(target);
        this.delegate = delegate;
        this.inspectionScheme = inspectionScheme;
    }

    @Override
    public ConfigurationTargetIdentifier getConfigurationTargetIdentifier() {
        return delegate.getConfigurationTargetIdentifier();
    }

    @Override
    public void applyImperative(@Nullable String pluginId, Plugin<?> plugin) {
        inspectionScheme.getPropertyWalker().visitProperties(
            plugin,
            TypeValidationContext.NOOP,
            addSoftwareTypesAsExtensions
        );
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
    public static class AddSoftwareTypesAsExtensions implements PropertyVisitor {
        private final ProjectInternal target;

        public AddSoftwareTypesAsExtensions(ProjectInternal target) {
            this.target = target;
        }

        @Override
        public void visitSoftwareTypeProperty(String propertyName, PropertyValue value, SoftwareType softwareType) {
            ExtensionContainer extensions = ((ExtensionAware) target).getExtensions();
            Class<?> returnType = softwareType.modelPublicType();
            extensions.add(returnType, softwareType.name(), Cast.uncheckedNonnullCast(value.call()));
        }
    }
}
