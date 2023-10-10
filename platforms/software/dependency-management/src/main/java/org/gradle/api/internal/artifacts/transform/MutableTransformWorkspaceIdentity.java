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

class MutableTransformWorkspaceIdentity implements TransformWorkspaceIdentity {
    private final String inputArtifactAbsolutePath;
    private final String producerBuildTreePath;
    private final ValueSnapshot secondaryInputsSnapshot;
    private final HashCode dependenciesHash;

    public MutableTransformWorkspaceIdentity(String inputArtifactAbsolutePath, String producerBuildTreePath, ValueSnapshot secondaryInputsSnapshot, HashCode dependenciesHash) {
        this.inputArtifactAbsolutePath = inputArtifactAbsolutePath;
        this.producerBuildTreePath = producerBuildTreePath;
        this.secondaryInputsSnapshot = secondaryInputsSnapshot;
        this.dependenciesHash = dependenciesHash;
    }

    @Override
    public String getUniqueId() {
        Hasher hasher = Hashing.newHasher();
        hasher.putString(inputArtifactAbsolutePath);
        hasher.putString(producerBuildTreePath);
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

        MutableTransformWorkspaceIdentity that = (MutableTransformWorkspaceIdentity) o;

        if (!secondaryInputsSnapshot.equals(that.secondaryInputsSnapshot)) {
            return false;
        }
        if (!dependenciesHash.equals(that.dependenciesHash)) {
            return false;
        }
        if (!producerBuildTreePath.equals(that.producerBuildTreePath)) {
            return false;
        }
        return inputArtifactAbsolutePath.equals(that.inputArtifactAbsolutePath);
    }

    @Override
    public int hashCode() {
        int result = inputArtifactAbsolutePath.hashCode();
        result = 31 * result + secondaryInputsSnapshot.hashCode();
        result = 31 * result + dependenciesHash.hashCode();
        return result;
    }
}
