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
import org.gradle.api.Transformer;
import org.gradle.api.internal.tasks.TaskDependencyContainer;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.internal.Cast;
import org.gradle.internal.DisplayName;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Encapsulates the production of some value by some producer.
 * <p>
 * All providers implement this interface, but this interface may be implemented whenever
 * lazy evaluation is required.
 * </p>
 */
public interface ValueSupplier {
    /**
     * Visits the producer of the value for this supplier. This might be one or more tasks, some external location, nothing (for a fixed value) or might unknown.
     */
    ValueProducer getProducer();

    boolean calculatePresence(ValueConsumer consumer);

    enum ValueConsumer {
        DisallowUnsafeRead, IgnoreUnsafeRead
    }

    /**
     * Carries information about the producer of a value.
     */
    @SuppressWarnings("ClassInitializationDeadlock")
    interface ValueProducer extends TaskDependencyContainer {
        NoProducer NO_PRODUCER = new NoProducer();
        UnknownProducer UNKNOWN_PRODUCER = new UnknownProducer();

        default boolean isKnown() {
            return true;
        }

        @Override
        default void visitDependencies(TaskDependencyResolveContext context) {
            visitProducerTasks(context);
        }

        void visitProducerTasks(Action<? super Task> visitor);

        default void visitContentProducerTasks(Action<? super Task> visitor) {
            visitProducerTasks(visitor);
        }

        default ValueProducer plus(ValueProducer producer) {
            if (this == NO_PRODUCER) {
                return producer;
            }
            if (producer == NO_PRODUCER) {
                return this;
            }
            if (producer == this) {
                return this;
            }
            return new PlusProducer(this, producer);
        }

        static ValueProducer noProducer() {
            return NO_PRODUCER;
        }

        static ValueProducer unknown() {
            return UNKNOWN_PRODUCER;
        }

        static ValueProducer externalValue() {
            // At the moment, external values do not differ from values without the producer.
            return NO_PRODUCER;
        }

        /**
         * Value and its contents are produced by task.
         */
        static ValueProducer task(Task task) {
            return new TaskProducer(task, true);
        }

        /**
         * Value is produced from the properties of the task, and carries an implicit dependency on the task
         */
        static ValueProducer taskState(Task task) {
            return new TaskProducer(task, false);
        }
    }

    class TaskProducer implements ValueProducer {
        private final Task task;
        private final boolean content;

        public TaskProducer(Task task, boolean content) {
            this.task = task;
            this.content = content;
        }

        @Override
        public void visitProducerTasks(Action<? super Task> visitor) {
            visitor.execute(task);
        }

        @Override
        public void visitContentProducerTasks(Action<? super Task> visitor) {
            if (content) {
                visitor.execute(task);
            }
        }
    }

    class PlusProducer implements ValueProducer {
        private final ValueProducer left;
        private final ValueProducer right;

        public PlusProducer(ValueProducer left, ValueProducer right) {
            this.left = left;
            this.right = right;
        }

        @Override
        public boolean isKnown() {
            return left.isKnown() || right.isKnown();
        }

        @Override
        public void visitProducerTasks(Action<? super Task> visitor) {
            left.visitProducerTasks(visitor);
            right.visitProducerTasks(visitor);
        }
    }

    class UnknownProducer implements ValueProducer {
        @Override
        public boolean isKnown() {
            return false;
        }

        @Override
        public void visitProducerTasks(Action<? super Task> visitor) {
        }
    }

    class NoProducer implements ValueProducer {
        @Override
        public void visitProducerTasks(Action<? super Task> visitor) {
        }
    }

    /**
     * An action that can be {@link ProviderInternal#withSideEffect(SideEffect) attached}
     * to a {@code Provider} to be executed when the underlying value is accessed.
     */
    interface SideEffect<T> extends Serializable {

        /**
         * Executes this side effect against the provided value.
         */
        void execute(T t);

        /**
         * Creates a new side effect that ignores its argument
         * and instead always executes against the given {@code value}.
         */
        @Nullable
        static <T, A> SideEffect<T> fixed(A value, @Nullable SideEffect<A> sideEffect) {
            return FixedSideEffect.of(value, sideEffect);
        }

        /**
         * Extracts the side effect from the {@code value} if any, and fixes it on that value.
         * <p>
         * If the value is {@link Value#isMissing() missing} then null is returned.
         *
         * @see #fixed
         */
        @Nullable
        static <T, A> SideEffect<T> fixedFrom(Value<A> value) {
            if (value.isMissing()) {
                return null;
            }

            return fixed(value.getWithoutSideEffect(), value.getSideEffect());
        }

        /**
         * Extracts the side effect from the {@code value} if any, and fixes it on that value.
         * <p>
         * If the execution time value is {@link ExecutionTimeValue#hasFixedValue() not fixed} then null is returned.
         *
         * @see #fixed
         */
        @Nullable
        static <T, A> SideEffect<T> fixedFrom(ExecutionTimeValue<A> value) {
            if (!value.hasFixedValue()) {
                return null;
            }

            return fixed(value.getFixedValue(), value.getSideEffect());
        }

        /**
         * Creates a new side effect that executes given side effects sequentially.
         */
        @Nullable
        static <T> SideEffect<T> composite(Iterable<SideEffect<T>> sideEffects) {
            return CompositeSideEffect.of(sideEffects);
        }

        /**
         * Creates a new side effect that executes given side effects sequentially.
         */
        @SafeVarargs
        @Nullable
        static <T> SideEffect<T> composite(SideEffect<? super T>... sideEffects) {
            @SuppressWarnings("varargs")
            List<SideEffect<T>> sideEffectList = Cast.uncheckedNonnullCast(Arrays.asList(sideEffects));
            return CompositeSideEffect.of(sideEffectList);
        }

        /**
         * Creates a new builder for collecting side effects and building a composite side effect later.
         */
        static <T> SideEffectBuilder<T> builder() {
            return new SideEffectBuilder<>();
        }
    }

    /**
     * A side effect that ignores its argument and always runs on a fixed value instead.
     */
    class FixedSideEffect<T, A> implements SideEffect<T> {

        @Nullable
        private static <T, A> FixedSideEffect<T, A> of(A value, @Nullable SideEffect<? super A> sideEffect) {
            if (sideEffect == null) {
                return null;
            }

            if (sideEffect instanceof FixedSideEffect) {
                // Optimization to not nest fixed side effect, since they ignore the argument
                return Cast.uncheckedNonnullCast(sideEffect);
            }

            return new FixedSideEffect<>(value, sideEffect);
        }

        private final A value;
        private final SideEffect<? super A> sideEffect;

        private FixedSideEffect(A value, SideEffect<? super A> sideEffect) {
            this.value = value;
            this.sideEffect = sideEffect;
        }

        @Override
        public void execute(T t) {
            sideEffect.execute(value);
        }

        @Override
        public String toString() {
            return "fixed(" + value + ", " + sideEffect + ")";
        }
    }

    class CompositeSideEffect<T> implements SideEffect<T> {

        @Nullable
        private static <T> SideEffect<T> of(Iterable<SideEffect<T>> sideEffects) {
            List<SideEffect<? super T>> flatSideEffects = new ArrayList<>();

            for (SideEffect<? super T> sideEffect : sideEffects) {
                if (sideEffect == null) {
                    continue;
                }

                if (sideEffect instanceof CompositeSideEffect) {
                    CompositeSideEffect<? super T> compositeSideEffect = Cast.uncheckedNonnullCast(sideEffect);
                    flatSideEffects.addAll(compositeSideEffect.sideEffects);
                } else {
                    flatSideEffects.add(sideEffect);
                }
            }

            if (flatSideEffects.isEmpty()) {
                return null;
            } else if (flatSideEffects.size() == 1) {
                return Cast.uncheckedNonnullCast(flatSideEffects.get(0));
            } else {
                return new CompositeSideEffect<>(flatSideEffects);
            }
        }

        private final List<SideEffect<? super T>> sideEffects;

        private CompositeSideEffect(Iterable<SideEffect<? super T>> sideEffects) {
            this.sideEffects = ImmutableList.copyOf(sideEffects);
        }

        @Override
        public void execute(T t) {
            for (SideEffect<? super T> sideEffect : sideEffects) {
                sideEffect.execute(t);
            }
        }

        @Override
        public String toString() {
            return "composite(" + sideEffects + ")";
        }
    }

    class SideEffectBuilder<T> {

        List<SideEffect<T>> sideEffects = new ArrayList<>();

        void add(@Nullable SideEffect<? super T> sideEffect) {
            if (sideEffect == null) {
                return;
            }

            sideEffects.add(Cast.uncheckedCast(sideEffect));
        }

        @Nullable
        public SideEffect<T> build() {
            return sideEffects.isEmpty() ? null : SideEffect.composite(sideEffects);
        }
    }

    /**
     * Carries either a value or some diagnostic information about where the value would have come from, had it been present.
     * <p>
     * If value is present, it can optionally carry a {@link #getSideEffect() side effect}.
     * A {@link #isMissing() missing} value never carries a side effect.
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
            if (value == null) {
                throw new IllegalArgumentException();
            }
            return new Present<>(value);
        }

        static Value<Void> present() {
            return SUCCESS;
        }

        /**
         * Creates a value attaching the given side effect.
         */
        static <T> Value<T> withSideEffect(T value, SideEffect<? super T> sideEffect) {
            if (value == null) {
                throw new IllegalArgumentException();
            }
            return new Present<>(value, sideEffect);
        }

        /**
         * Returns the value if present and throws an {@code IllegalStateException} otherwise.
         * <p>
         * <b>Runs</b> a {@link #getSideEffect() side effect} if one is attached to the value and the value is present.
         * <p>
         * If the value needs to be transformed, instead of unpacking consider using {@link #transform(Transformer)}
         * which automatically keeps track of side effects.
         */
        T get() throws IllegalStateException;

        /**
         * Returns the value if present or null otherwise.
         * <p>
         * <b>Runs</b> a {@link #getSideEffect() side effect} if one is attached to the value and the value is present.
         * <p>
         * If the value needs to be transformed, instead of unpacking consider using {@link #transform(Transformer)}
         * which automatically keeps track of side effects.
         */
        @Nullable
        T orNull();

        /**
         * Returns the value if present or the {@code defaultValue} otherwise.
         * <p>
         * <b>Runs</b> a {@link #getSideEffect() side effect} if one is attached to the value and the value is present.
         * <p>
         * If the value needs to be transformed, instead of unpacking consider using {@link #transform(Transformer)}
         * which automatically keeps track of side effects.
         */
        T orElse(T defaultValue);

        /**
         * Behaves like {@link #get()}, but does not execute a side effect if present.
         * <p>
         * If the value needs to be transformed, instead of unpacking consider using {@link #transform(Transformer)}
         * which automatically keeps track of side effects.
         * <p>
         * This method should only be used when the side effect is {@link #getSideEffect() extracted} separately
         * to be passed further.
         */
        T getWithoutSideEffect() throws IllegalStateException;

        /**
         * Applies the {@code transformer} to the value, preserving a side effect if present.
         */
        <R> Value<R> transform(Transformer<? extends R, ? super T> transformer);

        /**
         * Returns a value attaching the given side effect.
         * <p>
         * A {@link #isMissing() missing} value never carries a side effect.
         * If the value is present and already has a side effect, then side effects are composed
         * to be executed in the order of attachment.
         */
        Value<T> withSideEffect(@Nullable SideEffect<? super T> sideEffect);

        // Only populated when value is missing
        List<DisplayName> getPathToOrigin();

        boolean isMissing();

        /**
         * Returns a side effect if one is attached to this value.
         * <p>
         * A {@link #isMissing() missing} value never carries a side effect.
         */
        @Nullable
        SideEffect<? super T> getSideEffect();

        <S> Value<S> asType();

        Value<T> pushWhenMissing(@Nullable DisplayName displayName);

        Value<T> addPathsFrom(Value<?> rightValue);
    }

    class Present<T> implements Value<T> {
        private final T result;
        private final SideEffect<? super T> sideEffect;

        private Present(T result) {
            this.result = result;
            this.sideEffect = null;
        }

        private Present(T result, @Nullable SideEffect<? super T> sideEffect) {
            this.result = result;
            this.sideEffect = sideEffect;
        }

        @Override
        public boolean isMissing() {
            return false;
        }

        @Override
        public T get() throws IllegalStateException {
            runSideEffect();
            return result;
        }

        @Override
        public T orNull() {
            return get();
        }

        @Override
        public T orElse(T defaultValue) {
            return get();
        }

        @Override
        public T getWithoutSideEffect() throws IllegalStateException {
            return result;
        }

        @Override
        public <R> Value<R> transform(Transformer<? extends R, ? super T> transformer) {
            R transformResult = transformer.transform(result);
            if (transformResult == null || sideEffect == null) {
                return Value.ofNullable(transformResult);
            }
            return new Present<>(transformResult, SideEffect.fixed(result, sideEffect));
        }

        @Override
        public Value<T> withSideEffect(@Nullable SideEffect<? super T> sideEffect) {
            if (sideEffect == null) {
                return this;
            }

            SideEffect<? super T> composedSideEffect = this.sideEffect == null ? sideEffect : SideEffect.composite(this.sideEffect, sideEffect);
            // Using the direct constructor call, because `result` can be null due to `Value.SUCCESS`
            return new Present<>(result, composedSideEffect);
        }

        @Override
        public Value<T> pushWhenMissing(@Nullable DisplayName displayName) {
            return this;
        }

        @Nullable
        @Override
        public SideEffect<? super T> getSideEffect() {
            return sideEffect;
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

        private void runSideEffect() {
            if (sideEffect != null) {
                sideEffect.execute(result);
            }
        }

        @Override
        public String toString() {
            return sideEffect == null
                ? String.format("value(%s)", result)
                : String.format("valueWithSideEffect(%s, %s)", result, sideEffect);
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

        @Nullable
        @Override
        public SideEffect<? super T> getSideEffect() {
            return null;
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
        public T orElse(T defaultValue) {
            return defaultValue;
        }

        @Override
        public T getWithoutSideEffect() throws IllegalStateException {
            return get();
        }

        @Override
        public <R> Value<R> transform(Transformer<? extends R, ? super T> transformer) {
            return asType();
        }

        @Override
        public Value<T> withSideEffect(@Nullable SideEffect<? super T> sideEffect) {
            // Missing value never carries a side effect
            return this;
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

        @Override
        public String toString() {
            return path.isEmpty() ? "missing" : String.format("missing(path=%s)", path);
        }
    }

    /**
     * Represents either a missing value, a fixed value with fixed contents, a fixed value with changing contents, or a changing value (with changing contents).
     *
     * @see ProviderInternal for a discussion of these states.
     */
    @SuppressWarnings("ClassInitializationDeadlock")
    abstract class ExecutionTimeValue<T> {
        private static final MissingExecutionTimeValue MISSING = new MissingExecutionTimeValue();

        /**
         * Returns {@code true} when the value is <b>definitely</b> missing.
         * <p>
         * A {@code false} return value doesn't mean the value is <b>definitely</b> present, it might still be missing at runtime.
         */
        public boolean isMissing() {
            return false;
        }

        public boolean hasFixedValue() {
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

        @Nullable
        public SideEffect<? super T> getSideEffect() throws IllegalStateException {
            return null;
        }

        public Value<T> toValue() throws IllegalStateException {
            throw new IllegalStateException();
        }

        public abstract ProviderInternal<T> toProvider();

        public abstract ExecutionTimeValue<T> withChangingContent();

        public abstract ExecutionTimeValue<T> withSideEffect(@Nullable SideEffect<? super T> sideEffect);

        public static <T> ExecutionTimeValue<T> missing() {
            return Cast.uncheckedCast(MISSING);
        }

        public static <T> ExecutionTimeValue<T> fixedValue(T value) {
            assert value != null;
            return new FixedExecutionTimeValue<>(value, false, null);
        }

        public static <T> ExecutionTimeValue<T> ofNullable(@Nullable T value) {
            if (value == null) {
                return missing();
            } else {
                return fixedValue(value);
            }
        }

        public static <T> ExecutionTimeValue<T> value(Value<T> value) {
            if (value.isMissing()) {
                return missing();
            } else {
                return fixedValue(value.getWithoutSideEffect()).withSideEffect(value.getSideEffect());
            }
        }

        public static <T> ExecutionTimeValue<T> changingValue(ProviderInternal<T> provider) {
            return new ChangingExecutionTimeValue<>(provider);
        }
    }

    class MissingExecutionTimeValue extends ExecutionTimeValue<Object> {

        @Override
        public String toString() {
            return "missing";
        }

        @Override
        public boolean isMissing() {
            return true;
        }

        @Override
        public ProviderInternal<Object> toProvider() {
            return Providers.notDefined();
        }

        @Override
        public ExecutionTimeValue<Object> withChangingContent() {
            return this;
        }

        @Override
        public Value<Object> toValue() {
            return Value.missing();
        }

        @Override
        public ExecutionTimeValue<Object> withSideEffect(@Nullable SideEffect<? super Object> sideEffect) {
            return this;
        }
    }

    class FixedExecutionTimeValue<T> extends ExecutionTimeValue<T> {
        private final T value;
        private final boolean changingContent;
        private final SideEffect<? super T> sideEffect;

        private FixedExecutionTimeValue(T value, boolean changingContent, @Nullable SideEffect<? super T> sideEffect) {
            this.value = value;
            this.changingContent = changingContent;
            this.sideEffect = sideEffect;
        }

        @Override
        public String toString() {
            return String.format("fixed(%s)", value);
        }

        @Override
        public boolean hasFixedValue() {
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

        @Nullable
        @Override
        public SideEffect<? super T> getSideEffect() {
            return sideEffect;
        }

        @Override
        public Value<T> toValue() {
            return sideEffect == null ? Value.of(value) : Value.withSideEffect(value, sideEffect);
        }

        @Override
        public ProviderInternal<T> toProvider() {
            ProviderInternal<T> provider;
            if (changingContent) {
                provider = new Providers.FixedValueWithChangingContentProvider<>(value);
            } else {
                provider = Providers.of(value);
            }

            return sideEffect == null ? provider : provider.withSideEffect(sideEffect);
        }

        @Override
        public ExecutionTimeValue<T> withChangingContent() {
            return new FixedExecutionTimeValue<>(value, true, sideEffect);
        }

        @Override
        public ExecutionTimeValue<T> withSideEffect(@Nullable SideEffect<? super T> sideEffect) {
            if (sideEffect == null) {
                return this;
            }

            if (this.sideEffect == null) {
                return new FixedExecutionTimeValue<>(value, changingContent, sideEffect);
            }

            return new FixedExecutionTimeValue<>(value, changingContent, SideEffect.composite(this.sideEffect, sideEffect));
        }
    }

    class ChangingExecutionTimeValue<T> extends ExecutionTimeValue<T> {
        private final ProviderInternal<T> provider;

        private ChangingExecutionTimeValue(ProviderInternal<T> provider) {
            this.provider = provider;
        }

        @Override
        public String toString() {
            return String.format("changing(%s)", provider);
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

        @Override
        public ExecutionTimeValue<T> withSideEffect(@Nullable SideEffect<? super T> sideEffect) {
            if (sideEffect == null) {
                return this;
            }

            return new ChangingExecutionTimeValue<>(provider.withSideEffect(sideEffect));
        }
    }
}
