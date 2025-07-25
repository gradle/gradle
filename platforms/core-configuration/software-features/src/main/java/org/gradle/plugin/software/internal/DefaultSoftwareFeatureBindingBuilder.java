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
import org.gradle.api.internal.plugins.HasBuildModel;
import org.gradle.api.internal.plugins.SoftwareFeatureBinding;
import org.gradle.api.internal.plugins.SoftwareFeatureBindingBuilder;
import org.gradle.api.internal.plugins.SoftwareFeatureTransform;
import org.gradle.util.Path;

import java.util.List;
import java.util.ArrayList;

public class DefaultSoftwareFeatureBindingBuilder implements SoftwareFeatureBindingBuilder {
    private final List<DslBindingBuilder<?, ?>> bindings = new ArrayList<>();

    @Override
    public <
        Definition extends HasBuildModel<OwnBuildModel>,
        TargetDefinition extends HasBuildModel<?>,
        OwnBuildModel extends BuildModel
        >
    DslBindingBuilder<Definition, OwnBuildModel> bindSoftwareFeature(
        String name,
        ModelBindingTypeInformation<Definition, OwnBuildModel, TargetDefinition> bindingTypeInformation,
        SoftwareFeatureTransform<Definition, OwnBuildModel, TargetDefinition> transform
    ) {
        DslBindingBuilder<Definition, OwnBuildModel> builder = new DefaultDslBindingBuilder<>(
            bindingTypeInformation.definitionType,
            bindingTypeInformation.ownBuildModelType,
            bindingTypeInformation.targetType,
            Path.path(name),
            transform
        );
        bindings.add(builder);
        return builder;
    }

    public SoftwareFeatureBindingBuilder apply(Action<SoftwareFeatureBindingBuilder> configuration) {
        configuration.execute(this);
        return this;
    }

    @Override
    public List<SoftwareFeatureBinding<?, ?>> build() {
        List<SoftwareFeatureBinding<?, ?>> result = new ArrayList<>();
        for (DslBindingBuilder<?, ?> binding : bindings) {
            result.add(binding.build());
        }
        return result;
    }
}
