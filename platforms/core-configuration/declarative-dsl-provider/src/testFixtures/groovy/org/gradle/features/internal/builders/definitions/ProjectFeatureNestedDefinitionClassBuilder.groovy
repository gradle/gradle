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
 * A {@link ProjectFeatureDefinitionClassBuilder} that can be nested inside another definition, and can be exposed as a binding location.
 * It uses the parent definition's build model in its build model mapping.
 */
class ProjectFeatureNestedDefinitionClassBuilder extends ProjectFeatureDefinitionClassBuilder {
    @Override
    String getBuildModelMapping() {
        return super.getBuildModelMapping() + """
            model.getText().set(services.getProviderFactory().provider(() -> definition.getText().get() + " " + context.getBuildModel(parent).getBarProcessed().get()));
        """
    }
}
