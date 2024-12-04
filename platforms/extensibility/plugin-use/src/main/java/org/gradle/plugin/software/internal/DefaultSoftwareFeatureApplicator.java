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
import org.gradle.api.NonNullApi;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.internal.plugins.PluginManagerInternal;
import org.gradle.api.internal.plugins.software.SoftwareType;
import org.gradle.api.internal.tasks.properties.InspectionScheme;
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

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import static com.google.common.collect.ImmutableList.toImmutableList;

/**
 * Applies software features to a target object by registering the software model as an extension of the target object (unless
 * configured otherwise) and performing validations.  Application returns the public model object of the feature.  Features
 * are applied only once per target object and always return the same public model object for a given target/feature
 * combination.
 */
public class DefaultSoftwareFeatureApplicator implements SoftwareFeatureApplicator {
    private final ModelDefaultsApplicator modelDefaultsApplicator;
    private final InspectionScheme inspectionScheme;
    private final InternalProblems problems;
    private final PluginManagerInternal pluginManager;
    private final Set<AppliedFeature> applied = new HashSet<>();

    public DefaultSoftwareFeatureApplicator(ModelDefaultsApplicator modelDefaultsApplicator, InspectionScheme inspectionScheme, InternalProblems problems, PluginManagerInternal pluginManager) {
        this.modelDefaultsApplicator = modelDefaultsApplicator;
        this.inspectionScheme = inspectionScheme;
        this.problems = problems;
        this.pluginManager = pluginManager;
    }

    @Override
    public <T> T applyFeatureTo(ExtensionAware target, SoftwareTypeImplementation<T> softwareFeature) {
        AppliedFeature appliedFeature = new AppliedFeature(target, softwareFeature);
        if (!applied.contains(appliedFeature)) {
            pluginManager.apply(softwareFeature.getPluginClass());
            Plugin<Project> plugin = pluginManager.getPluginContainer().getPlugin(softwareFeature.getPluginClass());
            applyAndMaybeRegisterExtension(target, softwareFeature, plugin);
            applied.add(appliedFeature);
            modelDefaultsApplicator.applyDefaultsTo(target, plugin, softwareFeature);
        }
        return Cast.uncheckedCast(target.getExtensions().getByName(softwareFeature.getSoftwareType()));
    }

    private <T> void applyAndMaybeRegisterExtension(ExtensionAware target, SoftwareTypeImplementation<T> softwareFeature, Plugin<?> plugin) {
        DefaultTypeValidationContext typeValidationContext = DefaultTypeValidationContext.withRootType(softwareFeature.getPluginClass(), false, problems);
        ExtensionAddingVisitor<T> extensionAddingVisitor = new ExtensionAddingVisitor<>(target, typeValidationContext);
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

    @NonNullApi
    public static class ExtensionAddingVisitor<T> implements PropertyVisitor {
        private final ExtensionAware target;
        private final DefaultTypeValidationContext validationContext;

        public ExtensionAddingVisitor(ExtensionAware target, DefaultTypeValidationContext validationContext) {
            this.target = target;
            this.validationContext = validationContext;
        }

        @Override
        public void visitSoftwareTypeProperty(String propertyName, PropertyValue value, Class<?> declaredPropertyType, SoftwareType softwareType) {
            T publicModelObject = Cast.uncheckedNonnullCast(value.call());
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

    private static class AppliedFeature {
        private final Object target;
        private final SoftwareTypeImplementation<?> softwareFeature;

        public AppliedFeature(Object target, SoftwareTypeImplementation<?> softwareFeature) {
            this.target = target;
            this.softwareFeature = softwareFeature;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            AppliedFeature that = (AppliedFeature) o;
            // We use identity comparison here because we want to ensure that the software feature is applied to
            // each target object, even if two different target objects have equality.
            return target == that.target && Objects.equals(softwareFeature, that.softwareFeature);
        }

        @Override
        public int hashCode() {
            // We use identity hashes here because we want to ensure that the software feature is applied to
            // each target object, even if two different target objects hash equally.
            int result = System.identityHashCode(target);
            result = 31 * result + softwareFeature.hashCode();
            return result;
        }
    }
}
