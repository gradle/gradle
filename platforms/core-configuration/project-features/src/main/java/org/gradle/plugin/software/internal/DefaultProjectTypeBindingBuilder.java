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
import org.gradle.api.internal.plugins.DeclaredProjectFeatureBindingBuilder;
import org.gradle.api.internal.plugins.DeclaredProjectFeatureBindingBuilderInternal;
import org.gradle.api.internal.plugins.Definition;
import org.gradle.api.internal.plugins.ProjectFeatureApplicationContext;
import org.gradle.api.internal.plugins.ProjectFeatureBindingDeclaration;
import org.gradle.api.internal.plugins.ProjectFeatureApplyAction;
import org.gradle.api.internal.plugins.ProjectTypeBindingBuilder;
import org.gradle.api.internal.plugins.ProjectTypeBindingBuilderInternal;
import org.gradle.api.internal.plugins.ProjectTypeApplyAction;
import org.gradle.api.internal.plugins.TargetTypeInformation;
import org.gradle.util.Path;

import java.util.ArrayList;
import java.util.List;

public class DefaultProjectTypeBindingBuilder implements ProjectTypeBindingBuilderInternal {
    private final List<DeclaredProjectFeatureBindingBuilderInternal<?, ?>> bindings = new ArrayList<>();

    private <T extends Definition<V>, V extends BuildModel> DeclaredProjectFeatureBindingBuilder<T, V> bindProjectType(String name, Class<T> definitionClass, Class<V> buildModelClass, ProjectTypeApplyAction<T, V> transform) {
        // This needs to be an anonymous class for configuration cache compatibility
        ProjectFeatureApplyAction<T, V, ?> featureTransform = new ProjectFeatureApplyAction<T, V, Object>() {
            @Override
            public void transform(ProjectFeatureApplicationContext context, T definition, V buildModel, Object parentDefinition) {
                transform.transform(context, definition, buildModel);
            }
        };

        DeclaredProjectFeatureBindingBuilderInternal<T, V> builder = new DefaultDeclaredProjectFeatureBindingBuilder<>(
            definitionClass,
            buildModelClass,
            new TargetTypeInformation.DefinitionTargetTypeInformation<>(Project.class),
            Path.path(name),
            featureTransform
        );

        bindings.add(builder);
        return builder;
    }

    @Override
    public <T extends Definition<V>, V extends BuildModel> DeclaredProjectFeatureBindingBuilder<T, V> bindProjectType(String name, Class<T> definitionClass, ProjectTypeApplyAction<T, V> transform) {
        return bindProjectType(name, definitionClass, ModelTypeUtils.getBuildModelClass(definitionClass), transform);
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
