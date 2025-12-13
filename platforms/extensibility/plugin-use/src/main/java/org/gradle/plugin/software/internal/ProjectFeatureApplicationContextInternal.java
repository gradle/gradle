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

package org.gradle.plugin.software.internal;

import org.gradle.api.internal.DynamicObjectAware;
import org.gradle.api.internal.plugins.BuildModel;
import org.gradle.api.internal.plugins.Definition;
import org.gradle.api.internal.plugins.ProjectFeatureApplicationContext;
import org.gradle.internal.Cast;
import org.gradle.internal.inspection.DefaultTypeParameterInspection;
import org.gradle.internal.inspection.TypeParameterInspection;

import javax.inject.Inject;

public interface ProjectFeatureApplicationContextInternal extends ProjectFeatureApplicationContext {

    @Inject
    ProjectFeatureDeclarations getProjectFeatureRegistry();

    @Inject
    ProjectFeatureApplicator getProjectFeatureApplicator();

    @Override
    default <T extends Definition<V>, V extends BuildModel> V getBuildModel(T definition) {
        return Cast.uncheckedNonnullCast(ProjectFeatureSupportInternal.getContext((DynamicObjectAware) definition).getBuildModel());
    }

    @Override
    default <T extends Definition<V>, V extends BuildModel> V registerBuildModel(T definition, Class<? extends V> implementationType) {
        ProjectFeatureSupportInternal.ProjectFeatureDefinitionContext maybeContext = ProjectFeatureSupportInternal.tryGetContext(definition);
        if (maybeContext != null) {
            throw new IllegalStateException("Definition object '" + definition + "' already has a registered build model '" + maybeContext.getBuildModel()
                + "'. Registering another build model for it is an error."
            );
        }

        V buildModel = getObjectFactory().newInstance(implementationType);
        ProjectFeatureSupportInternal.attachDefinitionContext(definition, buildModel, getProjectFeatureApplicator(), getProjectFeatureRegistry(), getObjectFactory());

        return buildModel;
    }

    @Override
    default <T extends Definition<V>, V extends BuildModel> V registerBuildModel(T definition) {
        @SuppressWarnings("rawtypes")
        TypeParameterInspection<Definition, BuildModel> inspection = new DefaultTypeParameterInspection<>(Definition.class, BuildModel.class, BuildModel.NONE.class);
        Class<V> modelType = inspection.parameterTypeFor(definition.getClass());
        if (modelType == null) {
            throw new IllegalArgumentException("Cannot determine build model type for " + definition.getClass());
        }

        return registerBuildModel(definition, modelType);
    }
}
