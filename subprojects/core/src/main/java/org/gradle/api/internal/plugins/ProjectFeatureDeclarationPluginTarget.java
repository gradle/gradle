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
import org.gradle.api.internal.tasks.properties.InspectionScheme;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.internal.GradleCoreProblemGroup;
import org.gradle.api.problems.internal.ProblemsInternal;
import org.gradle.configuration.ConfigurationTargetIdentifier;
import org.gradle.features.annotations.BindsProjectFeature;
import org.gradle.features.annotations.BindsProjectType;
import org.gradle.features.annotations.ProjectFeature;
import org.gradle.features.annotations.ProjectType;
import org.gradle.features.annotations.RegistersProjectFeatures;
import org.gradle.features.binding.SchemaProjectFeatureApplyAction;
import org.gradle.features.binding.SchemaProjectTypeApplyAction;
import org.gradle.features.internal.binding.ProjectFeatureDeclarations;
import org.gradle.internal.Cast;
import org.gradle.internal.exceptions.DefaultMultiCauseException;
import org.gradle.internal.properties.annotations.TypeMetadata;
import org.gradle.internal.reflect.DefaultTypeValidationContext;
import org.gradle.internal.reflect.validation.TypeValidationProblemRenderer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

import static com.google.common.collect.ImmutableList.toImmutableList;

/**
 * A {@link PluginTarget} that inspects the plugin for {@link RegistersProjectFeatures} annotations and adds the
 * specified plugins to {@link ProjectFeatureDeclarations} prior to applying the plugin via the delegate.
 */
@NullMarked
public class ProjectFeatureDeclarationPluginTarget implements PluginTarget {
    private final PluginTarget delegate;
    private final ProjectFeatureDeclarations projectFeatureDeclarations;
    private final InspectionScheme inspectionScheme;
    private final ProblemsInternal problems;

    public ProjectFeatureDeclarationPluginTarget(PluginTarget delegate, ProjectFeatureDeclarations projectFeatureDeclarations, InspectionScheme inspectionScheme, ProblemsInternal problems) {
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
    public void applyProjectFeatureDeclaration(@Nullable String pluginId, Class<?> declarationClass) {
        // Register-only: a schema apply action applied directly in settings registers a project
        // type/feature declaration. There is no imperative plugin to apply, so we do not delegate.
        validateSchemaDeclaration(declarationClass);
        // Applied directly in settings, so there is no registering settings plugin.
        projectFeatureDeclarations.addSchemaDeclaration(pluginId, declarationClass, null);
    }

    private static boolean isSchemaApplyAction(Class<?> type) {
        return SchemaProjectTypeApplyAction.class.isAssignableFrom(type)
            || SchemaProjectFeatureApplyAction.class.isAssignableFrom(type);
    }

    private void validateSchemaDeclaration(Class<?> declarationClass) {
        boolean isType = SchemaProjectTypeApplyAction.class.isAssignableFrom(declarationClass);
        boolean isFeature = SchemaProjectFeatureApplyAction.class.isAssignableFrom(declarationClass);

        if (isType == isFeature) {
            throw invalidSchemaDeclaration(declarationClass, "must implement exactly one of SchemaProjectTypeApplyAction or SchemaProjectFeatureApplyAction");
        }
        if (isType && !declarationClass.isAnnotationPresent(ProjectType.class)) {
            throw invalidSchemaDeclaration(declarationClass, "is a SchemaProjectTypeApplyAction but is not annotated with @ProjectType");
        }
        if (isFeature && !declarationClass.isAnnotationPresent(ProjectFeature.class)) {
            throw invalidSchemaDeclaration(declarationClass, "is a SchemaProjectFeatureApplyAction but is not annotated with @ProjectFeature");
        }
    }

    private RuntimeException invalidSchemaDeclaration(Class<?> declarationClass, String reason) {
        String message = "Project feature declaration '" + declarationClass.getName() + "' " + reason + ".";
        ProblemId id = ProblemId.create("invalid-schema-feature-declaration", "Invalid schema project type or feature declaration", GradleCoreProblemGroup.validation().type());
        return problems.getInternalReporter()
            .throwing(new InvalidUserDataException(message), id, spec -> spec.contextualLabel(message));
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

    private void findAndAddProjectFeatures(@Nullable String pluginId, TypeMetadata typeMetadata) {
        Optional<RegistersProjectFeatures> registersProjectFeatures = typeMetadata.getTypeAnnotationMetadata().getAnnotation(RegistersProjectFeatures.class);
        registersProjectFeatures.ifPresent(registration -> {
            addFeatureDeclarations(registration.value(), Cast.uncheckedCast(typeMetadata.getType()), pluginId);
        });
    }

    private void addFeatureDeclarations(Class<?>[] featureClasses, Class<? extends Plugin<Settings>> registeringPlugin, @Nullable String pluginId) {
        for (Class<?> featureClass : featureClasses) {
            if (isSchemaApplyAction(featureClass)) {
                validateSchemaDeclaration(featureClass);
                projectFeatureDeclarations.addSchemaDeclaration(pluginId, featureClass, registeringPlugin);
            } else {
                Class<? extends Plugin<Project>> projectFeatureImplClass = Cast.uncheckedCast(featureClass);
                validateProjectFeatures(projectFeatureImplClass, registeringPlugin);
                projectFeatureDeclarations.addDeclaration(pluginId, projectFeatureImplClass, registeringPlugin);
            }
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
            typeValidationContext.visitTypeError(problem ->
                problem.withAnnotationType(projectTypePluginImplClass)
                    .id("missing-software-type", "Missing project feature annotation", GradleCoreProblemGroup.validation().type())
                    .contextualLabel("is registered as a project feature plugin but does not expose a project feature")
                    .details("This class was registered as a project feature plugin, but it does not expose a project feature. Project feature plugins must expose at least one project feature via either a @BindsProjectType or @BindsProjectFeature annotation on the plugin class.")
                    .solution("Remove " + projectTypePluginImplClass.getSimpleName() + " from the @RegistersSoftwareTypes or @RegistersProjectFeatures annotation on " + registeringPlugin.getSimpleName())
            );
        }

        if (!typeValidationContext.getErrors().isEmpty()) {
            throw new DefaultMultiCauseException(
                String.format(typeValidationContext.getErrors().size() == 1
                        ? "A problem was found with the %s plugin."
                        : "Some problems were found with the %s plugin.",
                    projectTypePluginImplClass.getSimpleName()),
                typeValidationContext.getErrors().stream()
                    .map(TypeValidationProblemRenderer::renderMinimalInformationAbout)
                    .sorted()
                    .map(InvalidUserDataException::new)
                    .collect(toImmutableList())
            );
        }
    }
}
