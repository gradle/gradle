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

import org.gradle.features.binding.BuildModel
import org.gradle.features.binding.Definition

/**
 * A {@link ProjectFeatureDefinitionClassBuilder} for creating a project feature definition that has both a public build model type and
 * a separate build model implementation type.
 */
class ProjectFeatureDefinitionWithImplementationAndPublicBuildModelTypesClassBuilder extends ProjectFeatureDefinitionClassBuilder {

    ProjectFeatureDefinitionWithImplementationAndPublicBuildModelTypesClassBuilder() {
        this.hasBuildModelImplementationType = true
    }

    @Override
    protected String getPublicTypeClassContent() {
        return """
            package org.gradle.test;

            import org.gradle.api.provider.Property;
            import ${Definition.class.name};
            import ${BuildModel.class.name};

            public interface ${publicTypeClassName} extends Definition<${publicTypeClassName}.${buildModelPublicTypeClassName}> {
                Property<String> getText();

                interface ${buildModelPublicTypeClassName} extends BuildModel {
                    Property<String> getText();
                }

                abstract class ${buildModelImplementationTypeClassName} implements ${buildModelPublicTypeClassName} {

                }
            }
        """
    }

    @Override
    String getBuildModelMapping() {
        return """
            model.getText().set(definition.getText());
        """
    }

    @Override
    String displayDefinitionPropertyValues() {
        return """
            ${displayProperty("definition", "text", "definition.getText().get()")}
        """
    }

    @Override
    String displayModelPropertyValues() {
        return """
            ${displayProperty("model", "text", "model.getText().get()")}
        """
    }
}
