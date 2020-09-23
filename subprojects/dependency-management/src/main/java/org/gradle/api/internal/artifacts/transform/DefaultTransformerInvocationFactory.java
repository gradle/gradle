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
import org.gradle.api.UncheckedIOException;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.file.DefaultFileSystemLocation;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.api.internal.provider.Providers;
import org.gradle.api.provider.Provider;
import org.gradle.internal.Try;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.execution.CachingResult;
import org.gradle.internal.execution.ExecutionRequestContext;
import org.gradle.internal.execution.InputChangesContext;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.WorkExecutor;
import org.gradle.internal.execution.caching.CachingDisabledReason;
import org.gradle.internal.execution.caching.CachingDisabledReasonCategory;
import org.gradle.internal.execution.history.ExecutionHistoryStore;
import org.gradle.internal.execution.history.changes.InputChangesInternal;
import org.gradle.internal.file.TreeType;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprinter;
import org.gradle.internal.fingerprint.FileCollectionFingerprinterRegistry;
import org.gradle.internal.fingerprint.overlap.OverlappingOutputs;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot;
import org.gradle.internal.time.Time;
import org.gradle.internal.time.Timer;
import org.gradle.internal.vfs.FileSystemAccess;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class DefaultTransformerInvocationFactory implements TransformerInvocationFactory {
    private static final CachingDisabledReason NOT_CACHEABLE = new CachingDisabledReason(CachingDisabledReasonCategory.NOT_CACHEABLE, "Caching not enabled.");
    private static final String INPUT_ARTIFACT_PROPERTY_NAME = "inputArtifact";
    private static final String DEPENDENCIES_PROPERTY_NAME = "inputArtifactDependencies";
    private static final String SECONDARY_INPUTS_HASH_PROPERTY_NAME = "inputPropertiesHash";
    private static final String OUTPUT_DIRECTORY_PROPERTY_NAME = "outputDirectory";
    private static final String RESULTS_FILE_PROPERTY_NAME = "resultsFile";
    private static final String INPUT_FILE_PATH_PREFIX = "i/";
    private static final String OUTPUT_FILE_PATH_PREFIX = "o/";

    private final FileSystemAccess fileSystemAccess;
    private final WorkExecutor<ExecutionRequestContext, CachingResult> workExecutor;
    private final ArtifactTransformListener artifactTransformListener;
    private final CachingTransformationWorkspaceProvider immutableTransformationWorkspaceProvider;
    private final FileCollectionFactory fileCollectionFactory;
    private final ProjectStateRegistry projectStateRegistry;
    private final BuildOperationExecutor buildOperationExecutor;

    public DefaultTransformerInvocationFactory(
        WorkExecutor<ExecutionRequestContext, CachingResult> workExecutor,
        FileSystemAccess fileSystemAccess,
        ArtifactTransformListener artifactTransformListener,
        CachingTransformationWorkspaceProvider immutableTransformationWorkspaceProvider,
        FileCollectionFactory fileCollectionFactory,
        ProjectStateRegistry projectStateRegistry,
        BuildOperationExecutor buildOperationExecutor
    ) {
        this.workExecutor = workExecutor;
        this.fileSystemAccess = fileSystemAccess;
        this.artifactTransformListener = artifactTransformListener;
        this.immutableTransformationWorkspaceProvider = immutableTransformationWorkspaceProvider;
        this.fileCollectionFactory = fileCollectionFactory;
        this.projectStateRegistry = projectStateRegistry;
        this.buildOperationExecutor = buildOperationExecutor;
    }

    @Override
    public CacheableInvocation<ImmutableList<File>> createInvocation(Transformer transformer, File inputArtifact, ArtifactTransformDependencies dependencies, TransformationSubject subject, FileCollectionFingerprinterRegistry fingerprinterRegistry) {
        ProjectInternal producerProject = determineProducerProject(subject);
        CachingTransformationWorkspaceProvider workspaceProvider = determineWorkspaceProvider(producerProject);

        FileCollectionFingerprinter inputArtifactFingerprinter = fingerprinterRegistry.getFingerprinter(transformer.getInputArtifactNormalizer());
        // This could be injected directly to DefaultTransformerInvocationFactory, too
        FileCollectionFingerprinter dependencyFingerprinter = fingerprinterRegistry.getFingerprinter(transformer.getInputArtifactDependenciesNormalizer());

        CompleteFileSystemLocationSnapshot inputArtifactSnapshot = fileSystemAccess.read(inputArtifact.getAbsolutePath(), Function.identity());
        String normalizedInputPath = inputArtifactFingerprinter.normalizePath(inputArtifactSnapshot);
        CurrentFileCollectionFingerprint dependenciesFingerprint = dependencies.fingerprint(dependencyFingerprinter);

        TransformationWorkspaceIdentity identity = getTransformationIdentity(producerProject, inputArtifactSnapshot, normalizedInputPath, transformer, dependenciesFingerprint);

        return new CacheableInvocation<ImmutableList<File>>() {
            @Override
            public Try<ImmutableList<File>> invoke() {
                return workspaceProvider.withWorkspace(identity, (identityString, workspaceDir) -> buildOperationExecutor.call(new CallableBuildOperation<Try<ImmutableList<File>>>() {
                    @Override
                    public Try<ImmutableList<File>> call(BuildOperationContext context) {
                        return fireTransformListeners(transformer, subject, () -> {
                            String transformIdentity = "transform/" + identityString;
                            ExecutionHistoryStore executionHistoryStore = workspaceProvider.getExecutionHistoryStore();

                            TransformerExecution execution = new TransformerExecution(
                                transformer,
                                workspaceDir,
                                transformIdentity,
                                inputArtifact,
                                inputArtifactSnapshot,
                                dependencies,
                                dependenciesFingerprint,
                                executionHistoryStore,
                                fileCollectionFactory,
                                inputArtifactFingerprinter
                            );

                            CachingResult outcome = workExecutor.execute(new ExecutionRequestContext() {
                                @Override
                                public UnitOfWork getWork() {
                                    return execution;
                                }

                                @Override
                                public Optional<String> getRebuildReason() {
                                    return Optional.empty();
                                }
                            });

                            return outcome.getOutcome()
                                .tryMap(outcome1 -> execution.loadResultsFile())
                                .mapFailure(failure -> new TransformException(String.format("Execution failed for %s.", execution.getDisplayName()), failure));
                        });
                    }

                    @Override
                    public BuildOperationDescriptor.Builder description() {
                        String displayName = transformer.getDisplayName() + " " + inputArtifact.getName();
                        return BuildOperationDescriptor.displayName(displayName)
                            .progressDisplayName(displayName);
                    }
                }));
            }

            @Override
            public Optional<Try<ImmutableList<File>>> getCachedResult() {
                return Optional.ofNullable(workspaceProvider.getCachedResult(identity));
            }
        };
    }

    private static TransformationWorkspaceIdentity getTransformationIdentity(@Nullable ProjectInternal project, CompleteFileSystemLocationSnapshot inputArtifactSnapshot, String inputArtifactPath, Transformer transformer, CurrentFileCollectionFingerprint dependenciesFingerprint) {
        return project == null
            ? getImmutableTransformationIdentity(inputArtifactPath, inputArtifactSnapshot, transformer, dependenciesFingerprint)
            : getMutableTransformationIdentity(inputArtifactSnapshot, transformer, dependenciesFingerprint);
    }

    private static TransformationWorkspaceIdentity getImmutableTransformationIdentity(String inputArtifactPath, CompleteFileSystemLocationSnapshot inputArtifactSnapshot, Transformer transformer, CurrentFileCollectionFingerprint dependenciesFingerprint) {
        return new ImmutableTransformationWorkspaceIdentity(
            inputArtifactPath,
            inputArtifactSnapshot.getHash(),
            transformer.getSecondaryInputHash(),
            dependenciesFingerprint.getHash()
        );
    }

    private static TransformationWorkspaceIdentity getMutableTransformationIdentity(CompleteFileSystemLocationSnapshot inputArtifactSnapshot, Transformer transformer, CurrentFileCollectionFingerprint dependenciesFingerprint) {
        return new MutableTransformationWorkspaceIdentity(
            inputArtifactSnapshot.getAbsolutePath(),
            transformer.getSecondaryInputHash(),
            dependenciesFingerprint.getHash()
        );
    }

    private CachingTransformationWorkspaceProvider determineWorkspaceProvider(@Nullable ProjectInternal producerProject) {
        if (producerProject == null) {
            return immutableTransformationWorkspaceProvider;
        }
        return producerProject.getServices().get(CachingTransformationWorkspaceProvider.class);
    }

    @Nullable
    private ProjectInternal determineProducerProject(TransformationSubject subject) {
        if (!subject.getProducer().isPresent()) {
            return null;
        }
        ProjectComponentIdentifier projectComponentIdentifier = subject.getProducer().get();
        return projectStateRegistry.stateFor(projectComponentIdentifier).getMutableModel();
    }

    private Try<ImmutableList<File>> fireTransformListeners(Transformer transformer, TransformationSubject subject, Supplier<Try<ImmutableList<File>>> execution) {
        artifactTransformListener.beforeTransformerInvocation(transformer, subject);
        try {
            return execution.get();
        } finally {
            artifactTransformListener.afterTransformerInvocation(transformer, subject);
        }
    }

    private static class TransformerExecution implements UnitOfWork {
        private final Transformer transformer;
        private final File inputArtifact;
        private final String identityString;
        private final CompleteFileSystemLocationSnapshot inputArtifactSnapshot;
        private final ArtifactTransformDependencies dependencies;
        private final CurrentFileCollectionFingerprint dependenciesFingerprint;

        private final ExecutionHistoryStore executionHistoryStore;
        private final FileCollectionFactory fileCollectionFactory;
        private final FileCollectionFingerprinter inputArtifactFingerprinter;

        private final Timer executionTimer;
        private final Provider<FileSystemLocation> inputArtifactProvider;
        private final File outputDir;
        private final File resultsFile;

        public TransformerExecution(
            Transformer transformer,
            File workspaceDir,
            String identityString,
            File inputArtifact,
            CompleteFileSystemLocationSnapshot inputArtifactSnapshot,
            ArtifactTransformDependencies dependencies,
            CurrentFileCollectionFingerprint dependenciesFingerprint,

            ExecutionHistoryStore executionHistoryStore,
            FileCollectionFactory fileCollectionFactory,
            FileCollectionFingerprinter inputArtifactFingerprinter
        ) {
            this.inputArtifactSnapshot = inputArtifactSnapshot;
            this.dependenciesFingerprint = dependenciesFingerprint;
            this.inputArtifact = inputArtifact;
            this.transformer = transformer;
            this.outputDir = new File(workspaceDir, "transformed");
            this.resultsFile = new File(workspaceDir, "results.bin");
            this.identityString = identityString;
            this.executionHistoryStore = executionHistoryStore;
            this.dependencies = dependencies;
            this.fileCollectionFactory = fileCollectionFactory;
            this.inputArtifactFingerprinter = inputArtifactFingerprinter;
            this.executionTimer = Time.startTimer();
            this.inputArtifactProvider = Providers.of(new DefaultFileSystemLocation(inputArtifact));
        }

        @Override
        public WorkResult execute(@Nullable InputChangesInternal inputChanges, InputChangesContext context) {
            ImmutableList<File> result = transformer.transform(inputArtifactProvider, outputDir, dependencies, inputChanges);
            writeResultsFile(outputDir, resultsFile, result);
            return WorkResult.DID_WORK;
        }

        private void writeResultsFile(File outputDir, File resultsFile, ImmutableList<File> result) {
            String outputDirPrefix = outputDir.getPath() + File.separator;
            String inputFilePrefix = inputArtifact.getPath() + File.separator;
            Stream<String> relativePaths = result.stream().map(file -> {
                if (file.equals(outputDir)) {
                    return OUTPUT_FILE_PATH_PREFIX;
                }
                if (file.equals(inputArtifact)) {
                    return INPUT_FILE_PATH_PREFIX;
                }
                String absolutePath = file.getAbsolutePath();
                if (absolutePath.startsWith(outputDirPrefix)) {
                    return OUTPUT_FILE_PATH_PREFIX + RelativePath.parse(true, absolutePath.substring(outputDirPrefix.length())).getPathString();
                }
                if (absolutePath.startsWith(inputFilePrefix)) {
                    return INPUT_FILE_PATH_PREFIX + RelativePath.parse(true, absolutePath.substring(inputFilePrefix.length())).getPathString();
                }
                throw new IllegalStateException("Invalid result path: " + absolutePath);
            });
            UncheckedException.callUnchecked(() -> Files.write(resultsFile.toPath(), (Iterable<String>) relativePaths::iterator));
        }

        private ImmutableList<File> loadResultsFile() {
            Path transformerResultsPath = resultsFile.toPath();
            try {
                ImmutableList.Builder<File> builder = ImmutableList.builder();
                List<String> paths = Files.readAllLines(transformerResultsPath, StandardCharsets.UTF_8);
                for (String path : paths) {
                    if (path.startsWith(OUTPUT_FILE_PATH_PREFIX)) {
                        builder.add(new File(outputDir, path.substring(2)));
                    } else if (path.startsWith(INPUT_FILE_PATH_PREFIX)) {
                        builder.add(new File(inputArtifact, path.substring(2)));
                    } else {
                        throw new IllegalStateException("Cannot parse result path string: " + path);
                    }
                }
                return builder.build();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public Optional<ExecutionHistoryStore> getExecutionHistoryStore() {
            return Optional.of(executionHistoryStore);
        }

        @Override
        public Optional<Duration> getTimeout() {
            return Optional.empty();
        }

        @Override
        public InputChangeTrackingStrategy getInputChangeTrackingStrategy() {
            return transformer.requiresInputChanges() ? InputChangeTrackingStrategy.INCREMENTAL_PARAMETERS : InputChangeTrackingStrategy.NONE;
        }

        @Override
        public void visitImplementations(ImplementationVisitor visitor) {
            visitor.visitImplementation(transformer.getImplementationClass());
        }

        @Override
        public void visitInputProperties(InputPropertyVisitor visitor) {
            // Emulate secondary inputs as a single property for now
            visitor.visitInputProperty(SECONDARY_INPUTS_HASH_PROPERTY_NAME, transformer.getSecondaryInputHash().toString());
        }

        @Override
        public void visitInputFileProperties(InputFilePropertyVisitor visitor) {
            visitor.visitInputFileProperty(INPUT_ARTIFACT_PROPERTY_NAME, inputArtifactProvider, true,
                () -> inputArtifactFingerprinter.fingerprint(ImmutableList.of(inputArtifactSnapshot)));
            visitor.visitInputFileProperty(DEPENDENCIES_PROPERTY_NAME, dependencies, false,
                () -> dependenciesFingerprint);
        }

        @Override
        public void visitOutputProperties(OutputPropertyVisitor visitor) {
            visitor.visitOutputProperty(OUTPUT_DIRECTORY_PROPERTY_NAME, TreeType.DIRECTORY, outputDir, fileCollectionFactory.fixed(outputDir));
            visitor.visitOutputProperty(RESULTS_FILE_PROPERTY_NAME, TreeType.FILE, resultsFile, fileCollectionFactory.fixed(resultsFile));
        }

        @Override
        public long markExecutionTime() {
            return executionTimer.getElapsedMillis();
        }

        @Override
        public Optional<CachingDisabledReason> shouldDisableCaching(@Nullable OverlappingOutputs detectedOverlappingOutputs) {
            return transformer.isCacheable()
                ? Optional.empty()
                : Optional.of(NOT_CACHEABLE);
        }

        @Override
        public String getIdentity() {
            return identityString;
        }

        @Override
        public String getDisplayName() {
            return transformer.getDisplayName() + ": " + inputArtifact;
        }
    }

    private static class ImmutableTransformationWorkspaceIdentity implements TransformationWorkspaceIdentity {
        private final String inputArtifactPath;
        private final HashCode inputArtifactHash;
        private final HashCode secondaryInputHash;
        private final HashCode dependenciesHash;

        public ImmutableTransformationWorkspaceIdentity(String inputArtifactPath, HashCode inputArtifactHash, HashCode secondaryInputHash, HashCode dependenciesHash) {
            this.inputArtifactPath = inputArtifactPath;
            this.inputArtifactHash = inputArtifactHash;
            this.secondaryInputHash = secondaryInputHash;
            this.dependenciesHash = dependenciesHash;
        }

        @Override
        public String getIdentity() {
            Hasher hasher = Hashing.newHasher();
            hasher.putString(inputArtifactPath);
            hasher.putHash(inputArtifactHash);
            hasher.putHash(secondaryInputHash);
            hasher.putHash(dependenciesHash);
            return hasher.hash().toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            ImmutableTransformationWorkspaceIdentity that = (ImmutableTransformationWorkspaceIdentity) o;

            if (!inputArtifactHash.equals(that.inputArtifactHash)) {
                return false;
            }
            if (!inputArtifactPath.equals(that.inputArtifactPath)) {
                return false;
            }
            if (!secondaryInputHash.equals(that.secondaryInputHash)) {
                return false;
            }
            return dependenciesHash.equals(that.dependenciesHash);
        }

        @Override
        public int hashCode() {
            int result = inputArtifactHash.hashCode();
            result = 31 * result + secondaryInputHash.hashCode();
            result = 31 * result + dependenciesHash.hashCode();
            return result;
        }
    }

    public static class MutableTransformationWorkspaceIdentity implements TransformationWorkspaceIdentity {
        private final String inputArtifactAbsolutePath;
        private final HashCode secondaryInputsHash;
        private final HashCode dependenciesHash;

        public MutableTransformationWorkspaceIdentity(String inputArtifactAbsolutePath, HashCode secondaryInputsHash, HashCode dependenciesHash) {
            this.inputArtifactAbsolutePath = inputArtifactAbsolutePath;
            this.secondaryInputsHash = secondaryInputsHash;
            this.dependenciesHash = dependenciesHash;
        }

        @Override
        public String getIdentity() {
            Hasher hasher = Hashing.newHasher();
            hasher.putString(inputArtifactAbsolutePath);
            hasher.putHash(secondaryInputsHash);
            hasher.putHash(dependenciesHash);
            return hasher.hash().toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            MutableTransformationWorkspaceIdentity that = (MutableTransformationWorkspaceIdentity) o;

            if (!secondaryInputsHash.equals(that.secondaryInputsHash)) {
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
            result = 31 * result + secondaryInputsHash.hashCode();
            result = 31 * result + dependenciesHash.hashCode();
            return result;
        }
    }
}
