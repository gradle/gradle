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
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.initialization.Settings;
import org.gradle.api.internal.plugins.software.RegistersSoftwareTypes;
import org.gradle.api.internal.plugins.software.RegistersProjectFeatures;
import org.gradle.api.internal.tasks.properties.InspectionScheme;
import org.gradle.api.problems.Severity;
import org.gradle.api.problems.internal.GradleCoreProblemGroup;
import org.gradle.api.problems.internal.InternalProblems;
import org.gradle.configuration.ConfigurationTargetIdentifier;
import org.gradle.internal.Cast;
import org.gradle.internal.exceptions.DefaultMultiCauseException;
import org.gradle.internal.properties.annotations.TypeMetadata;
import org.gradle.internal.reflect.DefaultTypeValidationContext;
import org.gradle.internal.reflect.validation.TypeValidationProblemRenderer;
import org.gradle.plugin.software.internal.ProjectFeatureDeclarations;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

import static com.google.common.collect.ImmutableList.toImmutableList;

/**
 * A {@link PluginTarget} that inspects the plugin for {@link RegistersSoftwareTypes} or {@link RegistersProjectFeatures} annotations and adds the
 * specified plugins to {@link ProjectFeatureDeclarations} prior to applying the plugin via the delegate.
 */
@NullMarked
public class ProjectFeatureDeclarationPluginTarget implements PluginTarget {
    private final PluginTarget delegate;
    private final ProjectFeatureDeclarations projectFeatureDeclarations;
    private final InspectionScheme inspectionScheme;
    private final InternalProblems problems;

    public ProjectFeatureDeclarationPluginTarget(PluginTarget delegate, ProjectFeatureDeclarations projectFeatureDeclarations, InspectionScheme inspectionScheme, InternalProblems problems) {
        this.delegate = delegate;
        this.projectFeatureDeclarations = projectFeatureDeclarations;
        this.inspectionScheme = inspectionScheme;
        this.problems = problems;
    }

    @Override
    public ConfigurationTargetIdentifier getConfigurationTargetIdentifier() {
        return delegate.getConfigurationTargetIdentifier();
    }

    @Override
    public void applyImperative(@Nullable String pluginId, Plugin<?> plugin) {
        TypeToken<?> pluginType = TypeToken.of(plugin.getClass());
        TypeMetadata typeMetadata = inspectionScheme.getMetadataStore().getTypeMetadata(pluginType.getRawType());
        findAndAddProjectTypes(pluginId, typeMetadata);
        findAndAddProjectFeatures(pluginId, typeMetadata);

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

    private void findAndAddProjectTypes(@Nullable String pluginId, TypeMetadata typeMetadata) {
        Optional<RegistersSoftwareTypes> registersSoftwareType = typeMetadata.getTypeAnnotationMetadata().getAnnotation(RegistersSoftwareTypes.class);
        registersSoftwareType.ifPresent(registration -> {
            addFeatureDeclarations(registration.value(), Cast.uncheckedCast(typeMetadata.getType()), pluginId);
        });
    }

    private void findAndAddProjectFeatures(@Nullable String pluginId, TypeMetadata typeMetadata) {
        Optional<RegistersProjectFeatures> registersProjectFeatures = typeMetadata.getTypeAnnotationMetadata().getAnnotation(RegistersProjectFeatures.class);
        registersProjectFeatures.ifPresent(registration -> {
            addFeatureDeclarations(registration.value(), Cast.uncheckedCast(typeMetadata.getType()), pluginId);
        });
    }

    private void addFeatureDeclarations(Class<? extends Plugin<Project>>[] featurePlugins, Class<? extends Plugin<Settings>> registeringPlugin, @Nullable String pluginId) {
        for (Class<? extends Plugin<Project>> projectFeatureImplClass : featurePlugins) {
            validateProjectFeatures(projectFeatureImplClass, registeringPlugin);
            projectFeatureDeclarations.addDeclaration(pluginId, projectFeatureImplClass, registeringPlugin);
        }
    }

    void validateProjectFeatures(Class<? extends Plugin<Project>> projectTypePluginImplClass, Class<?> registeringPlugin) {
        DefaultTypeValidationContext typeValidationContext = DefaultTypeValidationContext.withRootType(projectTypePluginImplClass, false, problems);
        TypeToken<?> projectTypePluginImplType = TypeToken.of(projectTypePluginImplClass);
        TypeMetadata projectTypePluginImplMetadata = inspectionScheme.getMetadataStore().getTypeMetadata(projectTypePluginImplType.getRawType());
        projectTypePluginImplMetadata.visitValidationFailures(null, typeValidationContext);

        boolean isBinding = projectTypePluginImplMetadata.getTypeAnnotationMetadata().getAnnotation(BindsProjectType.class).isPresent() ||
            projectTypePluginImplMetadata.getTypeAnnotationMetadata().getAnnotation(BindsProjectFeature.class).isPresent();

        if (!isBinding) {
            typeValidationContext.visitTypeProblem(problem ->
                problem.withAnnotationType(projectTypePluginImplClass)
                    .id("missing-software-type", "Missing project feature annotation", GradleCoreProblemGroup.validation().type())
                    .contextualLabel("is registered as a project feature plugin but does not expose a project feature")
                    .severity(Severity.ERROR)
                    .details("This class was registered as a project feature plugin, but it does not expose a project feature. Project feature plugins must expose at least one project feature via either a @BindsProjectType or @BindsProjectFeature annotation on the plugin class.")
                    .solution("Remove " + projectTypePluginImplClass.getSimpleName() + " from the @RegistersSoftwareTypes or @RegistersProjectFeatures annotation on " + registeringPlugin.getSimpleName())
            );
        }

        if (!typeValidationContext.getProblems().isEmpty()) {
            throw new DefaultMultiCauseException(
                String.format(typeValidationContext.getProblems().size() == 1
                        ? "A problem was found with the %s plugin."
                        : "Some problems were found with the %s plugin.",
                    projectTypePluginImplClass.getSimpleName()),
                typeValidationContext.getProblems().stream()
                    .map(TypeValidationProblemRenderer::renderMinimalInformationAbout)
                    .sorted()
                    .map(InvalidUserDataException::new)
                    .collect(toImmutableList())
            );
        }
    }
}
