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
import org.gradle.api.internal.artifacts.transform.TransformExecutionResult.TransformWorkspaceResult;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.cache.Cache;
import org.gradle.internal.Deferrable;
import org.gradle.internal.Try;
import org.gradle.internal.buildoption.InternalOptions;
import org.gradle.internal.buildoption.StringInternalOption;
import org.gradle.internal.execution.ExecutionEngine;
import org.gradle.internal.execution.ExecutionEngine.IdentityCacheResult;
import org.gradle.internal.execution.InputFingerprinter;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.UnitOfWork.Identity;
import org.gradle.internal.operations.BuildOperationProgressEventEmitter;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.vfs.FileSystemAccess;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class DefaultTransformInvocationFactory implements TransformInvocationFactory {
    private static final StringInternalOption CACHING_DISABLED_PROPERTY = new StringInternalOption("org.gradle.internal.transform-caching-disabled", null);

    private final ExecutionEngine executionEngine;
    private final FileSystemAccess fileSystemAccess;
    private final InternalOptions internalOptions;
    private final TransformExecutionListener transformExecutionListener;
    private final ImmutableTransformWorkspaceServices immutableWorkspaceServices;
    private final FileCollectionFactory fileCollectionFactory;
    private final ProjectStateRegistry projectStateRegistry;
    private final BuildOperationRunner buildOperationRunner;
    private final BuildOperationProgressEventEmitter progressEventEmitter;

    public DefaultTransformInvocationFactory(
        ExecutionEngine executionEngine,
        FileSystemAccess fileSystemAccess,
        InternalOptions internalOptions,
        TransformExecutionListener transformExecutionListener,
        ImmutableTransformWorkspaceServices immutableWorkspaceServices,
        FileCollectionFactory fileCollectionFactory,
        ProjectStateRegistry projectStateRegistry,
        BuildOperationRunner buildOperationRunner,
        BuildOperationProgressEventEmitter progressEventEmitter
    ) {
        this.executionEngine = executionEngine;
        this.fileSystemAccess = fileSystemAccess;
        this.internalOptions = internalOptions;
        this.transformExecutionListener = transformExecutionListener;
        this.immutableWorkspaceServices = immutableWorkspaceServices;
        this.fileCollectionFactory = fileCollectionFactory;
        this.projectStateRegistry = projectStateRegistry;
        this.buildOperationRunner = buildOperationRunner;
        this.progressEventEmitter = progressEventEmitter;
    }

    @Override
    public Deferrable<Try<ImmutableList<File>>> createInvocation(
        Transform transform,
        File inputArtifact,
        TransformDependencies dependencies,
        TransformStepSubject subject,
        InputFingerprinter inputFingerprinter
    ) {
        ProjectInternal producerProject = determineProducerProject(subject);

        Cache<Identity, IdentityCacheResult<TransformWorkspaceResult>> identityCache;
        UnitOfWork execution;

        boolean cachingDisabledByProperty = isCachingDisabledByProperty(transform);

        // TODO This is a workaround for script compilation that is triggered via the "early" execution
        //      engine created in DependencyManagementBuildScopeServices. We should unify the execution
        //      engines instead.
        ExecutionEngine effectiveEngine;
        if (producerProject == null) {
            // Non-project-bound transforms run in a global immutable workspace,
            // and are identified by a non-normalized identity
            // See comments on NonNormalizedIdentityImmutableTransformExecution
            identityCache = immutableWorkspaceServices.getIdentityCache();
            execution = new NonNormalizedIdentityImmutableTransformExecution(
                transform,
                inputArtifact,
                dependencies,
                subject,

                transformExecutionListener,
                buildOperationRunner,
                progressEventEmitter,
                fileCollectionFactory,
                inputFingerprinter,
                fileSystemAccess,
                immutableWorkspaceServices.getWorkspaceProvider(),

                cachingDisabledByProperty
            );
            effectiveEngine = executionEngine;
        } else {
            effectiveEngine = producerProject.getServices().get(ExecutionEngine.class);
            if (!transform.requiresInputChanges()) {
                // Non-incremental project artifact transforms also run in an immutable workspace
                identityCache = immutableWorkspaceServices.getIdentityCache();
                execution = new NormalizedIdentityImmutableTransformExecution(
                    transform,
                    inputArtifact,
                    dependencies,
                    subject,

                    transformExecutionListener,
                    buildOperationRunner,
                    progressEventEmitter,
                    fileCollectionFactory,
                    inputFingerprinter,
                    immutableWorkspaceServices.getWorkspaceProvider(),

                    cachingDisabledByProperty
                );
            } else {
                // Incremental project artifact transforms run in project-bound mutable workspace
                MutableTransformWorkspaceServices workspaceServices = producerProject.getServices().get(MutableTransformWorkspaceServices.class);
                identityCache = workspaceServices.getIdentityCache();
                execution = new MutableTransformExecution(
                    transform,
                    inputArtifact,
                    dependencies,
                    subject,
                    producerProject,

                    transformExecutionListener,
                    buildOperationRunner,
                    progressEventEmitter,
                    fileCollectionFactory,
                    inputFingerprinter,
                    workspaceServices.getWorkspaceProvider(),

                    cachingDisabledByProperty
                );
            }
        }
        return effectiveEngine.createRequest(execution)
            .executeDeferred(identityCache)
            .map(result -> result
                .map(successfulResult -> successfulResult.resolveForInputArtifact(inputArtifact))
                .mapFailure(failure -> new TransformException(String.format("Execution failed for %s.", execution.getDisplayName()), failure)));
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

    private boolean isCachingDisabledByProperty(Transform transform) {
        String experimentalProperty = internalOptions.getOption(CACHING_DISABLED_PROPERTY).get();
        if (experimentalProperty != null) {
            if (experimentalProperty.isEmpty() || experimentalProperty.equals("true")) {
                return true;
            }
            List<String> disabledTransformClasses = Arrays.asList(experimentalProperty.split(","));
            return disabledTransformClasses.contains(transform.getImplementationClass().getName());
        }

        return false;
    }
}
