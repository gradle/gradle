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
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.logging.text.TreeFormatter;
import org.gradle.internal.state.Managed;
import org.gradle.util.GUtil;

import javax.annotation.Nullable;

/**
 * A partial {@link Provider} implementation. Subclasses need to implement {@link ProviderInternal#getType()} and {@link AbstractMinimalProvider#calculateOwnValue()}.
 */
public abstract class AbstractMinimalProvider<T> implements ProviderInternal<T>, ScalarSupplier<T>, Managed {
    private static final DisplayName DEFAULT_DISPLAY_NAME = Describables.of("this provider");

    @Override
    public <S> ProviderInternal<S> map(final Transformer<? extends S, ? super T> transformer) {
        return new TransformBackedProvider<>(transformer, this);
    }

    @Override
    public <S> Provider<S> flatMap(final Transformer<? extends Provider<? extends S>, ? super T> transformer) {
        return new FlatMapProvider<>(this, transformer);
    }

    /**
     * Returns the human consumable display name for this provider, or null if this is not known.
     */
    @Nullable
    protected DisplayName getDeclaredDisplayName() {
        return null;
    }

    /**
     * Returns a display name for this provider, using a default if this is not known.
     */
    protected DisplayName getDisplayName() {
        DisplayName displayName = getDeclaredDisplayName();
        if (displayName == null) {
            return DEFAULT_DISPLAY_NAME;
        }
        return displayName;
    }

    protected DisplayName getTypedDisplayName() {
        return DEFAULT_DISPLAY_NAME;
    }

    protected abstract ScalarSupplier.Value<? extends T> calculateOwnValue();

    @Override
    public boolean isPresent() {
        return !calculateOwnValue().isMissing();
    }

    @Override
    public T get() {
        Value<? extends T> value = calculateOwnValue();
        if (value.isMissing()) {
            TreeFormatter formatter = new TreeFormatter();
            formatter.node("Cannot query the value of ").append(getDisplayName().getDisplayName()).append(" because it has no value available.");
            if (!value.getPathToOrigin().isEmpty()) {
                formatter.node("The value of ").append(getTypedDisplayName().getDisplayName()).append(" is derived from");
                formatter.startChildren();
                for (DisplayName displayName : value.getPathToOrigin()) {
                    formatter.node(displayName.getDisplayName());
                }
                formatter.endChildren();
            }
            throw new MissingValueException(formatter.toString());
        }
        return value.get();
    }

    @Override
    public T getOrNull() {
        return calculateOwnValue().orNull();
    }

    @Override
    public T getOrElse(T defaultValue) {
        return calculateOwnValue().orElse(defaultValue);
    }

    @Override
    public Value<? extends T> calculateValue() {
        return calculateOwnValue().pushWhenMissing(getDeclaredDisplayName());
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
        private final ProviderInternal<? extends T> provider;
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
        protected Value<? extends S> calculateOwnValue() {
            Value<? extends T> value = provider.calculateValue();
            if (value.isMissing()) {
                return value.asType();
            }
            return map(value.get()).calculateValue();
        }

        private ProviderInternal<? extends S> map(T value) {
            Provider<? extends S> result = transformer.transform(value);
            if (result == null) {
                throw new IllegalStateException(Providers.NULL_TRANSFORMER_RESULT);
            }
            return Providers.internal(result);
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

    private static class OrElseProvider<T> extends AbstractMinimalProvider<T> {
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

        @Override
        protected Value<? extends T> calculateOwnValue() {
            Value<? extends T> leftValue = left.calculateValue();
            if (!leftValue.isMissing()) {
                return leftValue;
            }
            Value<? extends T> rightValue = right.calculateValue();
            if (!rightValue.isMissing()) {
                return rightValue;
            }
            return leftValue.addPathsFrom(rightValue);
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
