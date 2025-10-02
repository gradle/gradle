/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.plugin.software.internal;

import org.gradle.api.Action;
import org.gradle.api.internal.plugins.BuildModel;
import org.gradle.api.internal.plugins.DslBindingBuilder;
import org.gradle.api.internal.plugins.DslBindingBuilderInternal;
import org.gradle.api.internal.plugins.Definition;
import org.gradle.api.internal.plugins.ProjectFeatureBinding;
import org.gradle.api.internal.plugins.ProjectFeatureBindingBuilder;
import org.gradle.api.internal.plugins.ProjectFeatureBindingBuilderInternal;
import org.gradle.api.internal.plugins.ProjectFeatureApplyAction;
import org.gradle.util.Path;

import java.util.List;
import java.util.ArrayList;

import static org.gradle.plugin.software.internal.ModelTypeUtils.getBuildModelClass;

public class DefaultProjectFeatureBindingBuilder implements ProjectFeatureBindingBuilderInternal {
    private final List<DslBindingBuilderInternal<?, ?>> bindings = new ArrayList<>();

    @Override
    public <
        OwnDefinition extends Definition<OwnBuildModel>,
        OwnBuildModel extends BuildModel,
        TargetDefinition extends Definition<?>
        >
    DslBindingBuilder<OwnDefinition, OwnBuildModel> bindProjectFeature(
        String name,
        ModelBindingTypeInformation<OwnDefinition, OwnBuildModel, TargetDefinition> bindingTypeInformation,
        ProjectFeatureApplyAction<OwnDefinition, OwnBuildModel, TargetDefinition> transform
    ) {
        DslBindingBuilderInternal<OwnDefinition, OwnBuildModel> builder = new DefaultDslBindingBuilder<>(
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
    public List<ProjectFeatureBinding<?, ?>> build() {
        List<ProjectFeatureBinding<?, ?>> result = new ArrayList<>();
        for (DslBindingBuilderInternal<?, ?> binding : bindings) {
            result.add(binding.build());
        }
        return result;
    }
}
