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

import org.gradle.api.model.ObjectFactory;
import org.gradle.features.binding.BuildModel;
import org.gradle.features.binding.Definition;
import org.gradle.features.binding.ProjectFeatureApplyAction;

/**
 * Factory for creating the transform action for a project feature binding.
 *
 * @param <OwnDefinition>> The type of the definition.
 * @param <OwnBuildModel>> The type of the build model.
 * @param <TargetDefinition> The type of the target definition.
 */
public interface ProjectFeatureApplyActionFactory<OwnDefinition extends Definition<OwnBuildModel>, OwnBuildModel extends BuildModel, TargetDefinition> {
    /**
     * Creates the transform action for a project feature binding.
     *
     * @param objectFactory The object factory specific to the feature context.
     * @return The transform action for the project feature binding.
     */
     ProjectFeatureApplyAction<OwnDefinition, OwnBuildModel, TargetDefinition> create(ObjectFactory objectFactory);
}
