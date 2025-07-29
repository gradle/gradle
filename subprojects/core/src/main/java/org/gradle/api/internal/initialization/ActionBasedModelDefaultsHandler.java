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

package org.gradle.api.internal.initialization;

import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Plugin;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.initialization.SharedModelDefaults;
import org.gradle.api.initialization.internal.SharedModelDefaultsInternal;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.internal.plugins.software.SoftwareType;
import org.gradle.api.internal.tasks.properties.InspectionScheme;
import org.gradle.api.problems.internal.InternalProblems;
import org.gradle.internal.Cast;
import org.gradle.internal.exceptions.DefaultMultiCauseException;
import org.gradle.internal.properties.PropertyValue;
import org.gradle.internal.properties.PropertyVisitor;
import org.gradle.internal.reflect.DefaultTypeValidationContext;
import org.gradle.internal.reflect.validation.TypeValidationProblemRenderer;
import org.gradle.model.internal.type.ModelType;
import org.gradle.plugin.software.internal.ModelDefault;
import org.gradle.plugin.software.internal.ModelDefaultsApplicator;
import org.gradle.plugin.software.internal.ModelDefaultsHandler;
import org.gradle.plugin.software.internal.SoftwareFeatureRegistry;
import org.gradle.plugin.software.internal.SoftwareFeatureImplementation;
import org.jspecify.annotations.Nullable;

import static com.google.common.collect.ImmutableList.toImmutableList;

public class ActionBasedModelDefaultsHandler implements ModelDefaultsHandler {

    private final SharedModelDefaultsInternal sharedModelDefaults;
    private final ProjectLayout projectLayout;
    private final SoftwareFeatureRegistry softwareFeatureRegistry;
    private final InspectionScheme inspectionScheme;
    private final InternalProblems problems;

    public ActionBasedModelDefaultsHandler(
        SharedModelDefaults sharedModelDefaults,
        ProjectLayout projectLayout,
        SoftwareFeatureRegistry softwareFeatureRegistry,
        InspectionScheme inspectionScheme,
        InternalProblems problems
    ) {
        this.sharedModelDefaults = (SharedModelDefaultsInternal) sharedModelDefaults;
        this.projectLayout = projectLayout;
        this.softwareFeatureRegistry = softwareFeatureRegistry;
        this.inspectionScheme = inspectionScheme;
        this.problems = problems;
    }

    @Override
    public <T> void apply(T target, ModelDefaultsApplicator.ClassLoaderContext classLoaderContext, String softwareFeatureName, Plugin<?> plugin) {
        SoftwareFeatureImplementation<?> softwareFeatureImplementation = softwareFeatureRegistry.getSoftwareFeatureImplementations().get(softwareFeatureName);

        DefaultTypeValidationContext typeValidationContext = DefaultTypeValidationContext.withRootType(plugin.getClass(), false, problems);
        inspectionScheme.getPropertyWalker().visitProperties(
            plugin,
            typeValidationContext,
            new PropertyVisitor() {
                @Override
                public void visitSoftwareTypeProperty(String propertyName, PropertyValue value, Class<?> declaredPropertyType, SoftwareType softwareType) {
                    try {
                        sharedModelDefaults.setProjectLayout(projectLayout);
                        softwareFeatureImplementation.visitModelDefaults(
                            Cast.uncheckedCast(ActionBasedDefault.class),
                            executeActionVisitor(softwareFeatureImplementation, value.call())
                        );
                    } finally {
                        sharedModelDefaults.clearProjectLayout();
                    }

                }
            }
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

    private static <T> ModelDefault.Visitor<Action<? super T>> executeActionVisitor(SoftwareFeatureImplementation<T> softwareFeatureImplementation, @Nullable Object modelObject) {
        if (modelObject == null) {
            throw new IllegalStateException("The model object for " + softwareFeatureImplementation.getFeatureName() + " declared in " + softwareFeatureImplementation.getPluginClass().getName() + " is null.");
        }
        return action -> action.execute(Cast.uncheckedNonnullCast(modelObject));
    }

    private static String getPluginObjectDisplayName(Object parameterObject) {
        return ModelType.of(new DslObject(parameterObject).getDeclaredType()).getDisplayName();
    }

}
