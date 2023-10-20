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

import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.snapshot.ValueSnapshot;

class TransformWorkspaceIdentity implements UnitOfWork.Identity {
    private final ValueSnapshot secondaryInputsSnapshot;
    private final String uniqueId;

    private TransformWorkspaceIdentity(ValueSnapshot secondaryInputsSnapshot, HashCode uniqueId) {
        this.uniqueId = uniqueId.toString();
        this.secondaryInputsSnapshot = secondaryInputsSnapshot;
    }

    public ValueSnapshot getSecondaryInputsSnapshot() {
        return secondaryInputsSnapshot;
    }

    @Override
    public String getUniqueId() {
        return uniqueId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        TransformWorkspaceIdentity that = (TransformWorkspaceIdentity) o;

        return uniqueId.equals(that.uniqueId);
    }

    @Override
    public int hashCode() {
        return uniqueId.hashCode();
    }

    public static TransformWorkspaceIdentity createMutable(
        String normalizedInputArtifactPath,
        String producerBuildTreePath,
        ValueSnapshot secondaryInputsSnapshot,
        HashCode dependenciesHash
    ) {
        Hasher hasher = Hashing.newHasher();
        hasher.putString(normalizedInputArtifactPath);
        hasher.putString(producerBuildTreePath);
        secondaryInputsSnapshot.appendToHasher(hasher);
        hasher.putHash(dependenciesHash);
        return new TransformWorkspaceIdentity(secondaryInputsSnapshot, hasher.hash());
    }

    public static TransformWorkspaceIdentity createNonNormalizedImmutable(
        ValueSnapshot inputArtifactPath,
        ValueSnapshot inputArtifactSnapshot,
        ValueSnapshot secondaryInputsSnapshot,
        HashCode dependenciesHash
    ) {
        Hasher hasher = Hashing.newHasher();
        inputArtifactPath.appendToHasher(hasher);
        inputArtifactSnapshot.appendToHasher(hasher);
        secondaryInputsSnapshot.appendToHasher(hasher);
        hasher.putHash(dependenciesHash);
        return new TransformWorkspaceIdentity(secondaryInputsSnapshot, hasher.hash());
    }

    public static TransformWorkspaceIdentity createNormalizedImmutable(
        ValueSnapshot inputArtifactPath,
        CurrentFileCollectionFingerprint inputArtifactFingerprint,
        ValueSnapshot secondaryInputsSnapshot,
        HashCode dependenciesHash
    ) {
        Hasher hasher = Hashing.newHasher();
        inputArtifactPath.appendToHasher(hasher);
        hasher.putHash(inputArtifactFingerprint.getHash());
        secondaryInputsSnapshot.appendToHasher(hasher);
        hasher.putHash(dependenciesHash);
        return new TransformWorkspaceIdentity(secondaryInputsSnapshot, hasher.hash());
    }
}
