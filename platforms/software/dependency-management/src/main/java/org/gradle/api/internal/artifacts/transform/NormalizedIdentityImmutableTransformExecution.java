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
import org.gradle.internal.operations.BuildOperationProgressEventEmitter;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.snapshot.ValueSnapshot;

import java.io.File;
import java.util.Map;

class NormalizedIdentityImmutableTransformExecution extends AbstractTransformExecution implements ImmutableUnitOfWork {
    private final ImmutableWorkspaceProvider workspaceProvider;

    public NormalizedIdentityImmutableTransformExecution(
        Transform transform,
        File inputArtifact,
        TransformDependencies dependencies,
        TransformStepSubject subject,

        TransformExecutionListener transformExecutionListener,
        BuildOperationRunner buildOperationRunner,
        BuildOperationProgressEventEmitter progressEventEmitter,
        FileCollectionFactory fileCollectionFactory,
        InputFingerprinter inputFingerprinter,
        ImmutableWorkspaceProvider workspaceProvider,

        boolean disableCachingByProperty
    ) {
        super(
            transform, inputArtifact, dependencies, subject,
            transformExecutionListener, buildOperationRunner, progressEventEmitter, fileCollectionFactory, inputFingerprinter,
            disableCachingByProperty
        );
        this.workspaceProvider = workspaceProvider;
    }

    @Override
    public ImmutableWorkspaceProvider getWorkspaceProvider() {
        return workspaceProvider;
    }

    @Override
    protected TransformWorkspaceIdentity createIdentity(Map<String, ValueSnapshot> identityInputs, Map<String, CurrentFileCollectionFingerprint> identityFileInputs) {
        return TransformWorkspaceIdentity.createNormalizedImmutable(
            identityInputs.get(INPUT_ARTIFACT_PATH_PROPERTY_NAME),
            identityFileInputs.get(INPUT_ARTIFACT_PROPERTY_NAME),
            identityInputs.get(SECONDARY_INPUTS_HASH_PROPERTY_NAME),
            identityFileInputs.get(DEPENDENCIES_PROPERTY_NAME).getHash()
        );
    }

    @Override
    public void visitIdentityInputs(InputVisitor visitor) {
        super.visitIdentityInputs(visitor);
        visitInputArtifact(visitor);
    }
}
