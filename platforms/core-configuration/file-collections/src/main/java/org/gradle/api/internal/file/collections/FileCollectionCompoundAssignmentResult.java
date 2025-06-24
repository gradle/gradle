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
import org.gradle.api.internal.groovy.support.CompoundAssignmentResult;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.jspecify.annotations.Nullable;

/**
 * A helper class to implement an intermediate result of a compound assignment operation, like "+=".
 * It is then assigned to the left-hand side operand. When the LHS is a ConfigurableFileCollection-typed property of some Gradle-enhanced object, then the assignment action is invoked.
 */
final class FileCollectionCompoundAssignmentResult extends UnionFileCollection implements CompoundAssignmentResult {
    @Nullable
    private ConfigurableFileCollection owner;
    private final FileCollectionInternal rhs;

    public FileCollectionCompoundAssignmentResult(TaskDependencyFactory taskDependencyFactory, DefaultConfigurableFileCollection owner, FileCollectionInternal rhs) {
        super(taskDependencyFactory, owner, rhs);
        this.owner = owner;
        this.rhs = rhs;
    }

    public boolean isOwnedBy(ConfigurableFileCollection owner) {
        return this.owner == owner;
    }

    public void assignToOwner() {
        ConfigurableFileCollection theOwner = owner;
        owner = null;
        Preconditions.checkState(theOwner != null, "The collection is already consumed by the owner");
        theOwner.from(rhs);
    }

    @Override
    public void assignmentComplete() {
        // When the expression involves a variable on the left side as opposed to a field, then this collection becomes its value.
        // It must lose all "magical" properties towards its owner, because it may be used to set its value outside the original expression.
        owner = null;
    }

    @Override
    public boolean shouldDiscardResult() {
        // Unlike the Property, we cannot discard the += value without losing backward compatibility.
        return false;
    }
}
