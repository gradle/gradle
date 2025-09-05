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
import org.gradle.api.internal.plugins.DslBindingBuilder;
import org.gradle.api.internal.plugins.DslBindingBuilderInternal;
import org.gradle.api.internal.plugins.HasBuildModel;
import org.gradle.api.internal.plugins.SoftwareFeatureBinding;
import org.gradle.api.internal.plugins.SoftwareFeatureTransform;
import org.gradle.api.internal.plugins.TargetTypeInformation;
import org.gradle.internal.Cast;
import org.gradle.util.Path;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

@NullMarked
public class DefaultDslBindingBuilder<T extends HasBuildModel<V>, V extends BuildModel> implements DslBindingBuilderInternal<T, V> {
    private final Class<T> dslType;
    private final TargetTypeInformation<?> targetDefinitionType;
    private final Class<V> buildModelType;
    private final Path path;
    private final SoftwareFeatureTransform<?, ?, ?> transform;

    @Nullable private Class<?> dslImplementationType;
    @Nullable private Class<?> buildModelImplementationType;

    public DefaultDslBindingBuilder(
        Class<T> dslType,
        Class<V> buildModelType,
        TargetTypeInformation<?> targetDefinitionType,
        Path path,
        SoftwareFeatureTransform<T, V, ?> transform
    ) {
        this.targetDefinitionType = targetDefinitionType;
        this.dslType = dslType;
        this.buildModelType = buildModelType;
        this.path = path;
        this.transform = transform;
    }

    private static <T extends HasBuildModel<V>, V extends BuildModel> SoftwareFeatureBinding<T, V> bindingOf(
        Class<T> dslType,
        @Nullable Class<? extends T> dslImplementationType,
        Path path,
        TargetTypeInformation<?> targetDefinitionType,
        Class<V> buildModelType,
        @Nullable Class<? extends V> buildModelImplementationType,
        SoftwareFeatureTransform<T, ?, V> transform
    ) {
        return new SoftwareFeatureBinding<T, V>() {
            @Override
            public TargetTypeInformation<?> targetDefinitionType() {
                return targetDefinitionType;
            }

            @Override
            public Class<T> getDslType() {
                return dslType;
            }

            @Override
            public Optional<Class<? extends T>> getDslImplementationType() {
                return Optional.ofNullable(dslImplementationType);
            }

            @Override
            public SoftwareFeatureTransform<T, ?, V> getTransform() {
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
    public DslBindingBuilder<T, V> withDefinitionImplementationType(Class<? extends T> implementationType) {
        this.dslImplementationType = implementationType;
        return this;
    }

    @Override
    public DslBindingBuilder<T, V> withBuildModelImplementationType(Class<? extends V> implementationType) {
        this.buildModelImplementationType = implementationType;
        return this;
    }

    @Override
    public SoftwareFeatureBinding<T, V> build() {
        if (dslImplementationType != null && !dslType.isAssignableFrom(dslImplementationType)) {
            throw new IllegalArgumentException("Implementation type " + dslImplementationType + " is not a subtype of dsl type " + dslType);
        }

        if (buildModelImplementationType != null && !buildModelType.isAssignableFrom(buildModelImplementationType)) {
            throw new IllegalArgumentException("Implementation type " + buildModelImplementationType + " is not a subtype of build model type " + buildModelType);
        }

        return DefaultDslBindingBuilder.bindingOf(
            dslType,
            Cast.uncheckedCast(dslImplementationType),
            path,
            targetDefinitionType,
            buildModelType,
            Cast.uncheckedCast(buildModelImplementationType),
            Cast.uncheckedCast(transform)
        );
    }
}
