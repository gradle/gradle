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
 * A builder for configuring a DSL binding for a project type or project feature.
 *
 * @param <OwnDefinition> the type of the project type or project feature definition object
 * @param <OwnBuildModel> the type of the build model object for this project type or project feature
 */
public interface DslBindingBuilder<OwnDefinition extends Definition<OwnBuildModel>, OwnBuildModel extends BuildModel> {
    /**
     * Specify the implementation type to use when creating instances of the definition object in the DSL.
     *
     * @param implementationType the implementation type to use
     * @return this builder
     */
    DslBindingBuilder<OwnDefinition, OwnBuildModel> withDefinitionImplementationType(Class<? extends OwnDefinition> implementationType);

    /**
     * Specify the implementation type to use when creating instances of the build model object.
     *
     * @param implementationType the implementation type to use
     * @return this builder
     */
    DslBindingBuilder<OwnDefinition, OwnBuildModel> withBuildModelImplementationType(Class<? extends OwnBuildModel> implementationType);
}
