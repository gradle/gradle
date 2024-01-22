/*
 * Copyright 2024 the original author or authors.
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
import org.gradle.api.internal.provider.support.SupportsCompoundAssignment;
import org.gradle.api.provider.Provider;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * A helper class to implement an intermediate result of a compound assignment operation, like "+=".
 * It is then assigned to the left-hand side operand. When the LHS is a Property-typed property of some Gradle-enhanced object, then the assignment action is invoked.
 */
public final class CompoundAssignmentResult<T> extends AbstractMinimalProvider<T> implements SupportsCompoundAssignment.Result<Provider<T>> {
    private final ProviderInternal<T> value;
    private final Object owner;
    @Nullable
    private Runnable assignToOwnerAction;

    /**
     * Creates the result for {@code owner <OP> rhs} operation.
     *
     * @param value the intermediate value of the operation, used when LHS is a variable or non-Gradle enhanced property
     * @param owner the LHS operand of the compound operation
     * @param assignToOwnerAction the mutation of the owner
     */
    public CompoundAssignmentResult(ProviderInternal<T> value, Object owner, Runnable assignToOwnerAction) {
        this.value = value;
        this.owner = owner;
        this.assignToOwnerAction = Objects.requireNonNull(assignToOwnerAction);
    }

    public boolean isOwnedBy(Object target) {
        // It might be that this object was unwrapped, in which case it is considered a normal provider.
        return assignToOwnerAction != null && target == owner;
    }

    public void assignToOwner() {
        Runnable action = assignToOwnerAction;
        Preconditions.checkState(action != null, "The property is already consumed by the owner");
        assignToOwnerAction = null;
        action.run();
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
    @Nullable
    public Provider<T> unwrap() {
        // When the expression involves a variable on the left side as opposed to a filed, then this provider becomes its value.
        // It must lose all "magical" properties towards its owner, because it may be used to set its value outside the original expression.
        assignToOwnerAction = null;
        return null;
    }
}
