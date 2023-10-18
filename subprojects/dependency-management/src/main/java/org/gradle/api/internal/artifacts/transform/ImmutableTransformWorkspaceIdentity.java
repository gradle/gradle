/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.internal.artifacts.transform;

import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.snapshot.ValueSnapshot;

class ImmutableTransformWorkspaceIdentity implements TransformWorkspaceIdentity {
    private final ValueSnapshot inputArtifactPath;
    private final HashCode inputArtifactSnapshot;
    private final ValueSnapshot secondaryInputsSnapshot;
    private final HashCode dependenciesHash;

    public ImmutableTransformWorkspaceIdentity(ValueSnapshot inputArtifactPath, HashCode inputArtifactSnapshot, ValueSnapshot secondaryInputsSnapshot, HashCode dependenciesHash) {
        this.inputArtifactPath = inputArtifactPath;
        this.inputArtifactSnapshot = inputArtifactSnapshot;
        this.secondaryInputsSnapshot = secondaryInputsSnapshot;
        this.dependenciesHash = dependenciesHash;
    }

    @Override
    public String getUniqueId() {
        Hasher hasher = Hashing.newHasher();
        inputArtifactPath.appendToHasher(hasher);
        hasher.putHash(inputArtifactSnapshot);
        secondaryInputsSnapshot.appendToHasher(hasher);
        hasher.putHash(dependenciesHash);
        return hasher.hash().toString();
    }

    @Override
    public ValueSnapshot getSecondaryInputsSnapshot() {
        return secondaryInputsSnapshot;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ImmutableTransformWorkspaceIdentity that = (ImmutableTransformWorkspaceIdentity) o;

        if (!inputArtifactPath.equals(that.inputArtifactPath)) {
            return false;
        }
        if (!inputArtifactSnapshot.equals(that.inputArtifactSnapshot)) {
            return false;
        }
        if (!secondaryInputsSnapshot.equals(that.secondaryInputsSnapshot)) {
            return false;
        }
        return dependenciesHash.equals(that.dependenciesHash);
    }

    @Override
    public int hashCode() {
        int result = inputArtifactPath.hashCode();
        result = 31 * result + inputArtifactSnapshot.hashCode();
        result = 31 * result + secondaryInputsSnapshot.hashCode();
        result = 31 * result + dependenciesHash.hashCode();
        return result;
    }
}
