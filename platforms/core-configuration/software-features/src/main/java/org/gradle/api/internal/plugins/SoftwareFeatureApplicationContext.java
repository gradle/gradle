/*
 * Copyright 2025 the original author or authors.
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

import org.gradle.api.Project;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.model.ObjectFactory;
import org.gradle.internal.inspection.DefaultTypeParameterInspection;
import org.gradle.internal.inspection.TypeParameterInspection;
import org.gradle.plugin.software.internal.SoftwareFeatureSupportInternal.ProjectFeatureDefinitionContext;

import javax.inject.Inject;

/**
 * Represents the context in which a software features is applied and the services
 * available in that context.
 */
public interface SoftwareFeatureApplicationContext {
    /**
     * The ProjectLayout for the Project the software feature is applied to.
     */
    @Inject
    ProjectLayout getProjectLayout();

    /**
     * The ObjectFactory for the Project the software feature is applied to.
     */
    @Inject
    ObjectFactory getObjectFactory();

    /**
     * The Project this software feature is applied to.  This should be used as a last
     * resort when the services necessary are not exposed in other ways in this context.
     * This method will eventually be removed.
     */
    @Inject
    Project getProject();

    /**
     * Allows a transform to create or access the build model object of a given
     * definition object.  This can be used to register build model objects
     * for nested definition objects and expose them as binding points for other
     * software features.
     */
    <T extends HasBuildModel<V>, V extends BuildModel> V getBuildModel(T definition);

    /**
     * Creates, registers, and returns a new build model instance for the given {@code definition} instance.
     * The build model implementation is created as a managed object of the definition's public build model type.
     * <p>
     * This method must only be used on nested definition objects, such as container elements, and not on a feature's primary definition object, which has its
     * build model registered automatically.
     * <p>
     * A build model must be registered for a definition before {@link ProjectFeatureDefinitionContext#getBuildModel()} is used on it.
     *
     * @see SoftwareFeatureApplicationContext#registerBuildModel(HasBuildModel, Class) the other overload to create a build model of a specific implementation type.
     *
     * @throws IllegalStateException if there is already a build model instance registered for the definition.
     */
    default <T extends HasBuildModel<V>, V extends BuildModel> V registerBuildModel(T definition) {
        @SuppressWarnings("rawtypes")
        TypeParameterInspection<HasBuildModel, BuildModel> inspection = new DefaultTypeParameterInspection<>(HasBuildModel.class, BuildModel.class, BuildModel.NONE.class);
        Class<V> modelType = inspection.parameterTypeFor(definition.getClass());
        if (modelType == null) {
            throw new IllegalArgumentException("Cannot determine build model type for " + definition.getClass());
        }

        return registerBuildModel(definition, modelType);
    }

    /**
     * Creates, registers, and returns a new build model of the specific implementation type {@code implementationType} for the given {@code definition} instance.
     * <p>
     * This method must only be used on nested definition objects, such as container elements, and not on a feature's primary definition object, which has its
     * build model registered automatically.
     * <p>
     * A build model must be registered for a definition before {@link ProjectFeatureDefinitionContext#getBuildModel()} is used on it.
     *
     * @see SoftwareFeatureApplicationContext#registerBuildModel(HasBuildModel, Class) the other overload to create a build model of a specific implementation type.
     *
     * @throws IllegalStateException if there is already a build model instance registered for the definition.
     */
    <T extends HasBuildModel<V>, V extends BuildModel> V registerBuildModel(T definition, Class<? extends V> implementationType);
}
