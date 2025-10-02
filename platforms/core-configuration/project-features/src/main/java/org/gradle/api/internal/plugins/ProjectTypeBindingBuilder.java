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

/**
 * A builder for creating project type bindings as well as declaring build logic
 * associated with the binding.
 */
public interface ProjectTypeBindingBuilder {
    /**
     * Create a binding for a project type definition object in the DSL with the provided name.
     * The supplied transform is used to implement the build logic associated with the binding.
     *
     * @param name the name of the binding.  This is how it will be referenced in the DSL.
     * @param dslType the class of the project type definition object
     * @param buildModelType the class of the build model object for this project type
     * @param transform the transform that maps the definition to the build model and implements the build logic associated with the feature
     * @return a {@link DslBindingBuilder} that can be used to further configure the binding
     * @param <OwnDefinition> the type of the project type definition object
     * @param <OwnBuildModel> the type of the build model object for this project type
     */
    <OwnDefinition extends Definition<OwnBuildModel>, OwnBuildModel extends BuildModel> DslBindingBuilder<OwnDefinition, OwnBuildModel> bindProjectType(
        String name,
        Class<OwnDefinition> dslType,
        Class<OwnBuildModel> buildModelType,
        ProjectTypeApplyAction<OwnDefinition, OwnBuildModel> transform
    );

    /**
     * Create a binding for a project type definition object in the DSL with the provided name.
     * The supplied transform is used to implement the build logic associated with the binding.
     *
     * @param name the name of the binding.  This is how it will be referenced in the DSL.
     * @param dslType the class of the project type definition object
     * @param transform the transform that maps the definition to the build model and implements the build logic associated with the feature
     * @return a {@link DslBindingBuilder} that can be used to further configure the binding
     * @param <OwnDefinition> the type of the project type definition object
     * @param <OwnBuildModel> the type of the build model object for this project type
     */
    <OwnDefinition extends Definition<OwnBuildModel>, OwnBuildModel extends BuildModel> DslBindingBuilder<OwnDefinition, OwnBuildModel> bindProjectType(
        String name,
        Class<OwnDefinition> dslType,
        ProjectTypeApplyAction<OwnDefinition, OwnBuildModel> transform
    );
}
