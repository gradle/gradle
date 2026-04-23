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
 * Generates a project type definition that has a {@code NamedDomainObjectContainer<Source>} property,
 * where {@code Source} extends {@code Definition<Source.SourceModel>}. Used to test NDOC auto-registration
 * of build models.
 */
class ProjectTypeDefinitionWithNdocContainingDefinitionsClassBuilder extends ProjectTypeDefinitionClassBuilder {

    @Override
    String getPublicTypeClassContent() {
        return """
            package org.gradle.test;

            import org.gradle.api.Named;
            import org.gradle.api.NamedDomainObjectContainer;
            import org.gradle.api.provider.ListProperty;
            import org.gradle.api.provider.Property;
            import ${Definition.class.name};
            import ${BuildModel.class.name};

            public interface ${publicTypeClassName} extends ${Definition.class.simpleName}<${publicTypeClassName}.ModelType> {
                Property<String> getId();
                NamedDomainObjectContainer<Source> getSources();

                interface Source extends ${Definition.class.simpleName}<Source.SourceModel>, Named {
                    Property<String> getSourceDir();

                    interface SourceModel extends ${BuildModel.class.simpleName} {
                        Property<String> getProcessedDir();
                    }
                }

                interface ModelType extends ${BuildModel.class.simpleName} {
                    Property<String> getId();
                    ListProperty<Source.SourceModel> getSources();
                }
            }
        """
    }

    @Override
    String getBuildModelMapping() {
        return """
            model.getId().set(definition.getId());
            definition.getSources().forEach(source -> {
                ${publicTypeClassName}.Source.SourceModel sourceModel = context.getBuildModel(source);
                sourceModel.getProcessedDir().set(source.getSourceDir().map(String::toUpperCase));
                model.getSources().add(sourceModel);
            });
        """
    }

    @Override
    String displayDefinitionPropertyValues() {
        return """
            System.out.println("definition id = " + definition.getId().getOrNull());
        """
    }

    @Override
    String displayModelPropertyValues() {
        return """
            System.out.println("model id = " + model.getId().getOrNull());
            model.getSources().get().forEach(s ->
                System.out.println("source processed dir = " + s.getProcessedDir().get()));
        """
    }

    String getSourceModelPublicClassName() {
        return "${publicTypeClassName}.Source.SourceModel"
    }
}
