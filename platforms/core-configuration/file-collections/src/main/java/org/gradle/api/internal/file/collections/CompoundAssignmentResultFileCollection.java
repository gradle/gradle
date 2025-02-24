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

package org.gradle.api.internal.file.collections;

import com.google.common.base.Preconditions;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.UnionFileCollection;
import org.gradle.api.internal.provider.support.CompoundAssignmentValue;
import org.gradle.api.internal.tasks.TaskDependencyFactory;

import javax.annotation.Nullable;

/**
 * A result of applying an operator (so far only {@code +}) to ConfigurableFileCollection.
 * Allows assigning itself back to the collection without causing circular evaluation failure if it is produced by the compound assignment operator {@code +=}.
 */
class CompoundAssignmentResultFileCollection extends UnionFileCollection implements CompoundAssignmentValue {
    private boolean canBeAssignedBack;
    @Nullable
    private ConfigurableFileCollection owner;
    private final FileCollectionInternal rhs;

    public CompoundAssignmentResultFileCollection(TaskDependencyFactory taskDependencyFactory, DefaultConfigurableFileCollection owner, FileCollectionInternal rhs) {
        super(taskDependencyFactory, owner, rhs);
        this.owner = owner;
        this.rhs = rhs;
    }

    public boolean canBeAssignedBackTo(ConfigurableFileCollection owner) {
        return canBeAssignedBack && this.owner == owner;
    }

    public void assignToOwner() {
        ConfigurableFileCollection theOwner = owner;
        Preconditions.checkState(theOwner != null, "The collection is already consumed by the owner");
        theOwner.from(rhs);
        assignmentCompleted();
    }

    @Override
    public void prepareForAssignment() {
        // We are part of the compound assignment and not just some standalone (prop `+` value).
        canBeAssignedBack = true;
    }

    @Override
    public void assignmentCompleted() {
        // When the expression involves a variable on the left side as opposed to a field, then this collection becomes its value.
        // It must lose all "magical" properties towards its owner, because it may be used to set its value outside the original expression.
        canBeAssignedBack = false;
        // Clean up stuff we no longer need to let GC collect them.
        owner = null;
    }

    @Override
    public boolean shouldReplaceResultWithNull() {
        // FileCollection supported += for a while, so changing this would be a breaking change.
        return false;
    }
}
