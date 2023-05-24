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
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.internal.file.DefaultFileSystemLocation;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.api.internal.provider.Providers;
import org.gradle.api.provider.Provider;
import org.gradle.internal.Deferrable;
import org.gradle.internal.Try;
import org.gradle.internal.execution.ExecutionEngine;
import org.gradle.internal.execution.InputFingerprinter;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.caching.CachingDisabledReason;
import org.gradle.internal.execution.caching.CachingDisabledReasonCategory;
import org.gradle.internal.execution.history.OverlappingOutputs;
import org.gradle.internal.execution.history.changes.InputChangesInternal;
import org.gradle.internal.execution.model.InputNormalizer;
import org.gradle.internal.execution.workspace.WorkspaceProvider;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationProgressEventEmitter;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.internal.operations.UncategorizedBuildOperations;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.vfs.FileSystemAccess;
import org.gradle.operations.dependencies.transforms.IdentifyTransformExecutionProgressDetails;

import javax.annotation.Nullable;
import javax.annotation.OverridingMethodsMustInvokeSuper;
import java.io.File;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import static org.gradle.internal.file.TreeType.DIRECTORY;
import static org.gradle.internal.file.TreeType.FILE;
import static org.gradle.internal.properties.InputBehavior.INCREMENTAL;
import static org.gradle.internal.properties.InputBehavior.NON_INCREMENTAL;

public class DefaultTransformInvocationFactory implements TransformInvocationFactory {
    private static final CachingDisabledReason NOT_CACHEABLE = new CachingDisabledReason(CachingDisabledReasonCategory.NOT_CACHEABLE, "Caching not enabled.");
    private static final String INPUT_ARTIFACT_PROPERTY_NAME = "inputArtifact";
    private static final String INPUT_ARTIFACT_PATH_PROPERTY_NAME = "inputArtifactPath";
    private static final String INPUT_ARTIFACT_SNAPSHOT_PROPERTY_NAME = "inputArtifactSnapshot";
    private static final String DEPENDENCIES_PROPERTY_NAME = "inputArtifactDependencies";
    private static final String SECONDARY_INPUTS_HASH_PROPERTY_NAME = "inputPropertiesHash";
    private static final String OUTPUT_DIRECTORY_PROPERTY_NAME = "outputDirectory";
    private static final String RESULTS_FILE_PROPERTY_NAME = "resultsFile";

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

    private static class ImmutableTransformExecution extends AbstractTransformExecution {
        private final FileSystemAccess fileSystemAccess;

        public ImmutableTransformExecution(
            Transform transform,
            File inputArtifact,
            TransformDependencies dependencies,
            TransformStepSubject subject,

            ProjectInternal owningProject, TransformExecutionListener transformExecutionListener,
            BuildOperationExecutor buildOperationExecutor,
            BuildOperationProgressEventEmitter progressEventEmitter,
            FileCollectionFactory fileCollectionFactory,
            InputFingerprinter inputFingerprinter,
            FileSystemAccess fileSystemAccess,
            TransformWorkspaceServices workspaceServices
        ) {
            super(
                transform, inputArtifact, dependencies, subject, owningProject,
                transformExecutionListener, buildOperationExecutor, progressEventEmitter, fileCollectionFactory, inputFingerprinter, workspaceServices
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
            ImmutableTransformWorkspaceIdentity transformWorkspaceIdentity = new ImmutableTransformWorkspaceIdentity(
                identityInputs.get(INPUT_ARTIFACT_PATH_PROPERTY_NAME),
                identityInputs.get(INPUT_ARTIFACT_SNAPSHOT_PROPERTY_NAME),
                identityInputs.get(SECONDARY_INPUTS_HASH_PROPERTY_NAME),
                identityFileInputs.get(DEPENDENCIES_PROPERTY_NAME).getHash()
            );
            emitIdentifyTransformExecutionProgressDetails(transformWorkspaceIdentity);
            return transformWorkspaceIdentity;
        }
    }

    private static class MutableTransformExecution extends AbstractTransformExecution {
        public MutableTransformExecution(
            Transform transform,
            File inputArtifact,
            TransformDependencies dependencies,
            TransformStepSubject subject,
            ProjectInternal owningProject,

            TransformExecutionListener transformExecutionListener,
            BuildOperationExecutor buildOperationExecutor,
            BuildOperationProgressEventEmitter progressEventEmitter,
            FileCollectionFactory fileCollectionFactory,
            InputFingerprinter inputFingerprinter,
            TransformWorkspaceServices workspaceServices
        ) {
            super(
                transform, inputArtifact, dependencies, subject, owningProject,
                transformExecutionListener, buildOperationExecutor, progressEventEmitter, fileCollectionFactory, inputFingerprinter, workspaceServices
            );
        }

        @Override
        public Identity identify(Map<String, ValueSnapshot> identityInputs, Map<String, CurrentFileCollectionFingerprint> identityFileInputs) {
            MutableTransformWorkspaceIdentity transformWorkspaceIdentity = new MutableTransformWorkspaceIdentity(
                inputArtifact.getAbsolutePath(),
                identityInputs.get(SECONDARY_INPUTS_HASH_PROPERTY_NAME),
                identityFileInputs.get(DEPENDENCIES_PROPERTY_NAME).getHash()
            );
            emitIdentifyTransformExecutionProgressDetails(transformWorkspaceIdentity);
            return transformWorkspaceIdentity;
        }
    }

    private abstract static class AbstractTransformExecution implements UnitOfWork {
        protected final Transform transform;
        protected final File inputArtifact;
        private final TransformDependencies dependencies;
        private final TransformStepSubject subject;
        private final ProjectInternal owningProject;

        private final TransformExecutionListener transformExecutionListener;
        private final BuildOperationExecutor buildOperationExecutor;
        private final BuildOperationProgressEventEmitter progressEventEmitter;
        private final FileCollectionFactory fileCollectionFactory;

        private final Provider<FileSystemLocation> inputArtifactProvider;
        protected final InputFingerprinter inputFingerprinter;
        private final TransformWorkspaceServices workspaceServices;

        public AbstractTransformExecution(
            Transform transform,
            File inputArtifact,
            TransformDependencies dependencies,
            TransformStepSubject subject,
            ProjectInternal owningProject,

            TransformExecutionListener transformExecutionListener,
            BuildOperationExecutor buildOperationExecutor,
            BuildOperationProgressEventEmitter progressEventEmitter,
            FileCollectionFactory fileCollectionFactory,
            InputFingerprinter inputFingerprinter,
            TransformWorkspaceServices workspaceServices
        ) {
            this.transform = transform;
            this.inputArtifact = inputArtifact;
            this.dependencies = dependencies;
            this.inputArtifactProvider = Providers.of(new DefaultFileSystemLocation(inputArtifact));
            this.subject = subject;
            this.owningProject = owningProject;
            this.transformExecutionListener = transformExecutionListener;

            this.buildOperationExecutor = buildOperationExecutor;
            this.progressEventEmitter = progressEventEmitter;
            this.fileCollectionFactory = fileCollectionFactory;
            this.inputFingerprinter = inputFingerprinter;
            this.workspaceServices = workspaceServices;
        }

        @Override
        public WorkOutput execute(ExecutionRequest executionRequest) {
            transformExecutionListener.beforeTransformExecution(transform, subject);
            try {
                return executeWithinTransformerListener(executionRequest);
            } finally {
                transformExecutionListener.afterTransformExecution(transform, subject);
            }
        }

        private WorkOutput executeWithinTransformerListener(ExecutionRequest executionRequest) {
            TransformExecutionResult result = buildOperationExecutor.call(new CallableBuildOperation<TransformExecutionResult>() {
                @Override
                public TransformExecutionResult call(BuildOperationContext context) {
                    File workspace = executionRequest.getWorkspace();
                    InputChangesInternal inputChanges = executionRequest.getInputChanges().orElse(null);
                    TransformExecutionResult result = transform.transform(inputArtifactProvider, getOutputDir(workspace), dependencies, inputChanges);
                    TransformExecutionResultSerializer resultSerializer = new TransformExecutionResultSerializer(getOutputDir(workspace));
                    resultSerializer.writeToFile(getResultsFile(workspace), result);
                    return result;
                }

                @Override
                public BuildOperationDescriptor.Builder description() {
                    String displayName = transform.getDisplayName() + " " + inputArtifact.getName();
                    return BuildOperationDescriptor.displayName(displayName)
                        .metadata(UncategorizedBuildOperations.TRANSFORM_ACTION)
                        .progressDisplayName(displayName);
                }
            });

            return new WorkOutput() {
                @Override
                public WorkResult getDidWork() {
                    return WorkResult.DID_WORK;
                }

                @Override
                public Object getOutput() {
                    return result;
                }
            };
        }

        @Override
        public Object loadAlreadyProducedOutput(File workspace) {
            TransformExecutionResultSerializer resultSerializer = new TransformExecutionResultSerializer(getOutputDir(workspace));
            return resultSerializer.readResultsFile(getResultsFile(workspace));
        }

        @Override
        public WorkspaceProvider getWorkspaceProvider() {
            return workspaceServices.getWorkspaceProvider();
        }

        @Override
        public InputFingerprinter getInputFingerprinter() {
            return inputFingerprinter;
        }

        private static File getOutputDir(File workspace) {
            return new File(workspace, "transformed");
        }

        private static File getResultsFile(File workspace) {
            return new File(workspace, "results.bin");
        }

        @Override
        public Optional<Duration> getTimeout() {
            return Optional.empty();
        }

        @Override
        public ExecutionBehavior getExecutionBehavior() {
            return transform.requiresInputChanges()
                ? ExecutionBehavior.INCREMENTAL
                : ExecutionBehavior.NON_INCREMENTAL;
        }

        @Override
        public void visitImplementations(ImplementationVisitor visitor) {
            visitor.visitImplementation(transform.getImplementationClass());
        }

        @Override
        @OverridingMethodsMustInvokeSuper
        public void visitIdentityInputs(InputVisitor visitor) {
            // Emulate secondary inputs as a single property for now
            visitor.visitInputProperty(SECONDARY_INPUTS_HASH_PROPERTY_NAME, transform::getSecondaryInputHash);
            visitor.visitInputProperty(INPUT_ARTIFACT_PATH_PROPERTY_NAME, () ->
                // We always need the name as an input to the artifact transform,
                // since it is part of the ComponentArtifactIdentifier returned by the transform.
                // For absolute paths, the name is already part of the normalized path,
                // and for all the other normalization strategies we use the name directly.
                transform.getInputArtifactNormalizer() == InputNormalizer.ABSOLUTE_PATH
                    ? inputArtifact.getAbsolutePath()
                    : inputArtifact.getName());
            visitor.visitInputFileProperty(DEPENDENCIES_PROPERTY_NAME, NON_INCREMENTAL,
                new InputFileValueSupplier(
                    dependencies,
                    transform.getInputArtifactDependenciesNormalizer(),
                    transform.getInputArtifactDependenciesDirectorySensitivity(),
                    transform.getInputArtifactDependenciesLineEndingNormalization(),
                    () -> dependencies.getFiles()
                        .orElse(FileCollectionFactory.empty())));
        }

        @Override
        @OverridingMethodsMustInvokeSuper
        public void visitRegularInputs(InputVisitor visitor) {
            visitor.visitInputFileProperty(INPUT_ARTIFACT_PROPERTY_NAME, INCREMENTAL,
                new InputFileValueSupplier(
                    inputArtifactProvider,
                    transform.getInputArtifactNormalizer(),
                    transform.getInputArtifactDirectorySensitivity(),
                    transform.getInputArtifactLineEndingNormalization(),
                    () -> fileCollectionFactory.fixed(inputArtifact)));
        }

        @Override
        public void visitOutputs(File workspace, OutputVisitor visitor) {
            File outputDir = getOutputDir(workspace);
            File resultsFile = getResultsFile(workspace);
            visitor.visitOutputProperty(OUTPUT_DIRECTORY_PROPERTY_NAME, DIRECTORY,
                OutputFileValueSupplier.fromStatic(outputDir, fileCollectionFactory.fixed(outputDir)));
            visitor.visitOutputProperty(RESULTS_FILE_PROPERTY_NAME, FILE,
                OutputFileValueSupplier.fromStatic(resultsFile, fileCollectionFactory.fixed(resultsFile)));
        }

        protected void emitIdentifyTransformExecutionProgressDetails(TransformWorkspaceIdentity transformWorkspaceIdentity) {
            progressEventEmitter.emitNowIfCurrent(new DefaultIdentifyTransformExecutionProgressDetails(transformWorkspaceIdentity, transform, owningProject));
        }

        @Override
        public Optional<CachingDisabledReason> shouldDisableCaching(@Nullable OverlappingOutputs detectedOverlappingOutputs) {
            return transform.isCacheable()
                ? Optional.empty()
                : Optional.of(NOT_CACHEABLE);
        }

        @Override
        public String getDisplayName() {
            return transform.getDisplayName() + ": " + inputArtifact;
        }
    }

    private interface TransformWorkspaceIdentity extends UnitOfWork.Identity {
        ValueSnapshot getSecondaryInputsSnapshot();
    }

    private static class ImmutableTransformWorkspaceIdentity implements TransformWorkspaceIdentity {
        private final ValueSnapshot inputArtifactPath;
        private final ValueSnapshot inputArtifactSnapshot;
        private final ValueSnapshot secondaryInputsSnapshot;
        private final HashCode dependenciesHash;

        public ImmutableTransformWorkspaceIdentity(ValueSnapshot inputArtifactPath, ValueSnapshot inputArtifactSnapshot, ValueSnapshot secondaryInputsSnapshot, HashCode dependenciesHash) {
            this.inputArtifactPath = inputArtifactPath;
            this.inputArtifactSnapshot = inputArtifactSnapshot;
            this.secondaryInputsSnapshot = secondaryInputsSnapshot;
            this.dependenciesHash = dependenciesHash;
        }

        @Override
        public String getUniqueId() {
            Hasher hasher = Hashing.newHasher();
            inputArtifactPath.appendToHasher(hasher);
            inputArtifactSnapshot.appendToHasher(hasher);
            secondaryInputsSnapshot.appendToHasher(hasher);
            hasher.putHash(dependenciesHash);
            return hasher.hash().toString();
        }

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

    public static class MutableTransformWorkspaceIdentity implements TransformWorkspaceIdentity {
        private final String inputArtifactAbsolutePath;
        private final ValueSnapshot secondaryInputsSnapshot;
        private final HashCode dependenciesHash;

        public MutableTransformWorkspaceIdentity(String inputArtifactAbsolutePath, ValueSnapshot secondaryInputsSnapshot, HashCode dependenciesHash) {
            this.inputArtifactAbsolutePath = inputArtifactAbsolutePath;
            this.secondaryInputsSnapshot = secondaryInputsSnapshot;
            this.dependenciesHash = dependenciesHash;
        }

        @Override
        public String getUniqueId() {
            Hasher hasher = Hashing.newHasher();
            hasher.putString(inputArtifactAbsolutePath);
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

    private static class DefaultIdentifyTransformExecutionProgressDetails implements IdentifyTransformExecutionProgressDetails {

        private final TransformWorkspaceIdentity transformWorkspaceIdentity;
        private final Transform transform;
        private final ProjectInternal owningProject;

        public DefaultIdentifyTransformExecutionProgressDetails(
            TransformWorkspaceIdentity transformWorkspaceIdentity,
            Transform transform,
            ProjectInternal owningProject
        ) {
            this.transformWorkspaceIdentity = transformWorkspaceIdentity;
            this.transform = transform;
            this.owningProject = owningProject;
        }

        @Override
        public String getUniqueId() {
            return transformWorkspaceIdentity.getUniqueId();
        }

        @Override
        public String getConsumerBuildPath() {
            return owningProject.getBuildPath().toString();
        }

        @Override
        public String getConsumerProjectPath() {
            return owningProject.getProjectPath().toString();
        }

        @Override
        public Map<String, String> getFromAttributes() {
            return AttributesToMapConverter.convertToMap(transform.getFromAttributes());
        }

        @Override
        public Map<String, String> getToAttributes() {
            return AttributesToMapConverter.convertToMap(transform.getToAttributes());
        }

        @Override
        public byte[] getSecondaryInputValueHashBytes() {
            Hasher hasher = Hashing.newHasher();
            transformWorkspaceIdentity.getSecondaryInputsSnapshot().appendToHasher(hasher);
            return hasher.hash().toByteArray();
        }
    }
}
