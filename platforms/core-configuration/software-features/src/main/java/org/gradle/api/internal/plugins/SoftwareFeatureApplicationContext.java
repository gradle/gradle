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
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.internal.Cast;
import org.gradle.internal.inspection.DefaultTypeParameterInspection;
import org.gradle.internal.inspection.TypeParameterInspection;

import javax.inject.Inject;

public interface SoftwareFeatureApplicationContext {
    @Inject
    ProjectLayout getProjectLayout();

    @Inject
    Project getProject();

    @Inject
    ObjectFactory getObjectFactory();

    default <T extends HasBuildModel<? extends V>, V extends BuildModel> V getOrCreateModel(T definition) {
        if (definition instanceof ExtensionAware) {
            Object existingModel = ((ExtensionAware) definition).getExtensions().findByName(SoftwareFeatureBinding.MODEL);
            if (existingModel != null) {
                return Cast.uncheckedCast(existingModel);
            }
        }

        @SuppressWarnings("rawtypes")
        TypeParameterInspection<HasBuildModel, BuildModel> inspection = new DefaultTypeParameterInspection<>(HasBuildModel.class, BuildModel.class, BuildModel.NONE.class);
        Class<V> modelType = inspection.parameterTypeFor(definition.getClass());
        if (modelType == null) {
            throw new IllegalArgumentException("Cannot determine build model type for " + definition.getClass());
        }
        return getOrCreateModel(definition, modelType);
    }

    default <T extends HasBuildModel<V>, V extends BuildModel> V getOrCreateModel(T definition, Class<? extends V> implementationType) {
        return ((ExtensionAware) definition).getExtensions().create(SoftwareFeatureBinding.MODEL, implementationType);
    }
}
