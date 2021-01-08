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
import org.gradle.internal.snapshot.impl.ImplementationSnapshot;

import javax.annotation.Nullable;

public class ImplementationChanges implements ChangeContainer {
    private final ImplementationSnapshot previousImplementation;
    private final ImmutableList<ImplementationSnapshot> previousAdditionalImplementations;
    private final ImplementationSnapshot currentImplementation;
    private final ImmutableList<ImplementationSnapshot> currentAdditionalImplementations;
    private final Describable executable;

    public ImplementationChanges(
        ImplementationSnapshot previousImplementation,
        ImmutableList<ImplementationSnapshot> previousAdditionalImplementations,
        ImplementationSnapshot currentImplementation,
        ImmutableList<ImplementationSnapshot> currentAdditionalImplementations,
        Describable executable
    ) {
        this.previousImplementation = previousImplementation;
        this.previousAdditionalImplementations = previousAdditionalImplementations;
        this.currentImplementation = currentImplementation;
        this.currentAdditionalImplementations = currentAdditionalImplementations;
        this.executable = executable;
    }

    @Override
    public boolean accept(ChangeVisitor visitor) {
        if (!currentImplementation.getTypeName().equals(previousImplementation.getTypeName())) {
            return visitor.visitChange(new DescriptiveChange("The type of %s has changed from '%s' to '%s'.",
                executable.getDisplayName(), previousImplementation.getTypeName(), currentImplementation.getTypeName()));
        }
        if (currentImplementation.isUnknown()) {
            return visitor.visitChange(new DescriptiveChange("The type of %s %s",
                    executable.getDisplayName(), currentImplementation.getUnknownReason()));
        }
        if (previousImplementation.isUnknown()) {
            return visitor.visitChange(new DescriptiveChange("During the previous execution of %s, it %s",
                    executable.getDisplayName(), previousImplementation.getUnknownReason()));
        }
        if (!currentImplementation.getClassLoaderHash().equals(previousImplementation.getClassLoaderHash())) {
            return visitor.visitChange(new DescriptiveChange("Class path of %s has changed from %s to %s.",
                    executable.getDisplayName(), previousImplementation.getClassLoaderHash(), currentImplementation.getClassLoaderHash()));
        }

        ImplementationSnapshot unknownImplementation = findUnknownImplementation(currentAdditionalImplementations);
        if (unknownImplementation != null) {
            return visitor.visitChange(new DescriptiveChange("Additional action for %s: %s",
                    executable.getDisplayName(), unknownImplementation.getUnknownReason()));
        }
        ImplementationSnapshot previousUnknownImplementation = findUnknownImplementation(previousAdditionalImplementations);
        if (previousUnknownImplementation != null) {
            return visitor.visitChange(new DescriptiveChange("During the previous execution of %s, it had an additional action that %s",
                    executable.getDisplayName(), previousUnknownImplementation.getUnknownReason()));
        }
        if (!currentAdditionalImplementations.equals(previousAdditionalImplementations)) {
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
