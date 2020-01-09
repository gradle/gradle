/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.provider;

import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.Transformer;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.provider.Provider;
import org.gradle.internal.Cast;
import org.gradle.internal.DisplayName;
import org.gradle.internal.state.Managed;
import org.gradle.util.GUtil;

import javax.annotation.Nullable;

/**
 * A partial {@link Provider} implementation.
 */
public abstract class AbstractMinimalProvider<T> implements ProviderInternal<T>, ScalarSupplier<T>, Managed {
    @Override
    public <S> ProviderInternal<S> map(final Transformer<? extends S, ? super T> transformer) {
        return new TransformBackedProvider<>(transformer, this);
    }

    @Override
    public <S> Provider<S> flatMap(final Transformer<? extends Provider<? extends S>, ? super T> transformer) {
        return new FlatMapProvider<>(this, transformer);
    }

    @Override
    public boolean isPresent() {
        return getOrNull() != null;
    }

    @Override
    public T get(DisplayName owner) throws IllegalStateException {
        return get();
    }

    @Override
    public T getOrElse(T defaultValue) {
        T value = getOrNull();
        if (value == null) {
            return defaultValue;
        }
        return value;
    }

    @Override
    public Provider<T> orElse(T value) {
        return new OrElseFixedValueProvider<>(this, value);
    }

    @Override
    public Provider<T> orElse(Provider<? extends T> provider) {
        return new OrElseProvider<>(this, Providers.internal(provider));
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        // When used as an input, add the producing tasks if known
        maybeVisitBuildDependencies(context);
    }

    @Override
    public boolean isValueProducedByTask() {
        return false;
    }

    @Override
    public void visitProducerTasks(Action<? super Task> visitor) {
    }

    @Override
    public boolean maybeVisitBuildDependencies(TaskDependencyResolveContext context) {
        return false;
    }

    @Override
    public ProviderInternal<T> asProvider() {
        return this;
    }

    @Override
    public ScalarSupplier<T> asSupplier(DisplayName owner, Class<? super T> targetType, ValueSanitizer<? super T> sanitizer) {
        if (getType() != null && !targetType.isAssignableFrom(getType())) {
            throw new IllegalArgumentException(String.format("Cannot set the value of %s of type %s using a provider of type %s.", owner.getDisplayName(), targetType.getName(), getType().getName()));
        } else if (getType() == null) {
            return new TypeSanitizingProvider<>(owner, sanitizer, targetType, this);
        } else {
            return this;
        }
    }

    @Override
    public ScalarSupplier<T> withFinalValue() {
        T value = getOrNull();
        return Providers.nullableValue(value);
    }

    @Override
    public String toString() {
        // NOTE: Do not realize the value of the Provider in toString().  The debugger will try to call this method and make debugging really frustrating.
        return String.format("provider(%s)", GUtil.elvis(getType(), "?"));
    }

    @Override
    public boolean isImmutable() {
        return false;
    }

    @Override
    public Class<?> publicType() {
        return Provider.class;
    }

    @Override
    public Object unpackState() {
        return getOrNull();
    }

    @Override
    public int getFactoryId() {
        return ManagedFactories.ProviderManagedFactory.FACTORY_ID;
    }

    private static class FlatMapProvider<S, T> extends AbstractMinimalProvider<S> {
        private final Provider<? extends T> provider;
        private final Transformer<? extends Provider<? extends S>, ? super T> transformer;

        FlatMapProvider(ProviderInternal<? extends T> provider, Transformer<? extends Provider<? extends S>, ? super T> transformer) {
            this.provider = provider;
            this.transformer = transformer;
        }

        @Nullable
        @Override
        public Class<S> getType() {
            return null;
        }

        @Override
        public boolean isPresent() {
            T value = provider.getOrNull();
            if (value == null) {
                return false;
            }
            return map(value).isPresent();
        }

        @Override
        public S get() {
            T value = provider.get();
            return map(value).get();
        }

        @Nullable
        @Override
        public S getOrNull() {
            T value = provider.getOrNull();
            if (value == null) {
                return null;
            }
            return map(value).getOrNull();
        }

        private Provider<? extends S> map(T value) {
            Provider<? extends S> result = transformer.transform(value);
            if (result == null) {
                throw new IllegalStateException(Providers.NULL_TRANSFORMER_RESULT);
            }
            return result;
        }

        @Override
        public boolean maybeVisitBuildDependencies(TaskDependencyResolveContext context) {
            return Providers.internal(map(provider.get())).maybeVisitBuildDependencies(context);
        }

        @Override
        public String toString() {
            return "flatmap(" + provider + ")";
        }
    }

    private static class OrElseFixedValueProvider<T> extends AbstractProviderWithValue<T> {
        private final ProviderInternal<T> provider;
        private final T value;

        public OrElseFixedValueProvider(ProviderInternal<T> provider, T value) {
            this.provider = provider;
            this.value = value;
        }

        @Nullable
        @Override
        public Class<T> getType() {
            return provider.getType();
        }

        @Override
        public boolean maybeVisitBuildDependencies(TaskDependencyResolveContext context) {
            if (provider.isPresent()) {
                return provider.maybeVisitBuildDependencies(context);
            } else {
                return super.maybeVisitBuildDependencies(context);
            }
        }

        @Override
        public T get() {
            T value = provider.getOrNull();
            return value != null ? value : this.value;
        }
    }

    private static class OrElseProvider<T> extends AbstractReadOnlyProvider<T> {
        private final ProviderInternal<T> left;
        private final ProviderInternal<? extends T> right;

        public OrElseProvider(ProviderInternal<T> left, ProviderInternal<? extends T> right) {
            this.left = left;
            this.right = right;
        }

        @Nullable
        @Override
        public Class<T> getType() {
            return left.getType();
        }

        @Override
        public boolean maybeVisitBuildDependencies(TaskDependencyResolveContext context) {
            if (left.isPresent()) {
                return left.maybeVisitBuildDependencies(context);
            } else {
                return right.maybeVisitBuildDependencies(context);
            }
        }

        @Override
        public boolean isPresent() {
            return left.isPresent() || right.isPresent();
        }

        @Nullable
        @Override
        public T getOrNull() {
            T value = left.getOrNull();
            if (value == null) {
                value = right.getOrNull();
            }
            return value;
        }
    }

    private static class TypeSanitizingProvider<T> extends AbstractMappingProvider<T, T> {
        private final DisplayName owner;
        private final ValueSanitizer<? super T> sanitizer;
        private final Class<? super T> targetType;

        public TypeSanitizingProvider(DisplayName owner, ValueSanitizer<? super T> sanitizer, Class<? super T> targetType, ProviderInternal<? extends T> delegate) {
            super(Cast.uncheckedNonnullCast(targetType), delegate);
            this.owner = owner;
            this.sanitizer = sanitizer;
            this.targetType = targetType;
        }

        @Override
        protected T mapValue(T v) {
            v = Cast.uncheckedCast(sanitizer.sanitize(v));
            if (targetType.isInstance(v)) {
                return v;
            }
            throw new IllegalArgumentException(String.format("Cannot get the value of %s of type %s as the provider associated with this property returned a value of type %s.", owner.getDisplayName(), targetType.getName(), v.getClass().getName()));
        }
    }
}
