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

import org.gradle.declarative.dsl.model.annotations.Adding
import org.gradle.declarative.dsl.model.annotations.HiddenInDefinition
import org.gradle.features.binding.BuildModel
import org.gradle.features.binding.Definition

/**
 * A {@link ProjectTypeDefinitionClassBuilder} for creating a project type definition similar to {@link ProjectTypeDefinitionClassBuilder},
 * but with some different properties and a nested type.
 */
@SuppressWarnings("UnusedImport") // because codenarc incorrectly decides some imports are unused
class AnotherProjectTypeDefinitionClassBuilder extends ProjectTypeDefinitionClassBuilder {
    AnotherProjectTypeDefinitionClassBuilder() {
        publicTypeClassName = "AnotherProjectTypeDefinition"
    }

    @Override
    String getPublicTypeClassContent() {
        return """
            package org.gradle.test;

            import ${Adding.class.name};
            import ${HiddenInDefinition.class.name};

            import org.gradle.api.Action;
            import org.gradle.api.model.ObjectFactory;
            import org.gradle.api.provider.ListProperty;
            import org.gradle.api.provider.Property;
            import org.gradle.api.tasks.Nested;
            import ${Definition.class.name};
            import ${BuildModel.class.name};

            import javax.inject.Inject;

            public interface ${publicTypeClassName} extends ${Definition.class.simpleName}<${publicTypeClassName}.ModelType> {
                Property<String> getId();

                Property<String> getFoo();

                @Nested
                Bar getBar();

                @${HiddenInDefinition.class.simpleName}
                default void bar(Action<? super Bar> action) {
                    action.execute(getBar());
                }

                abstract interface Bar {
                    Property<String> getBaz();
                }

                default String propertyValues() {
                    return "foo = " + getFoo().get() + "\\nbaz = " + getBar().getBaz().get();
                }

                public interface ModelType extends BuildModel {
                    Property<String> getId();
                }
            }
        """
    }

    @Override
    String displayDefinitionPropertyValues() {
        return """
            ${displayProperty("definition", "id", "definition.getId().get()")}
            ${displayProperty("definition", "foo", "definition.getFoo().get()")}
            ${displayProperty("definition", "bar.baz", "definition.getBar().getBaz().get()")}
        """
    }
}
