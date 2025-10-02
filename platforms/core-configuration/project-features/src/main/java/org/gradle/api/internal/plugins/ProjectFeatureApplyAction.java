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
 * An action that applies configuration from a project feature definition to its build model.
 *
 * @param <OwnDefinition> the type of the project feature definition
 * @param <OwnBuildModel> the type of the project feature's own build model
 * @param <ParentDefinition> the type of the parent project feature definition
 */
public interface ProjectFeatureApplyAction<OwnDefinition, OwnBuildModel, ParentDefinition> {
    void transform(ProjectFeatureApplicationContext context, OwnDefinition definition, OwnBuildModel buildModel, ParentDefinition parentDefinition);
}
