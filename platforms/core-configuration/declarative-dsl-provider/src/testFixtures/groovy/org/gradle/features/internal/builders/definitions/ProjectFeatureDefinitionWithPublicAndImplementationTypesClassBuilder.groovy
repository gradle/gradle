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
 * A {@link ProjectFeatureDefinitionClassBuilder} for creating a project feature definition that has both a public type and a separate
 * implementation type.
 */
class ProjectFeatureDefinitionWithPublicAndImplementationTypesClassBuilder extends ProjectFeatureDefinitionClassBuilder {
    String publicTypeClassName = "FeatureDefinition"

    ProjectFeatureDefinitionWithPublicAndImplementationTypesClassBuilder() {
        this.hasDefinitionImplementationType = true
    }

    @Override
    protected String getImplementationTypeClassContent() {
        return """
            package org.gradle.test;

            import org.gradle.api.provider.Property;

            public interface ${implementationTypeClassName} extends ${publicTypeClassName} {
                Property<String> getNonPublicProperty();
            }
        """
    }
}
