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
    @Nullable private Class<?> implementationType;
    @Nullable protected SoftwareFeatureTransform<?, ?, ?> transform;

    private static <T> SoftwareFeatureBinding bindingOf(Class<T> dslType, @Nullable Class<? extends T> implementationType, Path path, Class<?> bindingTargetType, Class<?> buildModelType, SoftwareFeatureTransform<T, ?, ?> transform) {
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
            public Optional<Class<?>> getImplementationType() {
                return Optional.ofNullable(implementationType);
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
        };
    }

    @Override
    public <V> DslBindingBuilder withImplementationType(Class<V> implementationType) {
        this.implementationType = implementationType;
        return this;
    }

    @Override
    public SoftwareFeatureBinding build() {
        if (dslType == null) {
            throw new IllegalStateException("No binding has been specified please call bind() first");
        }

        if (implementationType != null && !dslType.isAssignableFrom(implementationType)) {
            throw new IllegalArgumentException("Implementation type " + implementationType + " is not a subtype of dsl type " + dslType);
        }
        return AbstractDslBindingBuilder.bindingOf(dslType, Cast.uncheckedCast(implementationType), path, bindingTargetType, buildModelType, Cast.uncheckedCast(transform));
    }
}
