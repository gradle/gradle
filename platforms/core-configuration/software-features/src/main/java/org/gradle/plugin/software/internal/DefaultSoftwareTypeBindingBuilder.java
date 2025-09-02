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
import org.gradle.api.Project;
import org.gradle.api.internal.plugins.BuildModel;
import org.gradle.api.internal.plugins.DslBindingBuilder;
import org.gradle.api.internal.plugins.DslBindingBuilderInternal;
import org.gradle.api.internal.plugins.HasBuildModel;
import org.gradle.api.internal.plugins.SoftwareFeatureApplicationContext;
import org.gradle.api.internal.plugins.SoftwareFeatureBinding;
import org.gradle.api.internal.plugins.SoftwareFeatureTransform;
import org.gradle.api.internal.plugins.SoftwareTypeBindingBuilder;
import org.gradle.api.internal.plugins.SoftwareTypeBindingBuilderInternal;
import org.gradle.api.internal.plugins.SoftwareTypeTransform;
import org.gradle.api.internal.plugins.TargetTypeInformation;
import org.gradle.util.Path;

import java.util.ArrayList;
import java.util.List;

public class DefaultSoftwareTypeBindingBuilder implements SoftwareTypeBindingBuilderInternal {
    private final List<DslBindingBuilderInternal<?, ?>> bindings = new ArrayList<>();

    @Override
    public <T extends HasBuildModel<V>, V extends BuildModel> DslBindingBuilder<T, V> bindSoftwareType(String name, Class<T> dslType, Class<V> buildModelType, SoftwareTypeTransform<T, V> transform) {
        SoftwareFeatureTransform<T, V, ?> featureTransform = (SoftwareFeatureApplicationContext context, T definition, V buildModel, Object parentDefinition) ->
            transform.transform(context, definition, buildModel);

        DslBindingBuilderInternal<T, V> builder = new DefaultDslBindingBuilder<>(dslType,
            buildModelType,
            new TargetTypeInformation.DefinitionTargetTypeInformation<>(Project.class),
            Path.path(name),
            featureTransform
        );

        bindings.add(builder);
        return builder;
    }

    @Override
    public <T extends HasBuildModel<V>, V extends BuildModel> DslBindingBuilder<T, V> bindSoftwareType(String name, Class<T> dslType, SoftwareTypeTransform<T, V> transform) {
        return bindSoftwareType(name, dslType, ModelTypeUtils.getBuildModelClass(dslType), transform);
    }

    public SoftwareTypeBindingBuilder apply(Action<SoftwareTypeBindingBuilder> configuration) {
        configuration.execute(this);
        return this;
    }

    @Override
    public List<SoftwareFeatureBinding<?, ?>> build() {
        List<SoftwareFeatureBinding<?, ?>> result = new ArrayList<>();
        for (DslBindingBuilderInternal<?, ?> binding : bindings) {
            result.add(binding.build());
        }
        return result;
    }
}
