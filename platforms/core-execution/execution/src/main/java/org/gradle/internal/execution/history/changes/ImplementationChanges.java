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
import org.gradle.internal.snapshot.impl.ClassImplementationSnapshot;
import org.gradle.internal.snapshot.impl.ImplementationSnapshot;

public class ImplementationChanges implements ChangeContainer {
    private final ClassImplementationSnapshot previousImplementation;
    private final ImmutableList<ImplementationSnapshot> previousAdditionalImplementations;
    private final ClassImplementationSnapshot currentImplementation;
    private final ImmutableList<ImplementationSnapshot> currentAdditionalImplementations;
    private final Describable executable;

    public ImplementationChanges(
        ClassImplementationSnapshot previousImplementation,
        ImmutableList<ImplementationSnapshot> previousAdditionalImplementations,
        ClassImplementationSnapshot currentImplementation,
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
        if (!currentImplementation.getClassIdentifier().equals(previousImplementation.getClassIdentifier())) {
            return visitor.visitChange(new DescriptiveChange("The type of %s has changed from '%s' to '%s'.",
                executable.getDisplayName(), previousImplementation.getClassIdentifier(), currentImplementation.getClassIdentifier()));
        }

        if (!currentImplementation.getClassLoaderHash().equals(previousImplementation.getClassLoaderHash())) {
            return visitor.visitChange(new DescriptiveChange("Class path of %s has changed from %s to %s.",
                executable.getDisplayName(), previousImplementation.getClassLoaderHash(), currentImplementation.getClassLoaderHash()));
        }

        if (!currentAdditionalImplementations.equals(previousAdditionalImplementations)) {
            return visitor.visitChange(new DescriptiveChange("One or more additional actions for %s have changed.",
                executable.getDisplayName()));
        }
        return true;
    }
}
