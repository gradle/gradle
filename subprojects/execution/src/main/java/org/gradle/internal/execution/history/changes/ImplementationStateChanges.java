/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.execution.history.changes;

import com.google.common.collect.ImmutableList;
import org.gradle.api.Describable;
import org.gradle.internal.change.ChangeContainer;
import org.gradle.internal.change.ChangeVisitor;
import org.gradle.internal.change.DescriptiveChange;
import org.gradle.internal.execution.history.BeforeExecutionState;
import org.gradle.internal.execution.history.PreviousExecutionState;
import org.gradle.internal.snapshot.impl.ImplementationSnapshot;

import javax.annotation.Nullable;

public class ImplementationStateChanges implements ChangeContainer {
    private final PreviousExecutionState previousExecution;
    private final BeforeExecutionState currentExecution;
    private final Describable executable;

    public ImplementationStateChanges(PreviousExecutionState previousExecution, BeforeExecutionState currentExecution, Describable executable) {
        this.previousExecution = previousExecution;
        this.currentExecution = currentExecution;
        this.executable = executable;
    }

    @Override
    public boolean accept(ChangeVisitor visitor) {
        ImplementationSnapshot prevImplementation = previousExecution.getImplementation();
        ImplementationSnapshot implementation = currentExecution.getImplementation();
        if (!implementation.getTypeName().equals(prevImplementation.getTypeName())) {
            return visitor.visitChange(new DescriptiveChange("The type of %s has changed from '%s' to '%s'.",
                executable.getDisplayName(), prevImplementation.getTypeName(), implementation.getTypeName()));
        }
        if (implementation.isUnknown()) {
            return visitor.visitChange(new DescriptiveChange("The type of %s %s",
                    executable.getDisplayName(), implementation.getUnknownReason()));
        }
        if (prevImplementation.isUnknown()) {
            return visitor.visitChange(new DescriptiveChange("During the previous execution of %s, it %s",
                    executable.getDisplayName(), prevImplementation.getUnknownReason()));
        }
        if (!implementation.getClassLoaderHash().equals(prevImplementation.getClassLoaderHash())) {
            return visitor.visitChange(new DescriptiveChange("Class path of %s has changed from %s to %s.",
                    executable.getDisplayName(), prevImplementation.getClassLoaderHash(), implementation.getClassLoaderHash()));
        }

        ImmutableList<ImplementationSnapshot> additionalImplementations = currentExecution.getAdditionalImplementations();
        ImplementationSnapshot unknownImplementation = findUnknownImplementation(additionalImplementations);
        if (unknownImplementation != null) {
            return visitor.visitChange(new DescriptiveChange("Additional action for %s: %s",
                    executable.getDisplayName(), unknownImplementation.getUnknownReason()));
        }
        ImplementationSnapshot previousUnknownImplementation = findUnknownImplementation(previousExecution.getAdditionalImplementations());
        if (previousUnknownImplementation != null) {
            return visitor.visitChange(new DescriptiveChange("During the previous execution of %s, it had an additional action that %s",
                    executable.getDisplayName(), previousUnknownImplementation.getUnknownReason()));
        }
        if (!additionalImplementations.equals(previousExecution.getAdditionalImplementations())) {
            return visitor.visitChange(new DescriptiveChange("One or more additional actions for %s have changed.",
                    executable.getDisplayName()));
        }
        return true;
    }

    @Nullable
    private static ImplementationSnapshot findUnknownImplementation(Iterable<ImplementationSnapshot> implementations) {
        for (ImplementationSnapshot implementation : implementations) {
            if (implementation.isUnknown()) {
                return implementation;
            }
        }
        return null;
    }
}
