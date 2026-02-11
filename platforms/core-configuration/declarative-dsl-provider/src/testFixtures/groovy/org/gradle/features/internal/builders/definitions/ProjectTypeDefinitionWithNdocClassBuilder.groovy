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
 * A {@link ProjectTypeDefinitionClassBuilder} for creating a project type definition that has a NamedDomainObjectContainer property.
 */
class ProjectTypeDefinitionWithNdocClassBuilder extends ProjectTypeDefinitionClassBuilder {
    @Override
    String getPublicTypeClassContent() {
        return """
            package org.gradle.test;

            import org.gradle.api.Named;
            import org.gradle.api.NamedDomainObjectContainer;
            import org.gradle.api.provider.Property;
            import ${Definition.class.name};
            import ${BuildModel.class.name};

            public abstract class ${publicTypeClassName} implements ${Definition.class.simpleName}<${publicTypeClassName}.ModelType> {
                public abstract Property<String> getId();

                public abstract NamedDomainObjectContainer<Foo> getFoos();

                public abstract static class Foo implements Named {
                    private String name;

                    public Foo(String name) {
                        this.name = name;
                    }

                    @Override
                    public String getName() {
                        return name;
                    }

                    public abstract Property<Integer> getX();

                    public abstract Property<Integer> getY();

                    @Override
                    public String toString() {
                        return "Foo(name = " + name + ", x = " + getX().get() + ", y = " + getY().get() + ")";
                    }
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
            ${displayProperty("definition", "foos", 'definition.getFoos().stream().map(Object::toString).collect(java.util.stream.Collectors.joining(", "))')}
        """
    }
}
