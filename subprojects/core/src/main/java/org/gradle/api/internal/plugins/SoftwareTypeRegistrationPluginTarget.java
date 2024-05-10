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
import org.gradle.api.problems.Severity;
import org.gradle.api.problems.internal.GradleCoreProblemGroup;
import org.gradle.configuration.ConfigurationTargetIdentifier;
import org.gradle.internal.exceptions.DefaultMultiCauseException;
import org.gradle.internal.properties.annotations.TypeMetadata;
import org.gradle.internal.reflect.DefaultTypeValidationContext;
import org.gradle.internal.reflect.validation.TypeValidationProblemRenderer;
import org.gradle.plugin.software.internal.SoftwareTypeRegistry;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.google.common.collect.ImmutableList.toImmutableList;

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
                validateSoftwareTypePluginExposesExactlyOneSoftwareType(softwareTypeImplClass, typeMetadata.getType());
                softwareTypeRegistry.register(softwareTypeImplClass);
            }
        });
    }

    void validateSoftwareTypePluginExposesExactlyOneSoftwareType(Class<? extends Plugin<Project>> softwareTypePluginImplClass, Class<?> registeringPlugin) {
        DefaultTypeValidationContext typeValidationContext = DefaultTypeValidationContext.withRootType(softwareTypePluginImplClass, false);
        TypeToken<?> softwareTypePluginImplType = TypeToken.of(softwareTypePluginImplClass);
        TypeMetadata softwareTypePluginImplMetadata = inspectionScheme.getMetadataStore().getTypeMetadata(softwareTypePluginImplType.getRawType());
        softwareTypePluginImplMetadata.visitValidationFailures(null, typeValidationContext);

        List<String> exposedSoftwareTypes = softwareTypePluginImplMetadata.getPropertiesMetadata().stream()
            .map(propertyMetadata -> propertyMetadata.getAnnotation(SoftwareType.class))
            .filter(Optional::isPresent)
            .map(annotation -> annotation.get().name())
            .sorted()
            .collect(Collectors.toList());

        if (exposedSoftwareTypes.isEmpty()) {
            typeValidationContext.visitTypeProblem(problem ->
                problem.withAnnotationType(softwareTypePluginImplClass)
                    .id("missing-software-type", "Missing software type annotation", GradleCoreProblemGroup.validation().type())
                    .contextualLabel("is registered as a software type plugin but does not expose a software type")
                    .severity(Severity.ERROR)
                    .details("This class was registered as a software type plugin, but it does not expose a software type. Software type plugins must expose exactly one software type via a property with the @SoftwareType annotation.")
                    .solution("Add @SoftwareType annotations to properties of " + softwareTypePluginImplClass.getSimpleName())
                    .solution("Remove " + softwareTypePluginImplClass.getSimpleName() + " from the @RegistersSoftwareTypes annotation on " + registeringPlugin.getSimpleName())
            );
        } else if (exposedSoftwareTypes.size() > 1) {
            typeValidationContext.visitTypeProblem(problem ->
                problem.withAnnotationType(softwareTypePluginImplClass)
                    .id("multiple-software-types", "Multiple software type annotations", GradleCoreProblemGroup.validation().type())
                    .contextualLabel("is registered as a software type plugin, but it exposes multiple software types")
                    .severity(Severity.ERROR)
                    .details("This class was registered as a software type plugin, but it exposes multiple software types: [" + String.join(", ", exposedSoftwareTypes) + "]. Software type plugins must expose exactly one software type via a property with the @SoftwareType annotation.")
                    .solution("Add the @SoftwareType annotation to only one property of " + softwareTypePluginImplClass.getSimpleName())
                    .solution("Split " + softwareTypePluginImplClass.getSimpleName() + " into multiple plugins, each exposing a single software type and register all plugins in " + registeringPlugin.getSimpleName() + " using the @RegistersSoftwareTypes annotation")
            );
        }

        if (!typeValidationContext.getProblems().isEmpty()) {
            throw new DefaultMultiCauseException(
                String.format(typeValidationContext.getProblems().size() == 1
                        ? "A problem was found with the %s plugin."
                        : "Some problems were found with the %s plugin.",
                    softwareTypePluginImplClass.getSimpleName()),
                typeValidationContext.getProblems().stream()
                    .map(TypeValidationProblemRenderer::renderMinimalInformationAbout)
                    .sorted()
                    .map(InvalidUserDataException::new)
                    .collect(toImmutableList())
            );
        }
    }
}
