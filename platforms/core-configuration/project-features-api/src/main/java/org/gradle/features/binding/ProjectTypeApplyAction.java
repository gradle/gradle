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
 * A transformation action for configuring a build model from a definition and executing any necessary build logic.
 *
 * @param <OwnDefinition> the type of the definition
 * @param <OwnBuildModel> the type of the build model
 *
 * @since 9.5.0
 */
@Incubating
public interface ProjectTypeApplyAction<OwnDefinition extends Definition<OwnBuildModel>, OwnBuildModel extends BuildModel> {
    /**
     * Apply configuration from the definition to the build model and execute any necessary build logic.
     *
     * @param context the application context
     * @param definition the definition
     * @param buildModel the build model
     *
     * @since 9.5.0
     */
    void apply(ProjectFeatureApplicationContext context, OwnDefinition definition, OwnBuildModel buildModel);

    /**
     * A no-op {@link ProjectTypeApplyAction} that performs no build logic and makes
     * no mutations to the build model. Use this when a project type binding needs an
     * apply action class but does not have any work to perform.
     *
     * <p>Pass {@code ProjectTypeApplyAction.None.class} to
     * {@code ProjectFeatureBindingBuilder#bindProjectType} or one of its overloads.
     *
     * @param <OwnDefinition> the type of the project type definition
     * @param <OwnBuildModel> the type of the project type's build model
     *
     * @since 9.6.0
     */
    @Incubating
    interface None<
        OwnDefinition extends Definition<OwnBuildModel>,
        OwnBuildModel extends BuildModel
    > extends ProjectTypeApplyAction<OwnDefinition, OwnBuildModel> {
        @Override
        default void apply(
            ProjectFeatureApplicationContext context,
            OwnDefinition definition,
            OwnBuildModel buildModel
        ) {
        }
    }
}
