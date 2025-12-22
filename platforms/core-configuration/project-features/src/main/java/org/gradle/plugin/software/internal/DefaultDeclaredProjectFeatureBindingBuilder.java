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

import org.gradle.api.internal.plugins.BuildModel;
import org.gradle.api.internal.plugins.DeclaredProjectFeatureBindingBuilder;
import org.gradle.api.internal.plugins.DeclaredProjectFeatureBindingBuilderInternal;
import org.gradle.api.internal.plugins.Definition;
import org.gradle.api.internal.plugins.ProjectFeatureBindingDeclaration;
import org.gradle.api.internal.plugins.ProjectFeatureApplyAction;
import org.gradle.api.internal.plugins.TargetTypeInformation;
import org.gradle.internal.Cast;
import org.gradle.util.Path;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

@NullMarked
public class DefaultDeclaredProjectFeatureBindingBuilder<T extends Definition<V>, V extends BuildModel> implements DeclaredProjectFeatureBindingBuilderInternal<T, V> {
    private final Class<T> dslType;
    private final TargetTypeInformation<?> targetDefinitionType;
    private final Class<V> buildModelType;
    private final Path path;
    private final ProjectFeatureApplyAction<?, ?, ?> transform;

    @Nullable private Class<?> dslImplementationType;
    @Nullable private Class<?> buildModelImplementationType;
    private ProjectFeatureBindingDeclaration.Safety definitionSafety = ProjectFeatureBindingDeclaration.Safety.SAFE;

    public DefaultDeclaredProjectFeatureBindingBuilder(
        Class<T> definitionType,
        Class<V> buildModelType,
        TargetTypeInformation<?> targetDefinitionType,
        Path path,
        ProjectFeatureApplyAction<T, V, ?> transform
    ) {
        this.targetDefinitionType = targetDefinitionType;
        this.dslType = definitionType;
        this.buildModelType = buildModelType;
        this.path = path;
        this.transform = transform;
    }

    private static <T extends Definition<V>, V extends BuildModel> ProjectFeatureBindingDeclaration<T, V> bindingOf(
        Class<T> definitionType,
        @Nullable Class<? extends T> definitionImplementationType,
        ProjectFeatureBindingDeclaration.Safety definitionSafety,
        Path path,
        TargetTypeInformation<?> targetDefinitionType,
        Class<V> buildModelType,
        @Nullable Class<? extends V> buildModelImplementationType,
        ProjectFeatureApplyAction<T, ?, V> transform
    ) {
        return new ProjectFeatureBindingDeclaration<T, V>() {
            @Override
            public TargetTypeInformation<?> targetDefinitionType() {
                return targetDefinitionType;
            }

            @Override
            public Class<T> getDefinitionType() {
                return definitionType;
            }

            @Override
            public Optional<Class<? extends T>> getDefinitionImplementationType() {
                return Optional.ofNullable(definitionImplementationType);
            }

            @Override
            public Safety getDefinitionSafety() {
                return definitionSafety;
            }

            @Override
            public ProjectFeatureApplyAction<T, ?, V> getTransform() {
                return transform;
            }

            @Override
            public Class<V> getBuildModelType() {
                return buildModelType;
            }

            @Override
            public Optional<Class<? extends V>> getBuildModelImplementationType() {
                return Optional.ofNullable(buildModelImplementationType);
            }

            @Override
            public String getName() {
                return Objects.requireNonNull(path.getName());
            }
        };
    }

    @Override
    public DeclaredProjectFeatureBindingBuilder<T, V> withUnsafeDefinitionImplementationType(Class<? extends T> implementationType) {
        this.dslImplementationType = implementationType;
        return withUnsafeDefinition();
    }

    @Override
    public DeclaredProjectFeatureBindingBuilder<T, V> withBuildModelImplementationType(Class<? extends V> implementationType) {
        this.buildModelImplementationType = implementationType;
        return this;
    }

    @Override
    public DeclaredProjectFeatureBindingBuilder<T, V> withUnsafeDefinition() {
        this.definitionSafety = ProjectFeatureBindingDeclaration.Safety.UNSAFE;
        return this;
    }

    @Override
    public ProjectFeatureBindingDeclaration<T, V> build() {
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
            path,
            targetDefinitionType,
            buildModelType,
            Cast.uncheckedCast(buildModelImplementationType),
            Cast.uncheckedCast(transform)
        );
    }
}
