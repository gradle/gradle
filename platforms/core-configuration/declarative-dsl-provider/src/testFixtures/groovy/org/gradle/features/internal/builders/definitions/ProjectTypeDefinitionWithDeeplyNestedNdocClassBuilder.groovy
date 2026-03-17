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

import org.gradle.declarative.dsl.model.annotations.HiddenInDefinition
import org.gradle.features.binding.BuildModel
import org.gradle.features.binding.Definition

/**
 * Generates a project type definition where a {@code NamedDomainObjectContainer<Source>} (with
 * {@code Source extends Definition<Source.SourceModel>}) is nested inside a plain {@code Group} type
 * that does NOT extend {@code Definition}. Used to test that features can bind to Definition elements
 * in NDOCs that are nested inside plain (non-Definition) types.
 */
class ProjectTypeDefinitionWithDeeplyNestedNdocClassBuilder extends ProjectTypeDefinitionClassBuilder {

    @Override
    String getPublicTypeClassContent() {
        return """
            package org.gradle.test;

            import org.gradle.api.Action;
            import org.gradle.api.Named;
            import org.gradle.api.NamedDomainObjectContainer;
            import org.gradle.api.provider.Property;
            import org.gradle.api.tasks.Nested;
            import ${Definition.class.name};
            import ${BuildModel.class.name};
            import ${HiddenInDefinition.class.name};

            public interface ${publicTypeClassName} extends ${Definition.class.simpleName}<${publicTypeClassName}.ModelType> {
                Property<String> getId();

                @Nested
                Group getGroup();

                @${HiddenInDefinition.class.simpleName}
                default void group(Action<? super Group> action) {
                    action.execute(getGroup());
                }

                interface Group {
                    NamedDomainObjectContainer<Source> getSources();

                    interface Source extends ${Definition.class.simpleName}<Source.SourceModel>, Named {
                        Property<String> getSourceDir();

                        interface SourceModel extends ${BuildModel.class.simpleName} {
                            Property<String> getProcessedDir();
                        }
                    }
                }

                interface ModelType extends ${BuildModel.class.simpleName} {
                    Property<String> getId();
                }
            }
        """
    }

    @Override
    String getBuildModelMapping() {
        return """
            model.getId().set(definition.getId());
        """
    }

    String getSourceModelPublicClassName() {
        return "${publicTypeClassName}.Group.Source.SourceModel"
    }
}
