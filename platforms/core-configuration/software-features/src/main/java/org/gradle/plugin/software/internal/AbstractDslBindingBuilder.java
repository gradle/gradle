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

import org.gradle.api.internal.plugins.DslBindingBuilder;
import org.gradle.api.internal.plugins.SoftwareFeatureBinding;
import org.gradle.api.internal.plugins.SoftwareFeatureTransform;
import org.gradle.internal.Cast;
import org.gradle.util.Path;

import javax.annotation.Nullable;
import java.util.Optional;

abstract public class AbstractDslBindingBuilder implements DslBindingBuilder {
    @Nullable protected Class<?> bindingTargetType;
    @Nullable protected Class<?> dslType;
    @Nullable protected Class<?> buildModelType;
    @Nullable protected Path path;
    @Nullable private Class<?> dslImplementationType;
    @Nullable private Class<?> buildModelImplementationType;
    @Nullable protected SoftwareFeatureTransform<?, ?, ?> transform;

    private static <T, U> SoftwareFeatureBinding bindingOf(Class<T> dslType, @Nullable Class<? extends T> dslImplementationType, Path path, Class<?> bindingTargetType, Class<U> buildModelType, @Nullable Class<? extends U> buildModelImplementationType, SoftwareFeatureTransform<T, ?, ?> transform) {
        return new SoftwareFeatureBinding() {
            @Override
            public Class<?> getBindingTargetType() {
                return bindingTargetType;
            }

            @Override
            public Class<T> getDslType() {
                return dslType;
            }

            @Override
            public Optional<Class<?>> getDslImplementationType() {
                return Optional.ofNullable(dslImplementationType);
            }

            @Override
            public Path getPath() {
                return path;
            }

            @Override
            public SoftwareFeatureTransform<T, ?, ?> getTransform() {
                return transform;
            }

            @Override
            public Class<?> getBuildModelType() {
                return buildModelType;
            }

            @Override
            public Optional<Class<?>> getBuildModelImplementationType() {
                return Optional.ofNullable(buildModelImplementationType);
            }
        };
    }

    @Override
    public <V> DslBindingBuilder withDslImplementationType(Class<V> implementationType) {
        this.dslImplementationType = implementationType;
        return this;
    }

    @Override
    public <V> DslBindingBuilder withBuildModelImplementationType(Class<V> implementationType) {
        this.buildModelImplementationType = implementationType;
        return this;
    }

    @Override
    public SoftwareFeatureBinding build() {
        if (dslType == null  || buildModelType == null) {
            throw new IllegalStateException("No binding has been specified please call bind() first");
        }

        if (dslImplementationType != null && !dslType.isAssignableFrom(dslImplementationType)) {
            throw new IllegalArgumentException("Implementation type " + dslImplementationType + " is not a subtype of dsl type " + dslType);
        }

        if (buildModelImplementationType != null && !buildModelType.isAssignableFrom(buildModelImplementationType)) {
            throw new IllegalArgumentException("Implementation type " + buildModelImplementationType + " is not a subtype of build model type " + buildModelType);
        }

        return AbstractDslBindingBuilder.bindingOf(dslType, Cast.uncheckedCast(dslImplementationType), path, bindingTargetType, buildModelType, Cast.uncheckedCast(buildModelImplementationType), Cast.uncheckedCast(transform));
    }
}
