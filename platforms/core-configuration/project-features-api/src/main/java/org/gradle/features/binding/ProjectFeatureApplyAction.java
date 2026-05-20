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

package org.gradle.features.binding;

import org.gradle.api.Incubating;

/**
 * An action that applies configuration from a project feature definition to its build model and executes any necessary build logic.
 *
 * @param <OwnDefinition> the type of the project feature definition
 * @param <OwnBuildModel> the type of the project feature's own build model
 * @param <ParentDefinition> the type of the parent project feature definition
 *
 * @since 9.5.0
 */
@Incubating
public interface ProjectFeatureApplyAction<OwnDefinition extends Definition<OwnBuildModel>, OwnBuildModel extends BuildModel, ParentDefinition> {
    /**
     * Apply configuration from the project feature definition to its build model and execute any necessary build logic.
     *
     * @param context the application context
     * @param definition the project feature definition
     * @param buildModel the project feature's own build model
     * @param parentDefinition the parent project feature definition
     *
     * @since 9.5.0
     */
    void apply(ProjectFeatureApplicationContext context, OwnDefinition definition, OwnBuildModel buildModel, ParentDefinition parentDefinition);

    /**
     * A no-op {@link ProjectFeatureApplyAction} that performs no build logic and makes
     * no mutations to the build model. Use this when a project feature binding needs an
     * apply action class but does not have any work to perform, for example when the
     * binding exists purely to introduce a DSL name.
     *
     * <p>Pass {@code ProjectFeatureApplyAction.None.class} to
     * {@link ProjectFeatureBindingBuilder#bindProjectFeature} or one of its overloads.
     *
     * @param <OwnDefinition> the type of the project feature definition
     * @param <OwnBuildModel> the type of the project feature's own build model
     * @param <ParentDefinition> the type of the parent project feature definition
     *
     * @since 9.6.0
     */
    @Incubating
    interface None<
        OwnDefinition extends Definition<OwnBuildModel>,
        OwnBuildModel extends BuildModel,
        ParentDefinition
    > extends ProjectFeatureApplyAction<OwnDefinition, OwnBuildModel, ParentDefinition> {
        @Override
        default void apply(
            ProjectFeatureApplicationContext context,
            OwnDefinition definition,
            OwnBuildModel buildModel,
            ParentDefinition parentDefinition
        ) {
        }
    }
}
