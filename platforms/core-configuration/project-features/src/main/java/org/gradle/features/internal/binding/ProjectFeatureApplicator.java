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

import org.gradle.api.internal.DynamicObjectAware;
import org.gradle.features.binding.BuildModel;
import org.gradle.features.binding.Definition;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * Encapsulates the work of applying a project feature to a target.
 *
 * @since 8.12
 */
@ServiceScope(Scope.Project.class)
public interface ProjectFeatureApplicator {
    /**
     * Instantiates the definition and build model for the given project feature returning a {@link FeatureApplication}.
     *
     * Note that this does not apply the feature to the target.  {@link FeatureApplication#apply()} must be called to apply the feature.
     *
     * @param target - the receiver object the feature will be applied to
     * @param projectFeature - the feature to instantiate the definition and build model for
     * @return the definition for the feature
     * @param <OwnDefinition> the type of the definition for the feature
     * @param <OwnBuildModel> the type of the build model for the feature
     */
    <OwnDefinition extends Definition<OwnBuildModel>, OwnBuildModel extends BuildModel> FeatureApplication<OwnDefinition, OwnBuildModel> createFeatureApplicationFor(DynamicObjectAware target, ProjectFeatureImplementation<OwnDefinition, OwnBuildModel> projectFeature);

    /**
     * Represents a feature instance that has been created.
     * @param <OwnDefinition> the type of the definition
     * @param <OwnBuildModel> the type of the build model
     */
    interface FeatureApplication<OwnDefinition extends Definition<OwnBuildModel>, OwnBuildModel extends BuildModel> {
        OwnDefinition getDefinitionInstance();
        boolean isProjectType();
        void apply();
    }
}
