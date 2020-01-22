/*
 * Copyright 2019 the original author or authors.
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

import com.google.common.collect.ImmutableList;
import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.internal.Cast;
import org.gradle.internal.DisplayName;

import javax.annotation.Nullable;
import java.util.List;

public interface ValueSupplier {
    /**
     * See {@link ProviderInternal#maybeVisitBuildDependencies(TaskDependencyResolveContext)}.
     */
    boolean maybeVisitBuildDependencies(TaskDependencyResolveContext context);

    /**
     * See {@link ProviderInternal#visitProducerTasks(Action)}.
     */
    void visitProducerTasks(Action<? super Task> visitor);

    /**
     * See {@link ProviderInternal#isValueProducedByTask()}.
     */
    boolean isValueProducedByTask();

    boolean isPresent();

    interface Value<T> {
        Value<Object> MISSING = new ScalarSupplier.Missing<>();
        Value<Void> SUCCESS = new Present<>(null);

        static <T> Value<? extends T> ofNullable(@Nullable T value) {
            if (value == null) {
                return MISSING.asType();
            }
            return new Present<T>(value);
        }

        static <T> Value<T> missing() {
            return MISSING.asType();
        }

        static <T> Value<T> of(T value) {
            return new Present<>(value);
        }

        static Value<Void> present() {
            return SUCCESS;
        }

        T get() throws IllegalStateException;

        @Nullable
        T orNull();

        <S> S orElse(S defaultValue);

        // Only populated when value is missing
        List<DisplayName> getPathToOrigin();

        boolean isMissing();

        Value<T> pushWhenMissing(@Nullable DisplayName displayName);

        <S> Value<S> asType();
    }

    class Present<T> implements Value<T> {
        private final T result;

        public Present(T result) {
            this.result = result;
        }

        @Override
        public boolean isMissing() {
            return false;
        }

        @Override
        public T get() throws IllegalStateException {
            return result;
        }

        @Override
        public T orNull() {
            return result;
        }

        @Override
        public <S> S orElse(S defaultValue) {
            return Cast.uncheckedCast(result);
        }

        @Override
        public Value<T> pushWhenMissing(@Nullable DisplayName displayName) {
            return this;
        }

        @Override
        public <S> Value<S> asType() {
            throw new IllegalStateException();
        }

        @Override
        public List<DisplayName> getPathToOrigin() {
            throw new IllegalStateException();
        }
    }

    class Missing<T> implements Value<T> {
        private final List<DisplayName> path;

        public Missing() {
            this.path = ImmutableList.of();
        }

        public Missing(List<DisplayName> path) {
            this.path = path;
        }

        @Override
        public boolean isMissing() {
            return true;
        }

        @Override
        public T get() throws IllegalStateException {
            throw new IllegalStateException();
        }

        @Override
        public T orNull() {
            return null;
        }

        @Override
        public <S> S orElse(S defaultValue) {
            return defaultValue;
        }

        @Override
        public List<DisplayName> getPathToOrigin() {
            return path;
        }

        @Override
        public <S> Value<S> asType() {
            return Cast.uncheckedCast(this);
        }

        @Override
        public Value<T> pushWhenMissing(@Nullable DisplayName displayName) {
            if (displayName == null) {
                return this;
            }
            ImmutableList.Builder<DisplayName> builder = ImmutableList.builderWithExpectedSize(path.size() + 1);
            builder.add(displayName);
            builder.addAll(path);
            return new Missing<T>(builder.build());
        }
    }
}
