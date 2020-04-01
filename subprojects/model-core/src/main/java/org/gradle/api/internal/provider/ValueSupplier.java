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

/**
 * Encapsulates the production of some value.
 */
public interface ValueSupplier {
    /**
     * Visits the build dependencies of this supplier, if possible.
     *
     * @return true if the dependencies are konwn and have been added (possibly none), false if the build dependencies are unknown.
     */
    boolean maybeVisitBuildDependencies(TaskDependencyResolveContext context);

    /**
     * Visits the tasks that produce the <em>content</em> of the value of this supplier, if any.
     *
     * At some point, this method can {@link #maybeVisitBuildDependencies(TaskDependencyResolveContext)} could be merged.
     */
    void visitProducerTasks(Action<? super Task> visitor);

    /**
     * Returns true when the <em>value</em> of this supplier is produced by a task. The <em>value</em> is the object returned the query methods.
     * This is distinct from the <em>content</em>, which is the state of the value or the thing that the value points to. For example, for a file property, the file path
     * represents the value of the property, and the content of the file on the file system represents the content of the property.
     *
     * <p>Note that a task producing the value of this supplier is not necessarily the same as a task producing the <em>content</em> of
     * the value of this supplier.
     */
    boolean isValueProducedByTask();

    boolean isPresent();

    /**
     * Carries either a value or some diagnostic information about where the value would have come from, had it been present.
     */
    interface Value<T> {
        Value<Object> MISSING = new Missing<>();
        Value<Void> SUCCESS = new Present<>(null);

        static <T> Value<T> ofNullable(@Nullable T value) {
            if (value == null) {
                return MISSING.asType();
            }
            return new Present<>(value);
        }

        static <T> Value<T> missing() {
            return MISSING.asType();
        }

        static <T> Value<T> of(T value) {
            assert value != null;
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

        <S> Value<S> asType();

        Value<T> pushWhenMissing(@Nullable DisplayName displayName);

        Value<T> addPathsFrom(Value<?> rightValue);
    }

    class Present<T> implements Value<T> {
        private final T result;

        private Present(T result) {
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

        @Override
        public Value<T> addPathsFrom(Value<?> rightValue) {
            throw new IllegalStateException();
        }
    }

    class Missing<T> implements Value<T> {
        private final List<DisplayName> path;

        private Missing() {
            this.path = ImmutableList.of();
        }

        private Missing(List<DisplayName> path) {
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
            return new Missing<>(builder.build());
        }

        @Override
        public Value<T> addPathsFrom(Value<?> rightValue) {
            if (path.isEmpty()) {
                return rightValue.asType();
            }
            Missing<?> other = (Missing<?>) rightValue;
            if (other.path.isEmpty()) {
                return this;
            }
            ImmutableList.Builder<DisplayName> builder = ImmutableList.builderWithExpectedSize(path.size() + other.path.size());
            builder.addAll(path);
            builder.addAll(other.path);
            return new Missing<>(builder.build());
        }
    }

    /**
     * Represents either a missing value, a fixed value with fixed contents, a fixed value with changing contents, or a changing value (with changing contents).
     */
    abstract class ExecutionTimeValue<T> {
        public boolean isMissing() {
            return false;
        }

        public boolean isFixedValue() {
            return false;
        }

        public boolean isChangingValue() {
            return false;
        }

        /**
         * A fixed value may have changing contents. For example, a task output file whose location is known at configuration time.
         */
        public boolean hasChangingContent() {
            return false;
        }

        public T getFixedValue() throws IllegalStateException {
            throw new IllegalStateException();
        }

        public ProviderInternal<T> getChangingValue() throws IllegalStateException {
            throw new IllegalStateException();
        }

        public Value<T> toValue() throws IllegalStateException {
            throw new IllegalStateException();
        }

        public abstract ProviderInternal<T> toProvider();

        public abstract ExecutionTimeValue<T> withChangingContent();

        public static <T> ExecutionTimeValue<T> missing() {
            return new MissingExecutionTimeValue<>();
        }

        public static <T> ExecutionTimeValue<T> fixedValue(T value) {
            assert value != null;
            return new FixedExecutionTimeValue<>(value, false);
        }

        public static <T> ExecutionTimeValue<T> value(Value<T> value) {
            if (value.isMissing()) {
                return missing();
            } else {
                return fixedValue(value.get());
            }
        }

        public static <T> ExecutionTimeValue<T> changingValue(ProviderInternal<T> provider) {
            return new ChangingExecutionTimeValue<>(provider);
        }
    }

    class MissingExecutionTimeValue<T> extends ExecutionTimeValue<T> {
        @Override
        public boolean isMissing() {
            return true;
        }

        @Override
        public ProviderInternal<T> toProvider() {
            return Providers.notDefined();
        }

        @Override
        public ExecutionTimeValue<T> withChangingContent() {
            return this;
        }

        @Override
        public Value<T> toValue() {
            return Value.missing();
        }
    }

    class FixedExecutionTimeValue<T> extends ExecutionTimeValue<T> {
        private final T value;
        private final boolean changingContent;

        private FixedExecutionTimeValue(T value, boolean changingContent) {
            this.value = value;
            this.changingContent = changingContent;
        }

        @Override
        public boolean isFixedValue() {
            return true;
        }

        @Override
        public boolean hasChangingContent() {
            return changingContent;
        }

        @Override
        public T getFixedValue() {
            return value;
        }

        @Override
        public Value<T> toValue() {
            return Value.of(value);
        }

        @Override
        public ProviderInternal<T> toProvider() {
            if (changingContent) {
                return new Providers.FixedValueWithChangingContentProvider<>(value);
            }
            return Providers.of(value);
        }

        @Override
        public ExecutionTimeValue<T> withChangingContent() {
            return new FixedExecutionTimeValue<>(value, true);
        }
    }

    class ChangingExecutionTimeValue<T> extends ExecutionTimeValue<T> {
        private final ProviderInternal<T> provider;

        private ChangingExecutionTimeValue(ProviderInternal<T> provider) {
            this.provider = provider;
        }

        @Override
        public boolean isChangingValue() {
            return true;
        }

        @Override
        public boolean hasChangingContent() {
            return true;
        }

        @Override
        public ProviderInternal<T> getChangingValue() {
            return provider;
        }

        @Override
        public ProviderInternal<T> toProvider() {
            return provider;
        }

        @Override
        public ExecutionTimeValue<T> withChangingContent() {
            return this;
        }
    }
}
