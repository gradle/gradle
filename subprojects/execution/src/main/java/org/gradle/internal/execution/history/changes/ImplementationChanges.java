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
import org.gradle.api.GradleException;
import org.gradle.internal.snapshot.impl.ImplementationSnapshot;

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
            // We already validate that the current implementation can't be unknown.
            throw new GradleException("Cannot determine changes for work with unknown implementation");
        }
        if (previousImplementation.isUnknown()) {
            // When we fail on an unknown implementation, the previous one can't be unknown.
            // Currently, we only emit a deprecation warning
            return visitor.visitChange(new DescriptiveChange("The implementation of %s has changed.",
                executable.getDisplayName())
            );
        }
        if (!currentImplementation.getClassLoaderHash().equals(previousImplementation.getClassLoaderHash())) {
            return visitor.visitChange(new DescriptiveChange("Class path of %s has changed from %s to %s.",
                    executable.getDisplayName(), previousImplementation.getClassLoaderHash(), currentImplementation.getClassLoaderHash()));
        }

        validateImplementationsKnown(currentAdditionalImplementations);
        if (!currentAdditionalImplementations.equals(previousAdditionalImplementations)) {
            return visitor.visitChange(new DescriptiveChange("One or more additional actions for %s have changed.",
                    executable.getDisplayName()));
        }
        return true;
    }

    private static void validateImplementationsKnown(Iterable<ImplementationSnapshot> implementations) {
        for (ImplementationSnapshot implementation : implementations) {
            if (implementation.isUnknown()) {
                throw new GradleException("Cannot determine changes for additional action with unknown implementation");
            }
        }
    }
}
