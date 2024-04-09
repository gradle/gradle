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
import org.gradle.api.Describable;
import org.gradle.internal.Cast;
import org.gradle.internal.logging.text.TreeFormatter;
import org.gradle.internal.state.ModelObject;

import javax.annotation.Nullable;
import java.util.function.Function;

/**
 * Provides a state pattern implementation for values that are finalizable and support conventions.
 *
 * <h3>Finalization</h3>
 * See {@link org.gradle.api.provider.HasConfigurableValue} and {@link HasConfigurableValueInternal}.
 *
 * <h3>Conventions</h3>
 * See {@link org.gradle.api.provider.SupportsConvention}.
 *
 * @param <S> the type of the value
 */
public abstract class ValueState<S> {
    private static final ValueState<Object> FINALIZED_VALUE = new FinalizedValue<>();

    /**
     * Creates a new non-finalized state.
     */
    public static <S> ValueState<S> newState(PropertyHost host) {
        return new ValueState.NonFinalizedValue<>(host, Function.identity());
    }

    /**
     * Creates a new non-finalized state.
     *
     * @param copier when the value is mutable, a shallow-copying function should be provided to avoid
     * sharing of mutable state between effective values and convention values
     */
    public static <S> ValueState<S> newState(PropertyHost host, Function<S, S> copier) {
        return new ValueState.NonFinalizedValue<>(host, copier);
    }

    public abstract boolean shouldFinalize(Describable displayName, @Nullable ModelObject producer);

    /**
     * Returns the state to replace this state once is finalized.
     */
    public abstract ValueState<S> finalState();

    /**
     * Sets a new convention value, replacing the existing one if set.
     *
     * @param convention the new convention value
     */
    public abstract void setConvention(S convention);

    public abstract void disallowChanges();

    public abstract void finalizeOnNextGet();

    public abstract void disallowUnsafeRead();

    /**
     * Marks this value state as being explicitly assigned. Does not remember the given value in any way.
     *
     * @param value the new explicitly assigned value
     * @return the very <code>value</code> given
     */
    //TODO-RC rename this or the overload as they have significantly different semantics
    public abstract S explicitValue(S value);

    /**
     * Returns <code>value</code> if this value state is marked as explicit, otherwise returns the given <code>defaultValue</code>.
     *
     * Note that "default value" is not related to the convention value, though they are easy to confuse.
     * A default value is a fallback value that is sensible to the caller, in the absence of the explicit value.
     * The default value is not related in any way to the convention value.
     *
     * @param value the current explicit value
     * @param defaultValue the default value
     * @return the given value, if this value state is not explicit, or given default value
     */
    //TODO-RC rename this or the overload as they have significantly different semantics
    public abstract S explicitValue(S value, S defaultValue);

    /**
     * Applies a new convention value, which replaces the existing convention value.
     *
     * Returns the given <code>value</code> if this value state is explicit, otherwise returns the new convention value.
     *
     * This is similar to calling {@link #setConvention(Object)} followed by {@link #explicitValue(Object, Object)}.
     *
     * @param value the current explicit value
     * @param convention the new convention
     * @return the given value, if this value state is not explicit, otherwise the new convention value
     */
    public abstract S applyConvention(S value, S convention);

    /**
     * Marks this value state as being non-explicit. Returns the convention, if any.
     */
    public abstract S implicitValue(S convention);

    public abstract S implicitValue();

    public abstract boolean maybeFinalizeOnRead(Describable displayName, @Nullable ModelObject producer, ValueSupplier.ValueConsumer consumer);

    public abstract void beforeMutate(Describable displayName);

    public abstract ValueSupplier.ValueConsumer forUpstream(ValueSupplier.ValueConsumer consumer);

    public boolean isFinalized() {
        return this == FINALIZED_VALUE;
    }

    /**
     * Is this state final or on its way for being finalized?
     */
    public abstract boolean isFinalizing();

    public void finalizeOnReadIfNeeded(Describable displayName, @Nullable ModelObject effectiveProducer, ValueSupplier.ValueConsumer consumer, Action<ValueSupplier.ValueConsumer> finalizeNow) {
        if (maybeFinalizeOnRead(displayName, effectiveProducer, consumer)) {
            finalizeNow.execute(forUpstream(consumer));
        }
    }

    public void disallowChangesAndFinalizeOnNextGet() {
        disallowChanges();
        finalizeOnNextGet();
    }

    public abstract boolean isExplicit();

    /**
     * Retrieves the current convention.
     */
    public abstract S convention();

    /**
     * Marks this value as being explicitly set with
     * the current value assigned to the convention.
     */
    public abstract S setToConvention();

    /**
     * Marks this value as being explicitly set with
     * the current value assigned to the convention,
     * unless it is already an explicit value.
     */
    public abstract S setToConventionIfUnset(S value);

    private static class NonFinalizedValue<S> extends ValueState<S> {
        private final PropertyHost host;
        private final Function<S, S> copier;
        private boolean explicitValue;
        private boolean finalizeOnNextGet;
        private boolean disallowChanges;
        private boolean disallowUnsafeRead;
        private S convention;

        public NonFinalizedValue(PropertyHost host, Function<S, S> copier) {
            this.host = host;
            this.copier = copier;
        }

        @Override
        public boolean shouldFinalize(Describable displayName, @Nullable ModelObject producer) {
            if (disallowUnsafeRead) {
                String reason = host.beforeRead(producer);
                if (reason != null) {
                    throw new IllegalStateException(cannotFinalizeValueOf(displayName, reason));
                }
            }
            return true;
        }

        @Override
        public ValueState<S> finalState() {
            return Cast.uncheckedCast(FINALIZED_VALUE);
        }

        @Override
        public boolean maybeFinalizeOnRead(Describable displayName, @Nullable ModelObject producer, ValueSupplier.ValueConsumer consumer) {
            if (disallowUnsafeRead || consumer == ValueSupplier.ValueConsumer.DisallowUnsafeRead) {
                String reason = host.beforeRead(producer);
                if (reason != null) {
                    throw new IllegalStateException(cannotQueryValueOf(displayName, reason));
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
        public void beforeMutate(Describable displayName) {
            if (disallowChanges) {
                throw new IllegalStateException(String.format("The value for %s cannot be changed any further.", displayName.getDisplayName()));
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
        public boolean isExplicit() {
            return explicitValue;
        }

        @Override
        public S convention() {
            return convention;
        }

        @Override
        public S setToConvention() {
            explicitValue = true;
            return shallowCopy(convention);
        }

        private S shallowCopy(S toCopy) {
            return copier.apply(toCopy);
        }

        @Override
        public S setToConventionIfUnset(S value) {
            if (!explicitValue) {
                return setToConvention();
            }
            return value;
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
            return shallowCopy(convention);
        }

        @Override
        public S implicitValue(S newConvention) {
            setConvention(newConvention);
            return implicitValue();
        }

        @Override
        public S applyConvention(S value, S convention) {
            this.convention = convention;
            if (!explicitValue) {
                return shallowCopy(convention);
            } else {
                return value;
            }
        }

        @Override
        public void setConvention(S convention) {
            this.convention = convention;
        }

        private String cannotFinalizeValueOf(Describable displayName, String reason) {
            return cannot("finalize", displayName, reason);
        }

        private String cannotQueryValueOf(Describable displayName, String reason) {
            return cannot("query", displayName, reason);
        }

        private String cannot(String what, Describable displayName, String reason) {
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
        public boolean shouldFinalize(Describable displayName, @Nullable ModelObject producer) {
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
        public boolean maybeFinalizeOnRead(Describable displayName, @Nullable ModelObject producer, ValueSupplier.ValueConsumer consumer) {
            // Already finalized
            return false;
        }

        @Override
        public void beforeMutate(Describable displayName) {
            throw new IllegalStateException(String.format("The value for %s is final and cannot be changed any further.", displayName.getDisplayName()));
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
        public S implicitValue(S defaultValue) {
            throw unexpected();
        }

        @Override
        public boolean isFinalizing() {
            return true;
        }

        @Override
        public boolean isExplicit() {
            return true;
        }

        @Override
        public S convention() {
            return null;
        }

        @Override
        public S setToConvention() {
            throw unexpected();
        }

        @Override
        public S setToConventionIfUnset(S value) {
            throw unexpected();
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
