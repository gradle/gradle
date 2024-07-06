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

import com.google.common.collect.ImmutableSortedSet;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.internal.file.DefaultFileSystemLocation;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.provider.Providers;
import org.gradle.api.internal.tasks.properties.DefaultInputFilePropertySpec;
import org.gradle.api.internal.tasks.properties.InputFilePropertySpec;
import org.gradle.api.provider.Provider;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.execution.InputFingerprinter;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.caching.CachingDisabledReason;
import org.gradle.internal.execution.caching.CachingDisabledReasonCategory;
import org.gradle.internal.execution.caching.CachingState;
import org.gradle.internal.execution.history.OverlappingOutputs;
import org.gradle.internal.execution.history.changes.InputChangesInternal;
import org.gradle.internal.execution.model.InputNormalizer;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationProgressEventEmitter;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.internal.operations.UncategorizedBuildOperations;
import org.gradle.internal.properties.PropertyValue;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.operations.dependencies.transforms.ExecuteTransformActionBuildOperationType;
import org.gradle.operations.dependencies.transforms.IdentifyTransformExecutionProgressDetails;
import org.gradle.operations.dependencies.transforms.SnapshotTransformInputsBuildOperationType;

import javax.annotation.Nullable;
import javax.annotation.OverridingMethodsMustInvokeSuper;
import java.io.File;
import java.util.Map;
import java.util.Optional;

import static org.gradle.internal.file.TreeType.DIRECTORY;
import static org.gradle.internal.file.TreeType.FILE;
import static org.gradle.internal.properties.InputBehavior.INCREMENTAL;
import static org.gradle.internal.properties.InputBehavior.NON_INCREMENTAL;

abstract class AbstractTransformExecution implements UnitOfWork {
    private static final CachingDisabledReason NOT_CACHEABLE = new CachingDisabledReason(CachingDisabledReasonCategory.NOT_CACHEABLE, "Caching not enabled.");
    private static final CachingDisabledReason CACHING_DISABLED_REASON = new CachingDisabledReason(CachingDisabledReasonCategory.NOT_CACHEABLE, "Caching disabled by property ('org.gradle.internal.transform-caching-disabled')");

    protected static final String INPUT_ARTIFACT_PROPERTY_NAME = "inputArtifact";
    private static final String OUTPUT_DIRECTORY_PROPERTY_NAME = "outputDirectory";
    private static final String RESULTS_FILE_PROPERTY_NAME = "resultsFile";
    protected static final String INPUT_ARTIFACT_PATH_PROPERTY_NAME = "inputArtifactPath";
    protected static final String DEPENDENCIES_PROPERTY_NAME = "inputArtifactDependencies";
    protected static final String SECONDARY_INPUTS_HASH_PROPERTY_NAME = "inputPropertiesHash";

    private static final SnapshotTransformInputsBuildOperationType.Details SNAPSHOT_TRANSFORM_INPUTS_DETAILS = new SnapshotTransformInputsBuildOperationType.Details() {};

    protected final Transform transform;
    protected final File inputArtifact;
    private final TransformDependencies dependencies;
    private final TransformStepSubject subject;

    private final TransformExecutionListener transformExecutionListener;
    private final BuildOperationRunner buildOperationRunner;
    private final BuildOperationProgressEventEmitter progressEventEmitter;
    private final FileCollectionFactory fileCollectionFactory;

    private final Provider<FileSystemLocation> inputArtifactProvider;
    protected final InputFingerprinter inputFingerprinter;
    private final boolean disableCachingByProperty;

    private BuildOperationContext operationContext;

    protected AbstractTransformExecution(
        Transform transform,
        File inputArtifact,
        TransformDependencies dependencies,
        TransformStepSubject subject,
        TransformExecutionListener transformExecutionListener,
        BuildOperationRunner buildOperationRunner,
        BuildOperationProgressEventEmitter progressEventEmitter,
        FileCollectionFactory fileCollectionFactory,
        InputFingerprinter inputFingerprinter,
        boolean disableCachingByProperty
    ) {
        this.transform = transform;
        this.inputArtifact = inputArtifact;
        this.dependencies = dependencies;
        this.inputArtifactProvider = Providers.of(new DefaultFileSystemLocation(inputArtifact));
        this.subject = subject;
        this.transformExecutionListener = transformExecutionListener;

        this.buildOperationRunner = buildOperationRunner;
        this.progressEventEmitter = progressEventEmitter;
        this.fileCollectionFactory = fileCollectionFactory;
        this.inputFingerprinter = inputFingerprinter;
        this.disableCachingByProperty = disableCachingByProperty;
    }

    @Override
    public Optional<String> getBuildOperationWorkType() {
        return Optional.of("TRANSFORM");
    }

    @Override
    public Identity identify(Map<String, ValueSnapshot> identityInputs, Map<String, CurrentFileCollectionFingerprint> identityFileInputs) {
        TransformWorkspaceIdentity transformWorkspaceIdentity = createIdentity(identityInputs, identityFileInputs);
        emitIdentifyTransformExecutionProgressDetails(transformWorkspaceIdentity);
        return transformWorkspaceIdentity;
    }

    protected abstract TransformWorkspaceIdentity createIdentity(Map<String, ValueSnapshot> identityInputs, Map<String, CurrentFileCollectionFingerprint> identityFileInputs);

    @Override
    public WorkOutput execute(ExecutionRequest executionRequest) {
        transformExecutionListener.beforeTransformExecution(transform, subject);
        try {
            subject.getProducerTasks().forEach(producerTask -> {
                if (!producerTask.getState().getExecuted()) {
                    DeprecationLogger.deprecateAction(String.format("Executing the transform %s before %s has completed", transform.getDisplayName(), producerTask))
                        .willBecomeAnErrorInGradle9()
                        .withUpgradeGuideSection(8, "executing_a_transform_before_the_input_task_has_completed")
                        .nagUser();
                }
            });
            return executeWithinTransformerListener(executionRequest);
        } finally {
            transformExecutionListener.afterTransformExecution(transform, subject);
        }
    }

    private WorkOutput executeWithinTransformerListener(ExecutionRequest executionRequest) {
        TransformExecutionResult result = buildOperationRunner.call(new CallableBuildOperation<TransformExecutionResult>() {
            @Override
            public TransformExecutionResult call(BuildOperationContext context) {
                try {
                    File workspace = executionRequest.getWorkspace();
                    InputChangesInternal inputChanges = executionRequest.getInputChanges().orElse(null);
                    TransformExecutionResult result = transform.transform(inputArtifactProvider, getOutputDir(workspace), dependencies, inputChanges);
                    TransformExecutionResultSerializer resultSerializer = new TransformExecutionResultSerializer();
                    resultSerializer.writeToFile(getResultsFile(workspace), result);
                    return result;
                } finally {
                    context.setResult(ExecuteTransformActionBuildOperationType.RESULT_INSTANCE);
                }
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                String displayName = transform.getDisplayName() + " " + inputArtifact.getName();
                return BuildOperationDescriptor.displayName(displayName)
                    .details(ExecuteTransformActionBuildOperationType.DETAILS_INSTANCE)
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
            public Object getOutput(File workspace) {
                return result.resolveForWorkspace(getOutputDir(workspace));
            }
        };
    }

    @Override
    public Object loadAlreadyProducedOutput(File workspace) {
        TransformExecutionResultSerializer resultSerializer = new TransformExecutionResultSerializer();
        return resultSerializer.readResultsFile(getResultsFile(workspace)).resolveForWorkspace(getOutputDir(workspace));
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

    protected void emitIdentifyTransformExecutionProgressDetails(TransformWorkspaceIdentity transformWorkspaceIdentity) {
        progressEventEmitter.emitNowIfCurrent(new DefaultIdentifyTransformExecutionProgressDetails(
            inputArtifact,
            transformWorkspaceIdentity,
            transform,
            subject.getInitialComponentIdentifier()));
    }

    protected void visitInputArtifact(InputVisitor visitor) {
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

    @Override
    public void markLegacySnapshottingInputsStarted() {
        this.operationContext = buildOperationRunner.start(BuildOperationDescriptor
            .displayName("Snapshot transform inputs")
            .name("Snapshot transform inputs")
            .details(SNAPSHOT_TRANSFORM_INPUTS_DETAILS));
    }

    @Override
    public void markLegacySnapshottingInputsFinished(CachingState cachingState) {
        if (operationContext != null) {
            ImmutableSortedSet.Builder<InputFilePropertySpec> builder = ImmutableSortedSet.naturalOrder();
            builder.add(new DefaultInputFilePropertySpec(
                INPUT_ARTIFACT_PROPERTY_NAME,
                transform.getInputArtifactNormalizer(),
                FileCollectionFactory.empty(),
                PropertyValue.ABSENT,
                INCREMENTAL,
                transform.getInputArtifactDirectorySensitivity(),
                transform.getInputArtifactLineEndingNormalization()
            ));
            builder.add(new DefaultInputFilePropertySpec(
                DEPENDENCIES_PROPERTY_NAME,
                transform.getInputArtifactDependenciesNormalizer(),
                FileCollectionFactory.empty(),
                PropertyValue.ABSENT,
                NON_INCREMENTAL,
                transform.getInputArtifactDependenciesDirectorySensitivity(),
                transform.getInputArtifactDependenciesLineEndingNormalization()
            ));
            operationContext.setResult(new SnapshotTransformInputsBuildOperationResult(cachingState, builder.build()));
            operationContext = null;
        }
    }

    @Override
    public Optional<CachingDisabledReason> shouldDisableCaching(@Nullable OverlappingOutputs detectedOverlappingOutputs) {
        return transform.isCacheable()
            ? maybeDisableCachingByProperty()
            : Optional.of(NOT_CACHEABLE);
    }

    private Optional<CachingDisabledReason> maybeDisableCachingByProperty() {
        if (disableCachingByProperty) {
            return Optional.of(CACHING_DISABLED_REASON);
        }

        return Optional.empty();
    }

    @Override
    public String getDisplayName() {
        return transform.getDisplayName() + ": " + inputArtifact;
    }

    private static class DefaultIdentifyTransformExecutionProgressDetails implements IdentifyTransformExecutionProgressDetails {

        private final File inputArtifact;
        private final TransformWorkspaceIdentity transformWorkspaceIdentity;
        private final Transform transform;
        private final ComponentIdentifier componentIdentifier;

        public DefaultIdentifyTransformExecutionProgressDetails(
            File inputArtifact,
            TransformWorkspaceIdentity transformWorkspaceIdentity,
            Transform transform,
            ComponentIdentifier componentIdentifier
        ) {
            this.inputArtifact = inputArtifact;
            this.transformWorkspaceIdentity = transformWorkspaceIdentity;
            this.transform = transform;
            this.componentIdentifier = componentIdentifier;
        }

        @Override
        public String getIdentity() {
            return transformWorkspaceIdentity.getUniqueId();
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
        public org.gradle.operations.dependencies.variants.ComponentIdentifier getComponentId() {
            return ComponentToOperationConverter.convertComponentIdentifier(componentIdentifier);
        }

        @Override
        public String getArtifactName() {
            return inputArtifact.getName();
        }

        @Override
        public Class<?> getTransformActionClass() {
            return transform.getImplementationClass();
        }

        @Override
        public byte[] getSecondaryInputValueHashBytes() {
            return Hashing.hashHashable(transformWorkspaceIdentity.getSecondaryInputsSnapshot()).toByteArray();
        }
    }
}
