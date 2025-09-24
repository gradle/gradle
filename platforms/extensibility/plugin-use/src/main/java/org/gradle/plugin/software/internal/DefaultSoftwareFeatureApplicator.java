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

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Named;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.internal.DynamicObjectAware;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.plugins.BuildModel;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.internal.plugins.HasBuildModel;
import org.gradle.api.internal.plugins.PluginManagerInternal;
import org.gradle.api.internal.plugins.SoftwareFeatureApplicationContext;
import org.gradle.api.internal.plugins.software.SoftwareType;
import org.gradle.api.internal.tasks.properties.InspectionScheme;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.problems.Severity;
import org.gradle.api.problems.internal.GradleCoreProblemGroup;
import org.gradle.api.problems.internal.InternalProblems;
import org.gradle.internal.Cast;
import org.gradle.internal.exceptions.DefaultMultiCauseException;
import org.gradle.internal.properties.PropertyValue;
import org.gradle.internal.properties.PropertyVisitor;
import org.gradle.internal.reflect.DefaultTypeValidationContext;
import org.gradle.internal.reflect.validation.TypeValidationProblemRenderer;
import org.gradle.model.internal.type.ModelType;
import org.gradle.plugin.software.internal.SoftwareFeatureSupportInternal.ProjectFeatureDefinitionContext;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

import static com.google.common.collect.ImmutableList.toImmutableList;

/**
 * Applies software features to a target object by registering the software model as an extension of the target object (unless
 * configured otherwise) and performing validations.  Application returns the public model object of the feature.  Features
 * are applied only once per target object and always return the same public model object for a given target/feature
 * combination.
 */
public class DefaultSoftwareFeatureApplicator implements SoftwareFeatureApplicator {
    private final SoftwareFeatureRegistry softwareFeatureRegistry;
    private final ModelDefaultsApplicator modelDefaultsApplicator;
    private final InspectionScheme inspectionScheme;
    private final InternalProblems problems;
    private final PluginManagerInternal pluginManager;
    private final ClassLoaderScope classLoaderScope;
    private final ObjectFactory objectFactory;

    public DefaultSoftwareFeatureApplicator(SoftwareFeatureRegistry softwareFeatureRegistry, ModelDefaultsApplicator modelDefaultsApplicator, InspectionScheme inspectionScheme, InternalProblems problems, PluginManagerInternal pluginManager, ClassLoaderScope classLoaderScope, ObjectFactory objectFactory) {
        this.softwareFeatureRegistry = softwareFeatureRegistry;
        this.modelDefaultsApplicator = modelDefaultsApplicator;
        this.inspectionScheme = inspectionScheme;
        this.problems = problems;
        this.pluginManager = pluginManager;
        this.classLoaderScope = classLoaderScope;
        this.objectFactory = objectFactory;
    }

    @Override
    public <T, V> T applyFeatureTo(DynamicObjectAware parentDefinition, SoftwareFeatureImplementation<T, V> softwareFeature) {
        ProjectFeatureDefinitionContext parentDefinitionContext = SoftwareFeatureSupportInternal.getContext(parentDefinition);

        ProjectFeatureDefinitionContext.ChildDefinitionAdditionResult result = parentDefinitionContext.getOrAddChildDefinition(softwareFeature, () -> {
            if (parentDefinition instanceof Project) {
                checkSingleProjectTypeApplication(parentDefinitionContext, softwareFeature);
            }

            pluginManager.apply(softwareFeature.getPluginClass());
            Plugin<Project> plugin = pluginManager.getPluginContainer().getPlugin(softwareFeature.getPluginClass());

            Object definition = (softwareFeature instanceof BoundSoftwareFeatureImplementation) ?
                instantiateBoundFeatureObjectsAndApply(parentDefinition, Cast.uncheckedCast(softwareFeature)) :
                instantiateLegacySoftwareTypeDefinition(parentDefinition, softwareFeature, plugin);

            return Cast.uncheckedNonnullCast(definition);
        });

        if (result.isNew) {
            Plugin<Project> plugin = pluginManager.getPluginContainer().getPlugin(softwareFeature.getPluginClass());
            modelDefaultsApplicator.applyDefaultsTo(parentDefinition, result.definition, new ClassLoaderContextFromScope(classLoaderScope), plugin, softwareFeature);
        }

        return Cast.uncheckedNonnullCast(result.definition);
    }

    private static <T, V> void checkSingleProjectTypeApplication(ProjectFeatureDefinitionContext context, SoftwareFeatureImplementation<T, V> softwareFeature) {
        context.childrenDefinitions().keySet().stream().findFirst().ifPresent(softwareTypeAlreadyApplied -> {
            throw new IllegalStateException(
                "The project has already applied the '" +
                    softwareTypeAlreadyApplied.getFeatureName() +
                    "' software type and is also attempting to apply the '" +
                    softwareFeature.getFeatureName() +
                    "' software type.  Only one software type can be applied to a project."
            );
        });
    }

    private Object instantiateLegacySoftwareTypeDefinition(Object parentDefinition, SoftwareFeatureImplementation<?, ?> softwareFeature, Plugin<?> plugin) {
        applyAndMaybeRegisterExtension(parentDefinition, softwareFeature, plugin);
        return ((ExtensionAware) parentDefinition).getExtensions().getByName(softwareFeature.getFeatureName());
    }

    private <T extends HasBuildModel<V>, V extends BuildModel> T instantiateBoundFeatureObjectsAndApply(Object parentDefinition, BoundSoftwareFeatureImplementation<T, V> softwareFeature) {
        T definition = createDefinitionObject(parentDefinition, softwareFeature);
        V buildModelInstance = SoftwareFeatureSupportInternal.createBuildModelInstance(objectFactory, definition, softwareFeature);
        SoftwareFeatureSupportInternal.attachDefinitionContext(definition, buildModelInstance, this, softwareFeatureRegistry, objectFactory);

        SoftwareFeatureApplicationContext applyActionContext =
            objectFactory.newInstance(SoftwareFeatureApplicationContextInternal.class);

        softwareFeature.getBindingTransform().transform(applyActionContext, definition, buildModelInstance, Cast.uncheckedCast(parentDefinition));

        return definition;
    }

    private <T, V> T createDefinitionObject(Object target, SoftwareFeatureImplementation<T, V> softwareFeature) {
        Class<? extends T> dslType = softwareFeature.getDefinitionImplementationType();

        if (Named.class.isAssignableFrom(dslType)) {
            if (target instanceof Named) {
                return objectFactory.newInstance(softwareFeature.getDefinitionPublicType(), ((Named) target).getName());
            } else {
                throw new IllegalArgumentException("Cannot infer a name for definition " + dslType.getSimpleName() +
                    " because the parent definition of type " + target.getClass().getSimpleName() + " does not implement Named.");
            }
        } else {
            return objectFactory.newInstance(dslType);
        }
    }

    private <T, V> void applyAndMaybeRegisterExtension(Object target, SoftwareFeatureImplementation<T, V> softwareFeature, Plugin<?> plugin) {
        DefaultTypeValidationContext typeValidationContext = DefaultTypeValidationContext.withRootType(softwareFeature.getPluginClass(), false, problems);

        ExtensionAddingVisitor<T> extensionAddingVisitor = new ExtensionAddingVisitor<>((ExtensionAware) target, typeValidationContext, softwareFeatureRegistry, this, objectFactory);
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

    private static String getPluginObjectDisplayName(Object parameterObject) {
        return ModelType.of(new DslObject(parameterObject).getDeclaredType()).getDisplayName();
    }

    @NullMarked
    public static class ExtensionAddingVisitor<T> implements PropertyVisitor {
        private final ExtensionAware target;
        private final DefaultTypeValidationContext validationContext;
        private final SoftwareFeatureApplicator applicator;
        private final SoftwareFeatureRegistry softwareFeatureRegistry;
        private final ObjectFactory objectFactory;

        public ExtensionAddingVisitor(
            ExtensionAware target,
            DefaultTypeValidationContext validationContext,
            SoftwareFeatureRegistry softwareFeatureRegistry,
            SoftwareFeatureApplicator applicator,
            ObjectFactory objectFactory
        ) {
            this.target = target;
            this.validationContext = validationContext;
            this.softwareFeatureRegistry = softwareFeatureRegistry;
            this.applicator = applicator;
            this.objectFactory = objectFactory;
        }

        /**
         * Checks the invariants related to the plugin's software type property and its effects on the runtime model.
         *
         * The extension must have been already added in {@link SoftwareFeatureApplicator#applyFeatureTo}
         */
        @Override
        public void visitSoftwareTypeProperty(String propertyName, PropertyValue value, Class<?> declaredPropertyType, SoftwareType softwareType) {
            T publicModelObject = Cast.uncheckedNonnullCast(Objects.requireNonNull(value.call()));

            SoftwareFeatureSupportInternal.attachLegacyDefinitionContext(publicModelObject, applicator, softwareFeatureRegistry, objectFactory);

            if (softwareType.disableModelManagement()) {
                Object extension = target.getExtensions().findByName(softwareType.name());
                if (extension == null) {
                    validationContext.visitPropertyProblem(problem ->
                        problem
                            .forProperty(propertyName)
                            .id("extension-not-registered-for-software-type", "was not registered as an extension", GradleCoreProblemGroup.validation().property())
                            .contextualLabel("has @SoftwareType annotation with 'disableModelManagement' set to true, but no extension with name '" + softwareType.name() + "' was registered")
                            .severity(Severity.ERROR)
                            .details("When 'disableModelManagement' is set, the plugin must register the '" + propertyName + "' property as an extension with the same name as the software type.")
                            .solution("During plugin application, register the '" + propertyName + "' property as an extension with the name '" + softwareType.name() + "'.")
                            .solution("Set 'disableModelManagement' to false or remove the parameter from the @SoftwareType annotation.")
                    );
                } else if (extension != publicModelObject) {
                    validationContext.visitPropertyProblem(problem ->
                        problem
                            .forProperty(propertyName)
                            .id("mismatched-extension-registered-for-software-type", "does not match the extension registered as '" + softwareType.name(), GradleCoreProblemGroup.validation().property())
                            .contextualLabel("has @SoftwareType annotation with 'disableModelManagement' set to true, but the extension with name '" + softwareType.name() + "' does not match the value of the property")
                            .severity(Severity.ERROR)
                            .details("When 'disableModelManagement' is set, the plugin must register the '" + propertyName + "' property as an extension with the same name as the software type.")
                            .solution("During plugin application, register the '" + propertyName + "' property as an extension with the name '" + softwareType.name() + "'.")
                    );
                }
            } else {
                target.getExtensions().add(
                    publicTypeFrom(softwareType.modelPublicType(), declaredPropertyType),
                    softwareType.name(),
                    publicModelObject
                );
            }
        }

        private Class<? super T> publicTypeFrom(Class<?> fromAnnotation, Class<?> declaredPropertyType) {
            return Cast.uncheckedCast(fromAnnotation == Void.class ? declaredPropertyType : fromAnnotation);
        }
    }

    private static class ClassLoaderContextFromScope implements ModelDefaultsApplicator.ClassLoaderContext {
        private final ClassLoaderScope scope;

        public ClassLoaderContextFromScope(ClassLoaderScope scope) {
            this.scope = scope;
        }

        @Override
        public ClassLoader getClassLoader() {
            return scope.getLocalClassLoader();
        }

        @Override
        public ClassLoader getParentClassLoader() {
            return scope.getParent().getLocalClassLoader();
        }
    }
}
