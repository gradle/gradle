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

package org.gradle.api.internal.tasks;

import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.Transformer;
import org.gradle.api.internal.provider.AbstractMinimalProvider;
import org.gradle.api.internal.provider.ProviderInternal;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskCollection;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.internal.Cast;
import org.gradle.internal.reflect.Instantiator;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

public class DefaultTaskShadowingRegistry implements TaskShadowingRegistry {
    private final Map<Class<?>, Class<?>> shadowTypes = new ConcurrentHashMap<>();
    private final Map<Class<?>, BiFunction<Object, Class<?>, Object>> wrappers = new ConcurrentHashMap<>();
    private final Instantiator instantiator;

    public DefaultTaskShadowingRegistry(Instantiator instantiator) {
        this.instantiator = instantiator;
    }

    @Override
    public <T extends Task, S extends Task> void registerShadowing(Class<T> publicType, Class<S> shadowType, BiFunction<Object, Class<T>, Object> wrapper) {
        shadowTypes.put(publicType, shadowType);
        wrappers.put(publicType, Cast.uncheckedCast(wrapper));
    }

    @Override
    public <T extends Task> Class<T> getShadowType(Class<T> type) {
        Class<?> shadowType = shadowTypes.get(type);
        if (shadowType != null) {
            return Cast.uncheckedCast(shadowType);
        }
        return type;
    }

    @Override
    public <T extends Task> T maybeWrap(T task, Class<T> requestedType) {
        BiFunction<Object, Class<?>, Object> wrapper = wrappers.get(requestedType);
        if (wrapper != null) {
            return Cast.uncheckedCast(wrapper.apply(task, requestedType));
        }
        return task;
    }

    @Override
    public <T extends Task> TaskProvider<T> maybeWrapProvider(TaskProvider<T> provider, Class<T> requestedType) {
        BiFunction<Object, Class<?>, Object> wrapper = wrappers.get(requestedType);
        if (wrapper != null) {
            return Cast.uncheckedCast(instantiator.newInstance(DelegatingTaskProvider.class, provider, requestedType, wrapper));
        }
        return provider;
    }

    @Override
    public <T extends Task> TaskCollection<T> maybeWrapCollection(TaskCollection<T> collection, Class<T> requestedType) {
        // BiFunction<Object, Class<?>, Object> wrapper = wrappers.get(requestedType);
        // TODO
        // if (wrapper != null) {
        // }
        return collection;
    }

    @Override
    public <T extends Task> Action<? super T> maybeWrapAction(Action<? super T> action, Class<T> requestedType) {
        BiFunction<Object, Class<?>, Object> wrapper = wrappers.get(requestedType);
        if (wrapper != null) {
            return (Action<T>) t -> action.execute(Cast.uncheckedCast(wrapper.apply(t, requestedType)));
        }
        return action;
    }

    public static class DelegatingTaskProvider<T extends Task> extends AbstractMinimalProvider<T> implements TaskProvider<T> {

        private final TaskProvider<T> provider;
        private final Class<T> requestedType;
        private final BiFunction<Object, Class<?>, Object> wrapper;

        public DelegatingTaskProvider(TaskProvider<T> provider, Class<T> requestedType, BiFunction<Object, Class<?>, Object> wrapper) {
            this.requestedType = requestedType;
            this.provider = provider;
            this.wrapper = wrapper;
        }

        @Override
        protected Value<? extends T> calculateOwnValue(ValueConsumer consumer) {
            Value<?> value = ((AbstractMinimalProvider<?>) provider).calculateValue(consumer);
            if (value.isMissing()) {
                return Cast.uncheckedCast(value);
            }
            Object task = value.get();
            return Value.of(Cast.uncheckedCast(wrapper.apply(task, requestedType)));
        }

        @Override
        public void configure(Action<? super T> action) {
            provider.configure(t -> action.execute(Cast.uncheckedCast(wrapper.apply(t, requestedType))));
        }

        @Override
        public String getName() {
            return provider.getName();
        }

        @Override
        public T get() {
            return Cast.uncheckedCast(wrapper.apply(provider.get(), requestedType));
        }

        @Override
        public T getOrNull() {
            T task = provider.getOrNull();
            return task == null ? null : Cast.uncheckedCast(wrapper.apply(task, requestedType));
        }

        @Override
        public T getOrElse(T defaultValue) {
            return Cast.uncheckedCast(wrapper.apply(provider.getOrElse(defaultValue), requestedType));
        }

        @Override
        public @Nullable Class<T> getType() {
            return requestedType;
        }

        @Override
        public <S> ProviderInternal<S> map(Transformer<? extends S, ? super T> transformer) {
            return Cast.uncheckedCast(provider.map(t -> transformer.transform(Cast.uncheckedCast(wrapper.apply(t, requestedType)))));
        }

        @Override
        public ProviderInternal<T> filter(Spec<? super T> spec) {
            return Cast.uncheckedCast(provider.filter(t -> spec.isSatisfiedBy(Cast.uncheckedCast(wrapper.apply(t, requestedType)))));
        }

        @Override
        public <S> Provider<S> flatMap(Transformer<? extends @Nullable Provider<? extends S>, ? super T> transformer) {
            return provider.flatMap(t -> transformer.transform(Cast.uncheckedCast(wrapper.apply(t, requestedType))));
        }

        @Override
        public boolean isPresent() {
            return provider.isPresent();
        }

        @Override
        public Provider<T> orElse(T value) {
            return Cast.uncheckedCast(provider.map(t -> Cast.uncheckedCast(wrapper.apply(t, requestedType))).orElse(value));
        }

        @Override
        public Provider<T> orElse(Provider<? extends T> orElseProvider) {
            return Cast.uncheckedCast(provider.map(t -> Cast.uncheckedCast(wrapper.apply(t, requestedType))).orElse(Cast.uncheckedCast(orElseProvider)));
        }

        @Override
        public <U, R> Provider<R> zip(Provider<U> right, BiFunction<? super T, ? super U, ? extends R> combiner) {
            return provider.zip(right, (t, u) -> Cast.uncheckedCast(combiner.apply(Cast.uncheckedCast(wrapper.apply(t, requestedType)), Cast.uncheckedCast(u))));
        }
    }
}
