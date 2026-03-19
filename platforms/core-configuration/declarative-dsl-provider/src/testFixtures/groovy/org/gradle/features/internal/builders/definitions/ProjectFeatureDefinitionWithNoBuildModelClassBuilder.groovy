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
 * A {@link ProjectFeatureDefinitionClassBuilder} for creating a project feature definition that does not have a build model class.
 */
class ProjectFeatureDefinitionWithNoBuildModelClassBuilder extends ProjectFeatureDefinitionClassBuilder {
    @Override
    protected String getPublicTypeClassContent() {
        return """
            package org.gradle.test;

            import ${Definition.class.name};
            import ${BuildModel.class.name};
            import org.gradle.api.provider.Property;
            import org.gradle.api.Action;
            import org.gradle.api.tasks.Nested;
            import javax.inject.Inject;
            import org.gradle.api.model.ObjectFactory;

            public interface ${publicTypeClassName} extends ${Definition.class.simpleName}<${BuildModel.class.simpleName}.None> {
                Property<String> getText();
            }
        """
    }

    @Override
    String getBuildModelMapping() {
        return ""
    }

    @Override
    String displayDefinitionPropertyValues() {
        return """
            ${displayProperty("definition", "text", "definition.getText().get()")}
        """
    }

    @Override
    String displayModelPropertyValues() {
        return ""
    }

    @Override
    String getBuildModelPublicTypeClassName() {
        return "${BuildModel.class.simpleName}.None"
    }

    @Override
    String getBuildModelFullPublicClassName() {
        return "${BuildModel.class.name}.None"
    }

    @Override
    String getBuildModelImplementationTypeClassName() {
        return getBuildModelFullPublicClassName()
    }
}
