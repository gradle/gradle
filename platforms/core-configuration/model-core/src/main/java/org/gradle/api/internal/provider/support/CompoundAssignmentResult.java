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

package org.gradle.api.internal.provider.support;

import com.google.common.base.Preconditions;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * A helper class to implement a result of a compound assignment operation.
 * It can be assigned to the assignment target (the left-hand-side symbol), but only once, so it cannot be used in multi-step operations like
 * {@code a += (b += c)}.
 */
public final class CompoundAssignmentResult {
    private final Object owner;
    @Nullable
    private Runnable assignToOwnerAction;

    public CompoundAssignmentResult(Object owner, Runnable assignToOwnerAction) {
        this.owner = owner;
        this.assignToOwnerAction = Objects.requireNonNull(assignToOwnerAction);
    }

    public boolean isOwnedBy(Object target) {
        return target == owner;
    }

    public void assignToOwner() {
        Runnable action = assignToOwnerAction;
        Preconditions.checkState(action != null, "The property is already consumed by the owner");
        assignToOwnerAction = null;
        action.run();
    }
}
