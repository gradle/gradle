/*
 * Copyright 2025 the original author or authors.
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

import com.google.common.base.Preconditions;
import org.gradle.api.internal.provider.support.CompoundAssignmentValue;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * A result of applying an operator (so far only {@code +}) to ListProperty, MapProperty or SetProperty.
 * Allows assigning itself back to the property without causing circular evaluation failure if it is produced by the compound assignment operator {@code +=}.
 */
public final class CompoundAssignmentResultProvider<T> extends AbstractMinimalProvider<T> implements CompoundAssignmentValue {
    private final ProviderInternal<T> value;
    private boolean canBeAssignedBack;
    @Nullable
    private Object owner;
    @Nullable
    private Runnable assignToOwnerAction;

    /**
     * Creates the result for {@code owner <OP> rhs} operation.
     *
     * @param value the intermediate value of the operation, used when LHS is a variable or non-Gradle enhanced property
     * @param owner the LHS operand of the compound operation
     * @param assignToOwnerAction the mutation of the owner
     */
    public CompoundAssignmentResultProvider(ProviderInternal<T> value, Object owner, Runnable assignToOwnerAction) {
        this.value = value;
        this.owner = owner;
        this.assignToOwnerAction = Objects.requireNonNull(assignToOwnerAction);
    }

    public boolean canBeAssignedBackTo(Object target) {
        // It might be that this object is used outside its originating assignment expression, in which case it is considered a normal provider.
        return canBeAssignedBack && target == owner;
    }

    public void assignToOwner() {
        Runnable action = assignToOwnerAction;
        Preconditions.checkState(action != null, "The property is already consumed by the owner");
        action.run();
        assignmentCompleted();
    }

    @Override
    protected Value<? extends T> calculateOwnValue(ValueConsumer consumer) {
        return value.calculateValue(consumer);
    }

    @Override
    public boolean calculatePresence(ValueConsumer consumer) {
        return value.calculatePresence(consumer);
    }

    @Override
    public ExecutionTimeValue<? extends T> calculateExecutionTimeValue() {
        return value.calculateExecutionTimeValue();
    }

    @Nullable
    @Override
    public Class<T> getType() {
        return value.getType();
    }

    @Override
    public ValueProducer getProducer() {
        return value.getProducer();
    }

    @Override
    protected String toStringNoReentrance() {
        return value.toString();
    }

    @Override
    public void prepareForAssignment() {
        // We are part of the compound assignment and not just some standalone (prop `+` value).
        canBeAssignedBack = true;
    }

    @Override
    public void assignmentCompleted() {
        // When the expression involves a variable on the left side as opposed to a field, then this provider becomes its value.
        // It must lose all "magical" properties towards its owner, because it may be used to set its value outside the original expression.
        canBeAssignedBack = false;
        // Clean up stuff we no longer need to let GC collect them.
        owner = null;
        assignToOwnerAction = null;
    }

    @Override
    public boolean shouldReplaceResultWithNull() {
        // Disallow using prop += ... as a subexpression to mimic Kotlin.
        return true;
    }
}
