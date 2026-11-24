/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.component.external.model;

import org.jspecify.annotations.Nullable;

/**
 * Base class for {@link ModuleComponentArtifactIdentifier} implementations.
 *
 * <p>
 * Two identifiers are equal if they have the same component, file path, and destination file name,
 * regardless of which implementation they are.
 * </p>
 */
public abstract class AbstractModuleComponentArtifactIdentifier implements ModuleComponentArtifactIdentifier {
    private int hashCode;

    @Override
    public String getDisplayName() {
        return getDestinationFileName() + " (" + getComponentIdentifier().getDisplayName() + ")";
    }

    @Override
    public String getCapitalizedDisplayName() {
        return getDisplayName();
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    @Override
    public final boolean equals(@Nullable Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof AbstractModuleComponentArtifactIdentifier other)) {
            return false;
        }
        return getComponentIdentifier().equals(other.getComponentIdentifier())
            && getFilePath().equals(other.getFilePath())
            && getDestinationFileName().equals(other.getDestinationFileName());
    }

    @Override
    public final int hashCode() {
        int result = hashCode;
        // Lazy calculation of the hash code is done without any synchronization,
        // as the data used to calculate the hash code is immutable so any racy calculation
        // will still result in the same value being stored in the hashCode field.
        if (result == 0) {
            result = getComponentIdentifier().hashCode();
            result = 31 * result + getFilePath().hashCode();
            result = 31 * result + getDestinationFileName().hashCode();
            // Ensure that the hash code is never zero to ensure we don't recalculate it again later.
            if (result == 0) {
                result = 1;
            }
            hashCode = result;
        }
        return result;
    }
}
