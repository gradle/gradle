/*
 * Copyright 2023 the original author or authors.
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
import org.gradle.internal.Cast;
import org.gradle.internal.DisplayName;
import org.gradle.internal.logging.text.TreeFormatter;
import org.gradle.internal.state.ModelObject;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * Manages values that are finalizable and support conventions.
 *
 * @param <S>
 */
public abstract class ValueState<S> {
    private static final ValueState<Object> FINALIZED_VALUE = new FinalizedValue<>();

    public static <S> ValueState<S> newState(PropertyHost host) {
        return new ValueState.NonFinalizedValue<>(host);
    }

    public abstract boolean shouldFinalize(Supplier<DisplayName> displayName, @Nullable ModelObject producer);

    public abstract ValueState<S> finalState();

    public abstract void setConvention(S convention);

    public abstract void disallowChanges();

    public abstract void finalizeOnNextGet();

    public abstract void disallowUnsafeRead();

    public abstract S explicitValue(S value);

    public abstract S explicitValue(S value, S defaultValue);

    public abstract S applyConvention(S value, S convention);

    /**
     * Marks this value state as being non-explicit. Returns the convention, if any.
     */
    public abstract S implicitValue();

    public abstract boolean maybeFinalizeOnRead(Supplier<DisplayName> displayName, @Nullable ModelObject producer, ValueSupplier.ValueConsumer consumer);

    public abstract void beforeMutate(Supplier<DisplayName> displayName);

    public abstract ValueSupplier.ValueConsumer forUpstream(ValueSupplier.ValueConsumer consumer);

    public boolean isFinalized() {
        return this == FINALIZED_VALUE;
    }

    /**
     * Is this state final or on its way for being finalized?
     */
    public abstract boolean isFinalizing();

    public void finalizeOnReadIfNeeded(Supplier<DisplayName> getDisplayName, ModelObject effectiveProducer, ValueSupplier.ValueConsumer consumer, Action<ValueSupplier.ValueConsumer> finalizeNow) {
        if (maybeFinalizeOnRead(getDisplayName, effectiveProducer, consumer)) {
            finalizeNow.execute(forUpstream(consumer));
        }
    }

    public void disallowChangesAndFinalizeOnNextGet() {
        disallowChanges();
        finalizeOnNextGet();
    }

    private static class NonFinalizedValue<S> extends ValueState<S> {
        private final PropertyHost host;
        private boolean explicitValue;
        private boolean finalizeOnNextGet;
        private boolean disallowChanges;
        private boolean disallowUnsafeRead;
        private S convention;

        public NonFinalizedValue(PropertyHost host) {
            this.host = host;
        }

        @Override
        public boolean shouldFinalize(Supplier<DisplayName> displayName, @Nullable ModelObject producer) {
            if (disallowUnsafeRead) {
                String reason = host.beforeRead(producer);
                if (reason != null) {
                    throw new IllegalStateException(cannotFinalizeValueOf(displayName.get(), reason));
                }
            }
            return true;
        }

        @Override
        public ValueState<S> finalState() {
            return Cast.uncheckedCast(FINALIZED_VALUE);
        }

        @Override
        public boolean maybeFinalizeOnRead(Supplier<DisplayName> displayName, @Nullable ModelObject producer, ValueSupplier.ValueConsumer consumer) {
            if (disallowUnsafeRead || consumer == ValueSupplier.ValueConsumer.DisallowUnsafeRead) {
                String reason = host.beforeRead(producer);
                if (reason != null) {
                    throw new IllegalStateException(cannotQueryValueOf(displayName.get(), reason));
                }
            }
            return finalizeOnNextGet || consumer == ValueSupplier.ValueConsumer.DisallowUnsafeRead;
        }

        @Override
        public ValueSupplier.ValueConsumer forUpstream(ValueSupplier.ValueConsumer consumer) {
            if (disallowUnsafeRead) {
                return ValueSupplier.ValueConsumer.DisallowUnsafeRead;
            } else {
                return consumer;
            }
        }

        @Override
        public void beforeMutate(Supplier<DisplayName> displayName) {
            if (disallowChanges) {
                throw new IllegalStateException(String.format("The value for %s cannot be changed any further.", displayName.get().getDisplayName()));
            }
        }

        @Override
        public void disallowChanges() {
            disallowChanges = true;
        }

        @Override
        public void finalizeOnNextGet() {
            finalizeOnNextGet = true;
        }

        @Override
        public void disallowUnsafeRead() {
            disallowUnsafeRead = true;
            finalizeOnNextGet = true;
        }

        @Override
        public boolean isFinalizing() {
            return finalizeOnNextGet;
        }

        @Override
        public S explicitValue(S value) {
            explicitValue = true;
            return value;
        }

        @Override
        public S explicitValue(S value, S defaultValue) {
            if (!explicitValue) {
                return defaultValue;
            }
            return value;
        }

        @Override
        public S implicitValue() {
            explicitValue = false;
            return convention;
        }

        @Override
        public S applyConvention(S value, S convention) {
            this.convention = convention;
            if (!explicitValue) {
                return convention;
            } else {
                return value;
            }
        }

        @Override
        public void setConvention(S convention) {
            this.convention = convention;
        }

        private String cannotFinalizeValueOf(DisplayName displayName, String reason) {
            return cannot("finalize", displayName, reason);
        }

        private String cannotQueryValueOf(DisplayName displayName, String reason) {
            return cannot("query", displayName, reason);
        }

        private String cannot(String what, DisplayName displayName, String reason) {
            TreeFormatter formatter = new TreeFormatter();
            formatter.node("Cannot " + what + " the value of ");
            formatter.append(displayName.getDisplayName());
            formatter.append(" because ");
            formatter.append(reason);
            formatter.append(".");
            return formatter.toString();
        }
    }

    private static class FinalizedValue<S> extends ValueState<S> {
        @Override
        public boolean shouldFinalize(Supplier<DisplayName> displayName, @Nullable ModelObject producer) {
            return false;
        }

        @Override
        public void disallowChanges() {
            // Finalized, so already cannot change
        }

        @Override
        public void finalizeOnNextGet() {
            // Finalized already
        }

        @Override
        public void disallowUnsafeRead() {
            // Finalized already so read is safe
        }

        @Override
        public boolean maybeFinalizeOnRead(Supplier<DisplayName> displayName, @Nullable ModelObject producer, ValueSupplier.ValueConsumer consumer) {
            // Already finalized
            return false;
        }

        @Override
        public void beforeMutate(Supplier<DisplayName> displayName) {
            throw new IllegalStateException(String.format("The value for %s is final and cannot be changed any further.", displayName.get().getDisplayName()));
        }

        @Override
        public ValueSupplier.ValueConsumer forUpstream(ValueSupplier.ValueConsumer consumer) {
            throw unexpected();
        }

        @Override
        public S explicitValue(S value) {
            throw unexpected();
        }

        @Override
        public S explicitValue(S value, S defaultValue) {
            throw unexpected();
        }

        @Override
        public S applyConvention(S value, S convention) {
            throw unexpected();
        }

        @Override
        public S implicitValue() {
            throw unexpected();
        }

        @Override
        public boolean isFinalizing() {
            return true;
        }

        @Override
        public ValueState<S> finalState() {
            // TODO - it is currently possible for multiple threads to finalize a property instance concurrently (https://github.com/gradle/gradle/issues/12811)
            // This should be strict
            return this;
        }

        @Override
        public void setConvention(S convention) {
            throw unexpected();
        }

        private UnsupportedOperationException unexpected() {
            return new UnsupportedOperationException("Valued object is in an unexpected state.");
        }
    }
}
