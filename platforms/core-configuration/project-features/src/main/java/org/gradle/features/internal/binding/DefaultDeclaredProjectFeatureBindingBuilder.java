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

import org.gradle.features.binding.BuildModel;
import org.gradle.features.binding.DeclaredProjectFeatureBindingBuilder;
import org.gradle.features.binding.Definition;
import org.gradle.features.binding.TargetTypeInformation;
import org.gradle.internal.Cast;
import org.gradle.util.Path;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

@NullMarked
public class DefaultDeclaredProjectFeatureBindingBuilder<OwnDefinition extends Definition<OwnBuildModel>, OwnBuildModel extends BuildModel, TargetDefinition> implements DeclaredProjectFeatureBindingBuilderInternal<OwnDefinition, OwnBuildModel> {
    private final Class<OwnDefinition> dslType;
    private final TargetTypeInformation<?> targetDefinitionType;
    private final Class<OwnBuildModel> buildModelType;
    private final Path path;
    private final ProjectFeatureApplyActionFactory<OwnDefinition, OwnBuildModel, ?> applyActionFactory;

    @Nullable private Class<?> dslImplementationType;
    @Nullable private Class<?> buildModelImplementationType;
    private ProjectFeatureBindingDeclaration.Safety definitionSafety = ProjectFeatureBindingDeclaration.Safety.SAFE;
    private ProjectFeatureBindingDeclaration.Safety applyActionSafety = ProjectFeatureBindingDeclaration.Safety.SAFE;

    public DefaultDeclaredProjectFeatureBindingBuilder(
        Class<OwnDefinition> definitionType,
        Class<OwnBuildModel> buildModelType,
        TargetTypeInformation<?> targetDefinitionType,
        Path path,
        ProjectFeatureApplyActionFactory<OwnDefinition, OwnBuildModel, TargetDefinition> applyActionFactory
    ) {
        this.targetDefinitionType = targetDefinitionType;
        this.dslType = definitionType;
        this.buildModelType = buildModelType;
        this.path = path;
        this.applyActionFactory = applyActionFactory;
    }

    private static <OwnDefinition extends Definition<OwnBuildModel>, OwnBuildModel extends BuildModel> ProjectFeatureBindingDeclaration<OwnDefinition, OwnBuildModel> bindingOf(
        Class<OwnDefinition> definitionType,
        @Nullable Class<? extends OwnDefinition> definitionImplementationType,
        ProjectFeatureBindingDeclaration.Safety definitionSafety,
        ProjectFeatureBindingDeclaration.Safety applyActionSafety,
        Path path,
        TargetTypeInformation<?> targetDefinitionType,
        Class<OwnBuildModel> buildModelType,
        @Nullable Class<? extends OwnBuildModel> buildModelImplementationType,
        ProjectFeatureApplyActionFactory<OwnDefinition, OwnBuildModel, ?> projectFeatureApplyActionFactory
    ) {
        return new ProjectFeatureBindingDeclaration<OwnDefinition, OwnBuildModel>() {
            @Override
            public TargetTypeInformation<?> targetDefinitionType() {
                return targetDefinitionType;
            }

            @Override
            public Class<OwnDefinition> getDefinitionType() {
                return definitionType;
            }

            @Override
            public Optional<Class<? extends OwnDefinition>> getDefinitionImplementationType() {
                return Optional.ofNullable(definitionImplementationType);
            }

            @Override
            public Safety getDefinitionSafety() {
                return definitionSafety;
            }

            @Override
            public Safety getApplyActionSafety() {
                return applyActionSafety;
            }

            @Override
            public ProjectFeatureApplyActionFactory<OwnDefinition, OwnBuildModel, ?> getApplyActionFactory() {
                return projectFeatureApplyActionFactory;
            }

            @Override
            public Class<OwnBuildModel> getBuildModelType() {
                return buildModelType;
            }

            @Override
            public Optional<Class<? extends OwnBuildModel>> getBuildModelImplementationType() {
                return Optional.ofNullable(buildModelImplementationType);
            }

            @Override
            public String getName() {
                return Objects.requireNonNull(path.getName());
            }
        };
    }

    @Override
    public DeclaredProjectFeatureBindingBuilder<OwnDefinition, OwnBuildModel> withUnsafeDefinitionImplementationType(Class<? extends OwnDefinition> implementationType) {
        this.dslImplementationType = implementationType;
        return withUnsafeDefinition();
    }

    @Override
    public DeclaredProjectFeatureBindingBuilder<OwnDefinition, OwnBuildModel> withBuildModelImplementationType(Class<? extends OwnBuildModel> implementationType) {
        this.buildModelImplementationType = implementationType;
        return this;
    }

    @Override
    public DeclaredProjectFeatureBindingBuilder<OwnDefinition, OwnBuildModel> withUnsafeDefinition() {
        this.definitionSafety = ProjectFeatureBindingDeclaration.Safety.UNSAFE;
        return this;
    }

    @Override
    public DeclaredProjectFeatureBindingBuilder<OwnDefinition, OwnBuildModel> withUnsafeApplyAction() {
        this.applyActionSafety = ProjectFeatureBindingDeclaration.Safety.UNSAFE;
        return this;
    }

    @Override
    public ProjectFeatureBindingDeclaration<OwnDefinition, OwnBuildModel> build() {
        if (dslImplementationType != null && !dslType.isAssignableFrom(dslImplementationType)) {
            throw new IllegalArgumentException("Implementation type " + dslImplementationType + " is not a subtype of dsl type " + dslType);
        }

        if (buildModelImplementationType != null && !buildModelType.isAssignableFrom(buildModelImplementationType)) {
            throw new IllegalArgumentException("Implementation type " + buildModelImplementationType + " is not a subtype of build model type " + buildModelType);
        }

        return DefaultDeclaredProjectFeatureBindingBuilder.bindingOf(
            dslType,
            Cast.uncheckedCast(dslImplementationType),
            definitionSafety,
            applyActionSafety,
            path,
            targetDefinitionType,
            buildModelType,
            Cast.uncheckedCast(buildModelImplementationType),
            applyActionFactory
        );
    }
}
