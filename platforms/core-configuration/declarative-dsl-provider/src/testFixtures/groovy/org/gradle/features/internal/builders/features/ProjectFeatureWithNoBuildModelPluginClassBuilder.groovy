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

package org.gradle.features.internal.builders.features

import org.gradle.features.annotations.BindsProjectFeature
import org.gradle.features.binding.ProjectFeatureBinding
import org.gradle.features.binding.ProjectFeatureBindingBuilder
import org.gradle.features.internal.builders.definitions.ProjectFeatureDefinitionClassBuilder
import org.gradle.features.registration.TaskRegistrar

/**
 * A {@link ProjectFeaturePluginClassBuilder} that binds a project feature definition with no build model.
 */
@SuppressWarnings("UnusedImport") // because codenarc incorrectly decides some imports are unused
class ProjectFeatureWithNoBuildModelPluginClassBuilder extends ProjectFeaturePluginClassBuilder {
    ProjectFeatureWithNoBuildModelPluginClassBuilder(ProjectFeatureDefinitionClassBuilder definition) {
        super(definition)
    }

    @Override
    protected String getClassContent() {
        return """
            package org.gradle.test;

            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import ${BindsProjectFeature.class.name};
            import ${ProjectFeatureBindingBuilder.class.name};
            import static ${ProjectFeatureBindingBuilder.class.name}.bindingToTargetDefinition;
            import ${ProjectFeatureBinding.class.name};
            import ${TaskRegistrar.class.name};

            @${BindsProjectFeature.class.simpleName}(${projectFeaturePluginClassName}.Binding.class)
            public class ${projectFeaturePluginClassName} implements Plugin<Project> {

                static class Binding implements ${ProjectFeatureBinding.class.simpleName} {
                    @Override public void bind(${ProjectFeatureBindingBuilder.class.simpleName} builder) {
                        builder.${bindingMethodName}(
                            "${name}",
                            ${definition.publicTypeClassName}.class,
                            ${bindingTypeClassName}.class,
                            (context, definition, model, parent) -> {
                                System.out.println("Binding ${definition.publicTypeClassName}");
                                System.out.println("${name} model class: " + model.getClass().getSimpleName());

                                TestProjectTypeDefinition.ModelType parentModel = context.getBuildModel(parent);
                                parentModel.getId().set(definition.getText());
                            }
                        )
                        ${maybeDeclareDefinitionImplementationType()}
                        ${maybeDeclareBindingModifiers()};
                    }
                }

                @Override
                public void apply(Project project) {

                }
            }
        """
    }
}
