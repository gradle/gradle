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
import org.gradle.internal.Cast;
import org.gradle.internal.DisplayName;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Supplies zero or one value of type {@link T}.
 */
public interface ScalarSupplier<T> extends ValueSupplier {
    boolean isPresent();

    /**
     * Calculates the value of this supplier.
     */
    Value<? extends T> calculateValue();

    ProviderInternal<T> asProvider();

    ScalarSupplier<T> withFinalValue();

    interface Value<T> {
        static <T> Value<? extends T> ofNullable(@Nullable T value) {
            if (value == null) {
                return new Missing<T>();
            }
            return new Success<T>(value);
        }

        T get() throws IllegalStateException;

        // Only populated when value is missing
        List<DisplayName> getPathToOrigin();

        boolean isMissing();

        Value<T> pushWhenMissing(DisplayName displayName);

        @Nullable
        T orNull();

        <S> S orElse(S defaultValue);
    }

    class Success<T> implements Value<T> {
        private final T result;

        public Success(T result) {
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
        public Value<T> pushWhenMissing(DisplayName displayName) {
            return this;
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
        public Value<T> pushWhenMissing(DisplayName displayName) {
            ImmutableList.Builder<DisplayName> builder = ImmutableList.builderWithExpectedSize(path.size() + 1);
            builder.add(displayName);
            builder.addAll(path);
            return new Missing<T>(builder.build());
        }
    }
}
