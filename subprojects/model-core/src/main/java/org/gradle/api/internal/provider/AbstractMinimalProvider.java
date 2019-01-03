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

import org.gradle.api.Transformer;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.provider.Provider;
import org.gradle.util.GUtil;

import javax.annotation.Nullable;
import java.util.Collection;

public abstract class AbstractMinimalProvider<T> implements ProviderInternal<T> {
    @Override
    public <S> ProviderInternal<S> map(final Transformer<? extends S, ? super T> transformer) {
        return new TransformBackedProvider<S, T>(transformer, this);
    }

    @Override
    public <S> Provider<S> flatMap(final Transformer<? extends Provider<? extends S>, ? super T> transformer) {
        return new FlatMapProvider<S, T>(this, transformer);
    }

    @Override
    public boolean isPresent() {
        return getOrNull() != null;
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
    public boolean maybeVisitBuildDependencies(TaskDependencyResolveContext context) {
        return false;
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        if (!maybeVisitBuildDependencies(context)) {
            T value = get();
            // TODO - should add methods to the context that take care of this
            if (value instanceof Collection) {
                Collection<?> items = (Collection<?>) value;
                for (Object item : items) {
                    context.maybeAdd(item);
                }
            } else {
                context.maybeAdd(value);
            }
        }
    }

    @Override
    public ProviderInternal<T> withFinalValue() {
        T value = getOrNull();
        if (value == null) {
            return Providers.notDefined();
        }
        return Providers.of(value);
    }

    @Override
    public String toString() {
        // NOTE: Do not realize the value of the Provider in toString().  The debugger will try to call this method and make debugging really frustrating.
        return String.format("provider(%s)", GUtil.elvis(getType(), "?"));
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
}
