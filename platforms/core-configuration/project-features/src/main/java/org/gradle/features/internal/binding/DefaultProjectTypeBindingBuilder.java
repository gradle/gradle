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

package org.gradle.features.internal.binding;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.features.binding.BuildModel;
import org.gradle.features.binding.DeclaredProjectFeatureBindingBuilder;
import org.gradle.features.binding.Definition;
import org.gradle.features.binding.ProjectFeatureApplicationContext;
import org.gradle.features.binding.ProjectFeatureApplyAction;
import org.gradle.features.binding.ProjectTypeBindingBuilder;
import org.gradle.features.binding.ProjectTypeApplyAction;
import org.gradle.features.binding.TargetTypeInformation;
import org.gradle.util.Path;

import java.util.ArrayList;
import java.util.List;

public class DefaultProjectTypeBindingBuilder implements ProjectTypeBindingBuilderInternal {
    private final List<DeclaredProjectFeatureBindingBuilderInternal<?, ?>> bindings = new ArrayList<>();

    private <OwnDefinition extends Definition<OwnBuildModel>, OwnBuildModel extends BuildModel>
    DeclaredProjectFeatureBindingBuilder<OwnDefinition, OwnBuildModel> bindProjectType(
        String name,
        Class<OwnDefinition> definitionClass,
        Class<OwnBuildModel> buildModelClass,
        ProjectTypeApplyAction<OwnDefinition, OwnBuildModel> transform
    ) {
        // This needs to be an anonymous class for configuration cache compatibility
        ProjectFeatureApplyActionFactory<OwnDefinition, OwnBuildModel, Object> applyActionFactory = new ProjectFeatureApplyActionFactory<OwnDefinition, OwnBuildModel, Object>() {
            @Override
            public ProjectFeatureApplyAction<OwnDefinition, OwnBuildModel, Object> create(ObjectFactory objectFactory) {
                return new ProjectFeatureApplyAction<OwnDefinition, OwnBuildModel, Object>() {
                    @Override
                    public void apply(ProjectFeatureApplicationContext context, OwnDefinition definition, OwnBuildModel buildModel, Object parentDefinition) {
                        transform.apply(context, definition, buildModel);
                    }
                };
            }
        };

        return declaredProjectFeatureBindingBuilder(name, definitionClass, buildModelClass, applyActionFactory);
    }

    private <OwnDefinition extends Definition<OwnBuildModel>, OwnBuildModel extends BuildModel>
    DeclaredProjectFeatureBindingBuilder<OwnDefinition, OwnBuildModel> bindProjectType(
        String name,
        Class<OwnDefinition> definitionClass,
        Class<OwnBuildModel> buildModelClass,
        Class<? extends ProjectTypeApplyAction<OwnDefinition, OwnBuildModel>> transformClass
    ) {
        // This needs to be an anonymous class for configuration cache compatibility
        ProjectFeatureApplyActionFactory<OwnDefinition, OwnBuildModel, Object> featureTransformFactory = new ProjectFeatureApplyActionFactory<OwnDefinition, OwnBuildModel, Object>() {
            @Override
            public ProjectFeatureApplyAction<OwnDefinition, OwnBuildModel, Object> create(ObjectFactory objectFactory) {
                return new ProjectFeatureApplyAction<OwnDefinition, OwnBuildModel, Object>() {
                    @Override
                    public void apply(ProjectFeatureApplicationContext context, OwnDefinition definition, OwnBuildModel buildModel, Object parentDefinition) {
                        objectFactory.newInstance(transformClass).apply(context, definition, buildModel);
                    }
                };
            }
        };

        return declaredProjectFeatureBindingBuilder(name, definitionClass, buildModelClass, featureTransformFactory);
    }

    private <OwnDefinition extends Definition<OwnBuildModel>, OwnBuildModel extends BuildModel>
    DeclaredProjectFeatureBindingBuilderInternal<OwnDefinition, OwnBuildModel> declaredProjectFeatureBindingBuilder(
        String name,
        Class<OwnDefinition> definitionClass,
        Class<OwnBuildModel> buildModelClass,
        ProjectFeatureApplyActionFactory<OwnDefinition, OwnBuildModel, Object> featureTransformFactory
    ) {
        DeclaredProjectFeatureBindingBuilderInternal<OwnDefinition, OwnBuildModel> builder = new DefaultDeclaredProjectFeatureBindingBuilder<>(
            definitionClass,
            buildModelClass,
            new TargetTypeInformation.DefinitionTargetTypeInformation<>(Project.class),
            Path.path(name),
            featureTransformFactory
        );

        bindings.add(builder);
        return builder;
    }

    @Override
    public <OwnDefinition extends Definition<OwnBuildModel>, OwnBuildModel extends BuildModel>
    DeclaredProjectFeatureBindingBuilder<OwnDefinition, OwnBuildModel> bindProjectType(
        String name,
        Class<OwnDefinition> definitionClass,
        ProjectTypeApplyAction<OwnDefinition, OwnBuildModel> transform
    ) {
        return bindProjectType(name, definitionClass, ModelTypeUtils.getBuildModelClass(definitionClass), transform);
    }

    @Override
    public <OwnDefinition extends Definition<OwnBuildModel>, OwnBuildModel extends BuildModel>
    DeclaredProjectFeatureBindingBuilder<OwnDefinition, OwnBuildModel> bindProjectType(
        String name,
        Class<OwnDefinition> ownDefinitionClass,
        Class<? extends ProjectTypeApplyAction<OwnDefinition, OwnBuildModel>> transformClass
    ) {
        return bindProjectType(name, ownDefinitionClass, ModelTypeUtils.getBuildModelClass(ownDefinitionClass), transformClass);
    }

    public ProjectTypeBindingBuilder apply(Action<ProjectTypeBindingBuilder> configuration) {
        configuration.execute(this);
        return this;
    }

    @Override
    public List<ProjectFeatureBindingDeclaration<?, ?>> build() {
        List<ProjectFeatureBindingDeclaration<?, ?>> result = new ArrayList<>();
        for (DeclaredProjectFeatureBindingBuilderInternal<?, ?> binding : bindings) {
            result.add(binding.build());
        }
        return result;
    }
}
