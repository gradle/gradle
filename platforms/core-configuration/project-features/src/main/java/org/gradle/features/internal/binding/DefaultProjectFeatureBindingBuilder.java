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
import org.gradle.features.binding.BuildModel;
import org.gradle.features.binding.DeclaredProjectFeatureBindingBuilder;
import org.gradle.features.binding.Definition;
import org.gradle.features.binding.ProjectFeatureBindingBuilder;
import org.gradle.features.binding.ProjectFeatureApplyAction;
import org.gradle.util.Path;

import java.util.List;
import java.util.ArrayList;

import static org.gradle.features.internal.binding.ModelTypeUtils.getBuildModelClass;

public class DefaultProjectFeatureBindingBuilder implements ProjectFeatureBindingBuilderInternal {
    private final List<DeclaredProjectFeatureBindingBuilderInternal<?, ?>> bindings = new ArrayList<>();

    @Override
    public <
        OwnDefinition extends Definition<OwnBuildModel>,
        OwnBuildModel extends BuildModel,
        TargetDefinition extends Definition<?>
        >
    DeclaredProjectFeatureBindingBuilder<OwnDefinition, OwnBuildModel> bindProjectFeature(
        String name,
        ModelBindingTypeInformation<OwnDefinition, OwnBuildModel, TargetDefinition> bindingTypeInformation,
        ProjectFeatureApplyAction<OwnDefinition, OwnBuildModel, TargetDefinition> transform
    ) {
        DeclaredProjectFeatureBindingBuilderInternal<OwnDefinition, OwnBuildModel> builder = new DefaultDeclaredProjectFeatureBindingBuilder<>(
            bindingTypeInformation.getDefinitionType(),
            getBuildModelClass(bindingTypeInformation.getDefinitionType()),
            bindingTypeInformation.getTargetType(),
            Path.path(name),
            transform
        );
        bindings.add(builder);
        return builder;
    }

    public ProjectFeatureBindingBuilder apply(Action<ProjectFeatureBindingBuilder> configuration) {
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
