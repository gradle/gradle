/*
 * Copyright 2017 the original author or authors.
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

import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.internal.Deferrable;
import org.gradle.internal.Try;
import org.gradle.internal.execution.ExecutionEngine;
import org.gradle.internal.execution.InputFingerprinter;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationProgressEventEmitter;
import org.gradle.internal.vfs.FileSystemAccess;

import javax.annotation.Nullable;
import java.io.File;

public class DefaultTransformInvocationFactory implements TransformInvocationFactory {

    private final ExecutionEngine executionEngine;
    private final FileSystemAccess fileSystemAccess;
    private final TransformExecutionListener transformExecutionListener;
    private final TransformWorkspaceServices immutableWorkspaceProvider;
    private final FileCollectionFactory fileCollectionFactory;
    private final ProjectStateRegistry projectStateRegistry;
    private final BuildOperationExecutor buildOperationExecutor;
    private final BuildOperationProgressEventEmitter progressEventEmitter;

    public DefaultTransformInvocationFactory(
        ExecutionEngine executionEngine,
        FileSystemAccess fileSystemAccess,
        TransformExecutionListener transformExecutionListener,
        TransformWorkspaceServices immutableWorkspaceProvider,
        FileCollectionFactory fileCollectionFactory,
        ProjectStateRegistry projectStateRegistry,
        BuildOperationExecutor buildOperationExecutor,
        BuildOperationProgressEventEmitter progressEventEmitter
    ) {
        this.executionEngine = executionEngine;
        this.fileSystemAccess = fileSystemAccess;
        this.transformExecutionListener = transformExecutionListener;
        this.immutableWorkspaceProvider = immutableWorkspaceProvider;
        this.fileCollectionFactory = fileCollectionFactory;
        this.projectStateRegistry = projectStateRegistry;
        this.buildOperationExecutor = buildOperationExecutor;
        this.progressEventEmitter = progressEventEmitter;
    }

    @Override
    public Deferrable<Try<ImmutableList<File>>> createInvocation(
        Transform transform,
        File inputArtifact,
        TransformDependencies dependencies,
        TransformStepSubject subject,
        ProjectInternal owningProject,
        InputFingerprinter inputFingerprinter
    ) {
        ProjectInternal producerProject = determineProducerProject(subject);
        TransformWorkspaceServices workspaceServices = determineWorkspaceServices(producerProject);

        UnitOfWork execution;
        if (producerProject == null) {
            execution = new ImmutableTransformExecution(
                transform,
                inputArtifact,
                dependencies,
                subject,
                owningProject,

                transformExecutionListener,
                buildOperationExecutor,
                progressEventEmitter,
                fileCollectionFactory,
                inputFingerprinter,
                fileSystemAccess,
                workspaceServices
            );
        } else {
            execution = new MutableTransformExecution(
                transform,
                inputArtifact,
                dependencies,
                subject,
                owningProject,
                producerProject,

                transformExecutionListener,
                buildOperationExecutor,
                progressEventEmitter,
                fileCollectionFactory,
                inputFingerprinter,
                workspaceServices
            );
        }

        return executionEngine.createRequest(execution)
            .executeDeferred(workspaceServices.getIdentityCache())
            .map(result -> result
                .map(successfulResult -> successfulResult.resolveOutputsForInputArtifact(inputArtifact))
                .mapFailure(failure -> new TransformException(String.format("Execution failed for %s.", execution.getDisplayName()), failure)));
    }

    private TransformWorkspaceServices determineWorkspaceServices(@Nullable ProjectInternal producerProject) {
        if (producerProject == null) {
            return immutableWorkspaceProvider;
        }
        return producerProject.getServices().get(TransformWorkspaceServices.class);
    }

    @Nullable
    private ProjectInternal determineProducerProject(TransformStepSubject subject) {
        ComponentIdentifier componentIdentifier = subject.getInitialComponentIdentifier();
        if (componentIdentifier instanceof ProjectComponentIdentifier) {
            return projectStateRegistry.stateFor((ProjectComponentIdentifier) componentIdentifier).getMutableModel();
        } else {
            return null;
        }
    }
}
