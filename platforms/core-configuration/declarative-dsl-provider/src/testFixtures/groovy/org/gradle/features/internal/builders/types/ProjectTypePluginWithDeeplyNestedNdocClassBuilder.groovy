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

package org.gradle.features.internal.builders.types

import org.gradle.features.annotations.BindsProjectType
import org.gradle.features.binding.ProjectTypeBinding
import org.gradle.features.binding.ProjectTypeBindingBuilder
import org.gradle.features.internal.builders.definitions.ProjectTypeDefinitionWithDeeplyNestedNdocClassBuilder

/**
 * Generates a project type plugin for a definition that has a {@code NamedDomainObjectContainer<Source>}
 * nested inside a plain (non-Definition) {@code Group} type. Registers a concrete implementation
 * ({@code DefaultSourceModel}) for the deeply nested {@code Source.SourceModel} build model via
 * {@code withNestedBuildModelImplementationType}.
 */
class ProjectTypePluginWithDeeplyNestedNdocClassBuilder extends ProjectTypePluginClassBuilder {

    ProjectTypePluginWithDeeplyNestedNdocClassBuilder(ProjectTypeDefinitionWithDeeplyNestedNdocClassBuilder definition) {
        super(definition)
    }

    @Override
    protected String getClassContent() {
        return """
            package org.gradle.test;

            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.model.ObjectFactory;
            import org.gradle.api.provider.Property;
            import ${ProjectTypeBinding.class.name};
            import ${BindsProjectType.class.name};
            import ${ProjectTypeBindingBuilder.class.name};
            import javax.inject.Inject;

            @${BindsProjectType.class.simpleName}(${projectTypePluginClassName}.Binding.class)
            abstract public class ${projectTypePluginClassName} implements Plugin<Project> {

                static class Binding implements ${ProjectTypeBinding.class.simpleName} {

                    // Concrete implementation for the deeply nested Source.SourceModel
                    static class DefaultSourceModel implements ${definition.publicTypeClassName}.Group.Source.SourceModel {
                        private final Property<String> processedDir;

                        @Inject
                        public DefaultSourceModel(ObjectFactory objects) {
                            this.processedDir = objects.property(String.class);
                        }

                        @Override
                        public Property<String> getProcessedDir() { return processedDir; }
                    }

                    public void bind(${ProjectTypeBindingBuilder.class.simpleName} builder) {
                        builder.bindProjectType("${name}", ${definition.publicTypeClassName}.class, (context, definition, model) -> {
                            System.out.println("Binding " + ${definition.publicTypeClassName}.class.getSimpleName());

                            ${definition.buildModelMapping}
                        })
                        .withNestedBuildModelImplementationType(
                            ${definition.sourceModelPublicClassName}.class,
                            DefaultSourceModel.class
                        )
                        .withUnsafeApplyAction();
                    }
                }

                @Override
                public void apply(Project target) {
                    System.out.println("Applying " + getClass().getSimpleName());
                }
            }
        """
    }
}
