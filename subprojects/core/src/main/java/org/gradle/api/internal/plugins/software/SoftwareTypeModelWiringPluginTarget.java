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

package org.gradle.api.internal.plugins.software;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Plugin;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.internal.plugins.PluginTarget;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.properties.InspectionScheme;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.provider.Property;
import org.gradle.configuration.ConfigurationTargetIdentifier;
import org.gradle.internal.Cast;
import org.gradle.internal.exceptions.DefaultMultiCauseException;
import org.gradle.internal.properties.PropertyValue;
import org.gradle.internal.properties.PropertyVisitor;
import org.gradle.internal.properties.annotations.TypeMetadataStore;
import org.gradle.internal.properties.bean.PropertyPairVisitor;
import org.gradle.internal.reflect.DefaultTypeValidationContext;
import org.gradle.internal.reflect.validation.TypeValidationProblemRenderer;
import org.gradle.model.internal.type.ModelType;
import org.gradle.plugin.software.internal.SoftwareTypeRegistry;

import javax.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

import static com.google.common.collect.ImmutableList.toImmutableList;

/**
 * A {@link PluginTarget} that inspects the plugin for {@link SoftwareType} properties and adds them as extensions on the target prior to
 * applying the plugin via the delegate.  Once the plugin is applied, build-level conventions are then applied to the software type model objects.
 */
public class SoftwareTypeModelWiringPluginTarget implements PluginTarget {
    private final ProjectInternal target;
    private final ExtensionAddingVisitor extensionAddingVisitor;
    private final PluginTarget delegate;
    private final InspectionScheme inspectionScheme;

    private final SoftwareTypeRegistry softwareTypeRegistry;

    public SoftwareTypeModelWiringPluginTarget(ProjectInternal target, PluginTarget delegate, InspectionScheme inspectionScheme, SoftwareTypeRegistry softwareTypeRegistry) {
        this.target = target;
        this.extensionAddingVisitor = new ExtensionAddingVisitor(target, inspectionScheme.getMetadataStore());
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
            // Find the SoftwareType properties and add them as extensions on the Project object
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

        // Call the delegate to apply the plugin
        delegate.applyImperative(pluginId, plugin);

        // Apply build-level conventions for all software types
        extensionAddingVisitor.getSoftwareTypes().forEach((softwareType, model) -> applyBuildLevelConventions(target, softwareType, model));
    }

    private void applyBuildLevelConventions(ProjectInternal target, SoftwareType softwareType, Object model) {
        Object convention = target.getGradle().getSettings().getExtensions().getByName(softwareType.name());
        inspectionScheme.getPropertyPairWalker().visitPropertyPairs(softwareType.modelPublicType(), Cast.uncheckedCast(model), Cast.uncheckedCast(convention), new PropertyPairVisitor() {
            @Override
            public <T> void visitPropertyTypePair(@Nullable Property<T> model, @Nullable Property<T> convention) {
                if (model != null && convention != null && convention.isPresent()) {
                    model.convention(convention);
                }
            }
        });
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

    private static String getPluginObjectDisplayName(Object parameterObject) {
        return ModelType.of(new DslObject(parameterObject).getDeclaredType()).getDisplayName();
    }

    @Override
    public void applyRules(@Nullable String pluginId, Class<?> clazz) {
        delegate.applyRules(pluginId, clazz);
    }

    @Override
    public void applyImperativeRulesHybrid(@Nullable String pluginId, Plugin<?> plugin, Class<?> declaringClass) {
        delegate.applyImperativeRulesHybrid(pluginId, plugin, declaringClass);
    }

    public static class ExtensionAddingVisitor implements PropertyVisitor {
        private final ProjectInternal target;
        private final TypeMetadataStore typeMetadataStore;
        private final Map<SoftwareType, Object> softwareTypes = new HashMap<>();

        public ExtensionAddingVisitor(ProjectInternal target, TypeMetadataStore typeMetadataStore) {
            this.target = target;
            this.typeMetadataStore = typeMetadataStore;
        }

        public Map<SoftwareType, ?> getSoftwareTypes() {
            return softwareTypes;
        }

        @Override
        public void visitSoftwareTypeProperty(String propertyName, PropertyValue propertyValue, SoftwareType softwareType) {
            // Add software type as an extension
            ExtensionContainer extensions = ((ExtensionAware) target).getExtensions();
            Class<?> publicType = softwareType.modelPublicType();
            Object model = propertyValue.call();
            extensions.add(publicType, softwareType.name(), Cast.uncheckedNonnullCast(model));

            softwareTypes.put(softwareType, model);
        }
    }
}
