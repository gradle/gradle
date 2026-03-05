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

package org.gradle.features.internal.builders.definitions

/**
 * A {@link ProjectFeatureDefinitionClassBuilder} for creating a project feature definition that binds to a definition that
 * does not have a build model type, and uses the parent's definition in its build model mapping.
 */
class ProjectFeatureThatBindsToDefinitionWithNoBuildModeClassBuilder extends ProjectFeatureDefinitionClassBuilder {
    @Override
    String getBuildModelMapping() {
        return super.getBuildModelMapping() + """
            model.getText().set(parent.getText().map(text -> text + " " + definition.getText().get()));
        """
    }
}
