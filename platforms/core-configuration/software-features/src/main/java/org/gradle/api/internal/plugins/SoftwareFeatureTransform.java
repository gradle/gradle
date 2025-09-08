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
 * A transformation action that applies configuration from a software feature definition to its build model.
 *
 * @param <Definition> the type of the software feature definition
 * @param <OwnBuildModel> the type of the software feature's own build model
 * @param <ParentDefinition> the type of the parent software feature definition
 */
public interface SoftwareFeatureTransform<Definition, OwnBuildModel, ParentDefinition> {
    void transform(SoftwareFeatureApplicationContext context, Definition definition, OwnBuildModel buildModel, ParentDefinition parentDefinition);
}
