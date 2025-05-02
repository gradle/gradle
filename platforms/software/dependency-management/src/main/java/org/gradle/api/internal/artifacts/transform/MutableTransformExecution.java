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
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.internal.execution.InputFingerprinter;
import org.gradle.internal.execution.MutableUnitOfWork;
import org.gradle.internal.execution.workspace.MutableWorkspaceProvider;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.operations.BuildOperationProgressEventEmitter;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.snapshot.ValueSnapshot;

import java.io.File;
import java.util.Map;

class MutableTransformExecution extends AbstractTransformExecution implements MutableUnitOfWork {
    private final String rootProjectLocation;
    private final String producerBuildTreePath;
    private final MutableWorkspaceProvider workspaceProvider;

    public MutableTransformExecution(
        Transform transform,
        File inputArtifact,
        TransformDependencies dependencies,
        TransformStepSubject subject,
        ProjectInternal producerProject,

        TransformExecutionListener transformExecutionListener,
        BuildOperationRunner buildOperationRunner,
        BuildOperationProgressEventEmitter progressEventEmitter,
        FileCollectionFactory fileCollectionFactory,
        InputFingerprinter inputFingerprinter,
        MutableWorkspaceProvider workspaceProvider,

        boolean disableCachingByProperty
    ) {
        super(
            transform, inputArtifact, dependencies, subject,
            transformExecutionListener, buildOperationRunner, progressEventEmitter, fileCollectionFactory, inputFingerprinter,
            disableCachingByProperty
        );
        this.rootProjectLocation = producerProject.getRootDir().getAbsolutePath() + File.separator;
        this.producerBuildTreePath = producerProject.getBuildTreePath();
        this.workspaceProvider = workspaceProvider;
    }

    @Override
    public MutableWorkspaceProvider getWorkspaceProvider() {
        return workspaceProvider;
    }

    @Override
    protected TransformWorkspaceIdentity createIdentity(Map<String, ValueSnapshot> identityInputs, Map<String, CurrentFileCollectionFingerprint> identityFileInputs) {
        return TransformWorkspaceIdentity.createMutable(
            normalizeAbsolutePath(inputArtifact.getAbsolutePath()),
            producerBuildTreePath,
            identityInputs.get(AbstractTransformExecution.SECONDARY_INPUTS_HASH_PROPERTY_NAME),
            identityFileInputs.get(AbstractTransformExecution.DEPENDENCIES_PROPERTY_NAME).getHash()
        );
    }

    @Override
    public void visitRegularInputs(InputVisitor visitor) {
        visitInputArtifact(visitor);
    }

    private String normalizeAbsolutePath(String path) {
        // We try to normalize the absolute path, so the workspace id is stable between machines for cacheable transforms.
        if (path.startsWith(rootProjectLocation)) {
            return path.substring(rootProjectLocation.length());
        }
        return path;
    }
}
