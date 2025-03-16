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

import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.internal.execution.ImmutableUnitOfWork;
import org.gradle.internal.execution.InputFingerprinter;
import org.gradle.internal.execution.workspace.ImmutableWorkspaceProvider;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.operations.BuildOperationProgressEventEmitter;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.vfs.FileSystemAccess;

import java.io.File;
import java.util.Map;

class NonNormalizedIdentityImmutableTransformExecution extends AbstractTransformExecution implements ImmutableUnitOfWork  {
    private final FileSystemAccess fileSystemAccess;
    private final ImmutableWorkspaceProvider workspaceProvider;

    public NonNormalizedIdentityImmutableTransformExecution(
        Transform transform,
        File inputArtifact,
        TransformDependencies dependencies,
        TransformStepSubject subject,

        TransformExecutionListener transformExecutionListener,
        BuildOperationRunner buildOperationRunner,
        BuildOperationProgressEventEmitter progressEventEmitter,
        FileCollectionFactory fileCollectionFactory,
        InputFingerprinter inputFingerprinter,
        FileSystemAccess fileSystemAccess,
        ImmutableWorkspaceProvider workspaceProvider,

        boolean disableCachingByProperty
    ) {
        super(
            transform, inputArtifact, dependencies, subject,
            transformExecutionListener, buildOperationRunner, progressEventEmitter, fileCollectionFactory, inputFingerprinter,
            disableCachingByProperty
        );
        this.fileSystemAccess = fileSystemAccess;
        this.workspaceProvider = workspaceProvider;
    }

    @Override
    public ImmutableWorkspaceProvider getWorkspaceProvider() {
        return workspaceProvider;
    }

    @Override
    protected TransformWorkspaceIdentity createIdentity(Map<String, ValueSnapshot> identityInputs, Map<String, CurrentFileCollectionFingerprint> identityFileInputs) {
        // This is a performance hack. We could use the regular fingerprint of the input artifact, but that takes longer than
        // capturing the normalized path and the snapshot of the raw contents, so we are using these to determine the identity.
        // We do this because external artifact transforms typically need to identify themselves redundantly many times during a build.
        // Once we migrate to all-scheduled transforms we should consider if we can avoid having this optimization and use only normalized inputs.
        //
        // Note that we are not capturing this value in the actual inputs of the work; doing so would cause unnecessary cache misses.
        // This is why the hash is captured here and not in visitIdentityInputs().
        FileSystemLocationSnapshot inputArtifactSnapshot = fileSystemAccess.read(inputArtifact.getAbsolutePath());
        HashCode inputArtifactSnapshotHash = inputArtifactSnapshot.getHash();

        return TransformWorkspaceIdentity.createNonNormalizedImmutable(
            identityInputs.get(INPUT_ARTIFACT_PATH_PROPERTY_NAME),
            inputArtifactSnapshotHash,
            identityInputs.get(SECONDARY_INPUTS_HASH_PROPERTY_NAME),
            identityFileInputs.get(DEPENDENCIES_PROPERTY_NAME).getHash()
        );
    }

    @Override
    public void visitRegularInputs(InputVisitor visitor) {
        visitInputArtifact(visitor);
    }
}
