/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.internal.DisplayName;
import org.jspecify.annotations.Nullable;

/**
 * An immutable identifier for an artifact that belongs to some module version.
 */
public abstract class ModuleComponentArtifactIdentifier implements ComponentArtifactIdentifier, DisplayName {
    private int hashCode;

    /**
     * Returns the id of the component that this artifact belongs to.
     */
    @Override
    public abstract ModuleComponentIdentifier getComponentIdentifier();

    /**
     * The name of the file that the identified artifact is stored as locally.
     *
     * @implNote This is used as part of equality and hashing because downstream consumers may only use this name to identify the artifact.
     * We need to ensure that the same file stored in two different locations is treated as two different artifacts.
     */
    public abstract String getDestinationFileName();

    /**
     * The path of the identified artifact, relative to the directory that the module would use.
     *
     * <p>
     * Note that this may be assumed in some cases and not perfectly match the actual path used during resolution.
     * For example, Ivy repositories may use layouts that differ from the default layout, which must be used here
     * because identifiers are not aware of the owning repository.
     * </p>
     */
    public abstract String getFilePath();

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
        if (!(obj instanceof ModuleComponentArtifactIdentifier other)) {
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
