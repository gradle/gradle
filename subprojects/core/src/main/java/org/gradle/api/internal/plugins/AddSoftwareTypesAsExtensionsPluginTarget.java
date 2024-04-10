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

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.NonNullApi;
import org.gradle.api.Plugin;
import org.gradle.api.internal.plugins.software.SoftwareType;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.properties.InspectionScheme;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.configuration.ConfigurationTargetIdentifier;
import org.gradle.internal.Cast;
import org.gradle.internal.exceptions.DefaultMultiCauseException;
import org.gradle.internal.properties.PropertyValue;
import org.gradle.internal.properties.PropertyVisitor;
import org.gradle.internal.reflect.DefaultTypeValidationContext;
import org.gradle.internal.reflect.validation.TypeValidationProblemRenderer;
import org.gradle.model.internal.type.ModelType;
import org.gradle.plugin.software.internal.SoftwareTypeRegistry;
import org.gradle.plugin.use.PluginId;
import org.gradle.plugin.use.internal.DefaultPluginId;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.function.Supplier;

import static com.google.common.collect.ImmutableList.toImmutableList;

/**
 * A {@link PluginTarget} that inspects the plugin for {@link SoftwareType} properties and adds them as extensions on the target prior to
 * applying the plugin via the delegate.
 */
@NonNullApi
public class AddSoftwareTypesAsExtensionsPluginTarget implements PluginTarget {
    private final ExtensionAddingVisitor extensionAddingVisitor;
    private final PluginTarget delegate;
    private final InspectionScheme inspectionScheme;

    private final SoftwareTypeRegistry softwareTypeRegistry;

    public AddSoftwareTypesAsExtensionsPluginTarget(ProjectInternal target, PluginTarget delegate, InspectionScheme inspectionScheme, SoftwareTypeRegistry softwareTypeRegistry) {
        this.extensionAddingVisitor = new ExtensionAddingVisitor(target);
        this.delegate = delegate;
        this.inspectionScheme = inspectionScheme;
        this.softwareTypeRegistry = softwareTypeRegistry;
    }

    @Override
    public ConfigurationTargetIdentifier getConfigurationTargetIdentifier() {
        return delegate.getConfigurationTargetIdentifier();
    }

    @Override
    public void applyImperative(@Nullable String pluginId, Plugin<?> plugin) {
        if (softwareTypeRegistry.isRegistered(Cast.uncheckedCast(plugin.getClass()))) {
            DefaultTypeValidationContext typeValidationContext = DefaultTypeValidationContext.withRootType(plugin.getClass(), false);
            inspectionScheme.getPropertyWalker().visitProperties(
                plugin,
                typeValidationContext,
                extensionAddingVisitor
            );

            if (!typeValidationContext.getProblems().isEmpty()) {
                throw new DefaultMultiCauseException(
                    String.format(typeValidationContext.getProblems().size() == 1
                            ? "A problem was found with the %s plugin."
                            : "Some problems were found with the %s plugin.",
                        getPluginObjectDisplayName(plugin)),
                    typeValidationContext.getProblems().stream()
                        .map(TypeValidationProblemRenderer::renderMinimalInformationAbout)
                        .sorted()
                        .map(InvalidUserDataException::new)
                        .collect(toImmutableList())
                );
            }
        }

        delegate.applyImperative(pluginId, plugin);
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

    private static String getPluginObjectDisplayName(Object parameterObject) {
        return ModelType.of(new DslObject(parameterObject).getDeclaredType()).getDisplayName();
    }

    private static Supplier<Optional<PluginId>> getOptionalSupplier(@Nullable String pluginId) {
        return () -> Optional.ofNullable(pluginId).map(DefaultPluginId::of);
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
    public static class ExtensionAddingVisitor implements PropertyVisitor {
        private final ProjectInternal target;

        public ExtensionAddingVisitor(ProjectInternal target) {
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
