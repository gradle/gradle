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

import org.gradle.test.fixtures.plugin.PluginBuilder

/**
 * A {@link ProjectFeatureDefinitionClassBuilder} for creating a project feature definition that has a parent type with injectable services.
 */
class ProjectFeatureDefinitionWithInjectableParentClassBuilder extends ProjectFeatureDefinitionClassBuilder {
    String parentTypeClassName = "ParentFeatureDefinition"

    ProjectFeatureDefinitionWithInjectableParentClassBuilder() {
        // Add injected service to the parent
        withInjectedServices()
    }

    @Override
    protected String getPublicTypeClassContent() {
        return """
            package org.gradle.test;

            import org.gradle.api.provider.Property;

            public interface ${publicTypeClassName} extends ${parentTypeClassName} {
                Property<String> getNonPublicProperty();
            }
        """
    }

    @Override
    void build(PluginBuilder pluginBuilder) {
        super.build(pluginBuilder)
        pluginBuilder.file("src/main/java/org/gradle/test/${parentTypeClassName}.java") << getDefaultClassContent(parentTypeClassName)
    }
}
