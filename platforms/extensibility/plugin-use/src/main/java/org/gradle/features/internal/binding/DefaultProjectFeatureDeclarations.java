/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.features.internal.binding;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import org.gradle.api.NamedDomainObjectCollectionSchema;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.initialization.Settings;
import org.gradle.features.annotations.BindsProjectFeature;
import org.gradle.features.annotations.BindsProjectType;
import org.gradle.features.binding.BuildModel;
import org.gradle.features.binding.Definition;
import org.gradle.features.binding.ProjectFeatureBinding;
import org.gradle.features.binding.ProjectTypeBinding;
import org.gradle.features.binding.TargetTypeInformation;
import org.gradle.api.internal.tasks.properties.InspectionScheme;
import org.gradle.api.problems.Severity;
import org.gradle.api.problems.internal.GradleCoreProblemGroup;
import org.gradle.api.problems.internal.InternalProblem;
import org.gradle.api.problems.internal.InternalProblemReporter;
import org.gradle.api.reflect.TypeOf;
import org.gradle.api.tasks.Nested;
import org.gradle.internal.Cast;
import org.gradle.internal.Pair;
import org.gradle.internal.logging.text.TreeFormatter;
import org.gradle.internal.properties.annotations.TypeMetadata;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.reflect.annotations.TypeAnnotationMetadata;
import org.gradle.internal.reflect.validation.TypeValidationProblemRenderer;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Collections.singletonList;

/**
 * Default implementation of {@link ProjectFeatureDeclarations} that registers project types.
 */
public class DefaultProjectFeatureDeclarations implements ProjectFeatureDeclarations {
    private final Map<RegisteringPluginKey, Set<Class<? extends Plugin<Project>>>> pluginClasses = new LinkedHashMap<>();

    @Nullable
    private Map<String, Set<ProjectFeatureImplementation<?, ?>>> projectFeatureImplementations;

    @SuppressWarnings("unused")
    private final InspectionScheme inspectionScheme;
    private final Instantiator instantiator;
    private final InternalProblemReporter problemReporter;

    public DefaultProjectFeatureDeclarations(InspectionScheme inspectionScheme, Instantiator instantiator, InternalProblemReporter problemReporter) {
        this.inspectionScheme = inspectionScheme;
        this.instantiator = instantiator;
        this.problemReporter = problemReporter;
    }

    @Override
    public void addDeclaration(@Nullable String pluginId, Class<? extends Plugin<Project>> pluginClass, Class<? extends Plugin<Settings>> registeringPluginClass) {
        if (projectFeatureImplementations != null) {
            throw new IllegalStateException("Cannot register a plugin after project types have been discovered");
        }
        RegisteringPluginKey pluginKey = new RegisteringPluginKey(registeringPluginClass, pluginId);
        pluginClasses.computeIfAbsent(pluginKey, k -> new LinkedHashSet<>()).add(pluginClass);
    }

    private Map<String, Set<ProjectFeatureImplementation<?, ?>>> discoverProjectFeatureImplementations() {
        Map<String, Set<ProjectFeatureImplementation<?, ?>>> projectFeatureDeclarations = new LinkedHashMap<>();
        pluginClasses.forEach((registeringPluginClass, registeredPluginClasses) ->
            registeredPluginClasses.forEach(pluginClass -> {
                TypeMetadata pluginClassTypeMetadata = inspectionScheme.getMetadataStore().getTypeMetadata(pluginClass);
                TypeAnnotationMetadata pluginClassAnnotationMetadata = pluginClassTypeMetadata.getTypeAnnotationMetadata();
                registerTypeIfPresent(registeringPluginClass, pluginClass, pluginClassAnnotationMetadata, projectFeatureDeclarations);
                registerFeaturesIfPresent(registeringPluginClass, pluginClass, pluginClassAnnotationMetadata, projectFeatureDeclarations);
            })
        );
        return ImmutableMap.copyOf(projectFeatureDeclarations);
    }

    private <T extends Definition<V>, V extends BuildModel> void registerFeature(
        RegisteringPluginKey registeringPlugin,
        Class<? extends Plugin<Project>> pluginClass,
        ProjectFeatureBindingDeclaration<T, V> binding,
        Map<String, Set<ProjectFeatureImplementation<?, ?>>> projectFeatureDeclarations
    ) {
        String projectFeatureName = binding.getName();

        if (binding.getDefinitionSafety() == ProjectFeatureBindingDeclaration.Safety.SAFE) {
            validateDefinitionSafety(binding);
        }

        if (binding.targetDefinitionType() instanceof TargetTypeInformation.BuildModelTargetTypeInformation &&
            ((TargetTypeInformation.BuildModelTargetTypeInformation<?>) binding.targetDefinitionType()).getBuildModelType().equals(BuildModel.None.class)) {

            InternalProblem bindingTypeProblem = problemReporter.internalCreate(builder -> builder
                .id("bind=to-build-model-none", "Project features binds to BuildModel.None", GradleCoreProblemGroup.configurationUsage())
                .details("A project feature cannot bind to 'BuildModel.None' as its target build model type.")
                .contextualLabel("Project feature '" + projectFeatureName + "' is bound to 'BuildModel.None'")
                .solution("Bind to a target definition type instead.")
                .solution("Bind to a concrete build model type other than 'BuildModel.None'.")
                .severity(Severity.ERROR)
            );

            throwTypeValidationException("Project feature '" + projectFeatureName + "' is bound to an invalid type:", singletonList(bindingTypeProblem));
        }

        Set<ProjectFeatureImplementation<?, ?>> implementations = projectFeatureDeclarations.computeIfAbsent(
            projectFeatureName,
            k -> new LinkedHashSet<>()
        );

        List<Class<? extends Plugin<Project>>> existingPluginClasses = implementations.stream().filter(existingFeature ->
                // If the feature name matches another registration, check if the target types are ambiguous
                existingFeature.getFeatureName().equals(binding.getName()) && TargetTypeInformationChecks.isOverlappingBindingType(existingFeature.getTargetDefinitionType(), binding.targetDefinitionType()))
            .map(ProjectFeatureImplementation::getPluginClass)
            .collect(Collectors.toList());

        if (!existingPluginClasses.isEmpty()) {
            List<InternalProblem> problems = new ArrayList<>();
            existingPluginClasses.forEach(existingPluginClass -> {
                problems.add(
                    problemReporter.internalCreate(builder -> builder
                        .id("duplicate-project-feature-registration", "Duplicate project feature registration", GradleCoreProblemGroup.configurationUsage())
                        .details("A project feature or type with a given name must bind to a unique target type.")
                        .contextualLabel("Project feature '" + projectFeatureName + "' is registered by both '" + pluginClass.getName() + "' and '" + existingPluginClass.getName() + "' but their bindings have overlapping target types.")
                        .solution("Remove one of the plugins from the build.")
                        .severity(Severity.ERROR)
                    )
                );
            });

            throwTypeValidationException("Project feature '" + projectFeatureName + "' is registered by multiple plugins:", problems);
        }

        implementations.add(
            new DefaultProjectFeatureImplementation<>(
                projectFeatureName,
                binding.getDefinitionType(),
                binding.getDefinitionImplementationType().orElse(binding.getDefinitionType()),
                binding.getDefinitionSafety(),
                binding.getApplyActionSafety(),
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
        Map<String, Set<ProjectFeatureImplementation<?, ?>>> projectFeatureDeclarations
    ) {
        Optional<BindsProjectFeature> bindsProjectFeatureAnnotation = pluginClassAnnotationMetadata.getAnnotation(BindsProjectFeature.class);
        if (bindsProjectFeatureAnnotation.isPresent()) {
            BindsProjectFeature bindsSoftwareType = bindsProjectFeatureAnnotation.get();
            Class<? extends ProjectFeatureBinding> bindingRegistrationClass = bindsSoftwareType.value();
            ProjectFeatureBinding bindingRegistration = instantiator.newInstance(bindingRegistrationClass);
            ProjectFeatureBindingBuilderInternal builder = new DefaultProjectFeatureBindingBuilder();
            bindingRegistration.bind(builder);
            builder.build().forEach(binding ->
                registerFeature(registeringPluginClass, pluginClass, binding, projectFeatureDeclarations)
            );
        }
    }

    private void registerTypeIfPresent(
        RegisteringPluginKey registeringPluginKey,
        Class<? extends Plugin<Project>> pluginClass,
        TypeAnnotationMetadata pluginClassAnnotationMetadata,
        Map<String, Set<ProjectFeatureImplementation<?, ?>>> projectFeatureDeclarations
    ) {
        Optional<BindsProjectType> bindsProjectTypeAnnotation = pluginClassAnnotationMetadata.getAnnotation(BindsProjectType.class);
        if (bindsProjectTypeAnnotation.isPresent()) {
            BindsProjectType bindsProjectType = bindsProjectTypeAnnotation.get();
            Class<? extends ProjectTypeBinding> bindingRegistrationClass = bindsProjectType.value();
            ProjectTypeBinding bindingRegistration = instantiator.newInstance(bindingRegistrationClass);
            ProjectTypeBindingBuilderInternal builder = new DefaultProjectTypeBindingBuilder();
            bindingRegistration.bind(builder);
            builder.build().forEach(binding ->
                registerFeature(registeringPluginKey, pluginClass, binding, projectFeatureDeclarations)
            );
        }
    }

    private void validateDefinitionSafety(ProjectFeatureBindingDeclaration<?, ?> binding) {
        List<InternalProblem> problems = new ArrayList<>();
        if (binding.getDefinitionImplementationType().isPresent() && !binding.getDefinitionImplementationType().get().equals(binding.getDefinitionType())) {
            problems.add(problemReporter.internalCreate(builder -> builder
                .id("unsafe-definition-implementation-type", "Definition implementation type specified for safe definition", GradleCoreProblemGroup.configurationUsage())
                .details("Safe definitions must not specify an implementation type.")
                .contextualLabel("Project feature '" + binding.getName() + "' has a definition with type '" + binding.getDefinitionType().getSimpleName() + "' which was declared safe but has an implementation type '" + binding.getDefinitionImplementationType().get().getSimpleName() + "'")
                .solution("Mark the definition as unsafe.")
                .solution("Remove the implementation type specification.")
                .severity(Severity.ERROR)
            ));
        }

        if (!binding.getDefinitionType().isInterface()) {
            problems.add(problemReporter.internalCreate(builder -> builder
                .id("unsafe-definition-type-not-interface", "Definition type not an interface for safe definition", GradleCoreProblemGroup.configurationUsage())
                .details("Safe definition types must be an interface.")
                .contextualLabel("Project feature '" + binding.getName() + "' has a definition with type '" + binding.getDefinitionType().getSimpleName() + "' which was declared safe but is not an interface")
                .solution("Mark the definition as unsafe.")
                .solution("Refactor the type as an interface.")
                .severity(Severity.ERROR)
            ));
        }

        validateDefinition(binding.getDefinitionType(), problems);

        problemReporter.report(problems);

        throwTypeValidationException("Project feature '" + binding.getName() + "' has a definition type which was declared safe but has the following issues:", problems);
    }

    private static void throwTypeValidationException(String summary, List<InternalProblem> problems) {
        List<String> formattedErrors = problems.stream()
            .filter(problem -> problem.getDefinition().getSeverity().equals(Severity.ERROR))
            .map(TypeValidationProblemRenderer::renderMinimalInformationAbout)
            .collect(Collectors.toList());

        if (!formattedErrors.isEmpty()) {
            TreeFormatter formatter = new TreeFormatter(true);
            formatter.node(summary);
            formatter.startChildren();
            formattedErrors.forEach(formatter::node);
            formatter.endChildren();
            throw new IllegalArgumentException(formatter.toString());
        }
    }

    private void validateDefinition(Class<?> definitionType, List<InternalProblem> problems) {
        TypeMetadata definitionTypeMetadata = inspectionScheme.getMetadataStore().getTypeMetadata(definitionType);
        definitionTypeMetadata.getTypeAnnotationMetadata().getPropertiesAnnotationMetadata().forEach(propertyMetadata -> {
            if (propertyMetadata.isAnnotationPresent(Inject.class)) {
                problems.add(problemReporter.internalCreate(builder -> builder
                    .id("unsafe-definition-inject-property", "Property annotated with @Inject in safe definition", GradleCoreProblemGroup.configurationUsage())
                    .details("Safe definition types cannot inject services.")
                    .contextualLabel("The definition type has @Inject annotated property '" + propertyMetadata.getPropertyName() + "' in type '" + definitionType.getSimpleName() + "'")
                    .solution("Mark the definition as unsafe.")
                    .solution("Remove the @Inject annotation from the '" + propertyMetadata.getPropertyName() + "' property.")
                    .severity(Severity.ERROR)
                ));
            }

            if (propertyMetadata.isAnnotationPresent(Nested.class)) {
                validateDefinition(propertyMetadata.getDeclaredReturnType().getRawType(), problems);
            }
        });
    }

    @Override
    public Map<String, Set<ProjectFeatureImplementation<?, ?>>> getProjectFeatureImplementations() {
        if (projectFeatureImplementations == null) {
            projectFeatureImplementations = discoverProjectFeatureImplementations();
        }
        return projectFeatureImplementations;
    }

    @Override
    public NamedDomainObjectCollectionSchema getSchema() {
        return () -> Iterables.transform(
            () -> getProjectFeatureImplementations().entrySet().stream().flatMap(entry ->
                entry.getValue().stream().map(impl -> Pair.of(entry.getKey(), impl))
            ).iterator(),
            pair -> new ProjectFeatureSchema(pair.left, pair.right.getDefinitionPublicType())
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
