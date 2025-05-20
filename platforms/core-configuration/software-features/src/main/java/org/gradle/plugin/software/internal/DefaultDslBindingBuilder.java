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
import org.gradle.api.internal.plugins.HasBuildModel;
import org.gradle.api.internal.plugins.SoftwareFeatureBinding;
import org.gradle.api.internal.plugins.SoftwareFeatureTransform;
import org.gradle.internal.Cast;
import org.gradle.internal.Pair;
import org.gradle.util.Path;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class DefaultDslBindingBuilder<T extends HasBuildModel<V>, V extends BuildModel> implements DslBindingBuilder<T, V> {
    @Nullable private final Class<T> dslType;
    @Nullable private final Class<?> bindingTargetType;
    @Nullable private final Class<V> buildModelType;
    @Nullable private final Path path;
    @Nullable private final SoftwareFeatureTransform<?, ?, ?> transform;

    @Nullable private Class<?> dslImplementationType;
    @Nullable private Class<?> buildModelImplementationType;
    private final List<Pair<Class<?>, Class<?>>> nestedBindings = new ArrayList<>();

    public DefaultDslBindingBuilder(@Nullable Class<T> dslType, @Nullable Class<?> bindingTargetType, @Nullable Class<V> buildModelType, @Nullable Path path, @Nullable SoftwareFeatureTransform<T, ?, V> transform) {
        this.bindingTargetType = bindingTargetType;
        this.dslType = dslType;
        this.buildModelType = buildModelType;
        this.path = path;
        this.transform = transform;
    }

    private static <T extends HasBuildModel<V>, V extends BuildModel> SoftwareFeatureBinding<T, V> bindingOf(Class<T> dslType, @Nullable Class<? extends T> dslImplementationType, Path path, Class<?> bindingTargetType, Class<V> buildModelType, @Nullable Class<? extends V> buildModelImplementationType, SoftwareFeatureTransform<T, ?, V> transform, Map<Class<?>, Class<?>> nestedBindings) {
        return new SoftwareFeatureBinding<T, V>() {
            @Override
            public Class<?> getBindingTargetType() {
                return bindingTargetType;
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
            public Path getPath() {
                return path;
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
            public Map<Class<?>, Class<?>> getNestedBindings() {
                return nestedBindings;
            }
        };
    }

    @Override
    public DslBindingBuilder<T, V> withDslImplementationType(Class<? extends T> implementationType) {
        this.dslImplementationType = implementationType;
        return this;
    }

    @Override
    public DslBindingBuilder<T, V> withBuildModelImplementationType(Class<? extends V> implementationType) {
        this.buildModelImplementationType = implementationType;
        return this;
    }

    @Override
    public DslBindingBuilder<T, V> withNestedBinding(Class<?> nestedDslType, Class<?> nestedImplementationType) {
        nestedBindings.add(Pair.of(nestedDslType, nestedImplementationType));
        return this;
    }

    @Override
    public SoftwareFeatureBinding<T, V> build() {
        if (dslType == null  || buildModelType == null) {
            throw new IllegalStateException("No binding has been specified please call bind() first");
        }

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
            bindingTargetType,
            buildModelType,
            Cast.uncheckedCast(buildModelImplementationType),
            Cast.uncheckedCast(transform),
            nestedBindings.stream().collect(Collectors.toMap(Pair::getLeft, Pair::getRight)));
    }
}
