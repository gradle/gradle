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
import org.gradle.internal.execution.InputFingerprinter;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.vfs.FileSystemAccess;

import java.io.File;
import java.util.Map;

class ImmutableTransformExecution extends AbstractTransformExecution {
    private static final String INPUT_ARTIFACT_SNAPSHOT_PROPERTY_NAME = "inputArtifactSnapshot";

    private final FileSystemAccess fileSystemAccess;

    public ImmutableTransformExecution(
        Transform transform,
        File inputArtifact,
        TransformDependencies dependencies,
        TransformStepSubject subject,

        TransformExecutionListener transformExecutionListener,
        BuildOperationExecutor buildOperationExecutor,
        FileCollectionFactory fileCollectionFactory,
        InputFingerprinter inputFingerprinter,
        FileSystemAccess fileSystemAccess,
        TransformWorkspaceServices workspaceServices
    ) {
        super(
            transform, inputArtifact, dependencies, subject,
            transformExecutionListener, buildOperationExecutor, fileCollectionFactory, inputFingerprinter, workspaceServices
        );
        this.fileSystemAccess = fileSystemAccess;
    }

    @Override
    public void visitIdentityInputs(InputVisitor visitor) {
        super.visitIdentityInputs(visitor);
        // This is a performance hack. We could use the regular fingerprint of the input artifact, but that takes longer than
        // capturing the normalized path and the snapshot of the raw contents, so we are using these to determine the identity
        FileSystemLocationSnapshot inputArtifactSnapshot = fileSystemAccess.read(inputArtifact.getAbsolutePath());
        visitor.visitInputProperty(INPUT_ARTIFACT_SNAPSHOT_PROPERTY_NAME, inputArtifactSnapshot::getHash);
    }

    @Override
    public Identity identify(Map<String, ValueSnapshot> identityInputs, Map<String, CurrentFileCollectionFingerprint> identityFileInputs) {
        return new ImmutableTransformWorkspaceIdentity(
            identityInputs.get(AbstractTransformExecution.INPUT_ARTIFACT_PATH_PROPERTY_NAME),
            identityInputs.get(INPUT_ARTIFACT_SNAPSHOT_PROPERTY_NAME),
            identityInputs.get(AbstractTransformExecution.SECONDARY_INPUTS_HASH_PROPERTY_NAME),
            identityFileInputs.get(AbstractTransformExecution.DEPENDENCIES_PROPERTY_NAME).getHash()
        );
    }
}
