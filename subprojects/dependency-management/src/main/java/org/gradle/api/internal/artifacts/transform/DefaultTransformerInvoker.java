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
import com.google.common.collect.ImmutableSortedMap;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.transform.ArtifactTransform;
import org.gradle.api.artifacts.transform.TransformationException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder;
import org.gradle.api.internal.artifacts.transform.TransformationWorkspaceProvider.TransformationWorkspace;
import org.gradle.api.internal.file.collections.ImmutableFileCollection;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.internal.Try;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.change.Change;
import org.gradle.internal.change.ChangeVisitor;
import org.gradle.internal.change.SummarizingChangeContainer;
import org.gradle.internal.classloader.ClassLoaderHierarchyHasher;
import org.gradle.internal.execution.CacheHandler;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.WorkExecutor;
import org.gradle.internal.execution.history.AfterPreviousExecutionState;
import org.gradle.internal.execution.history.ExecutionHistoryStore;
import org.gradle.internal.execution.history.changes.AbstractFingerprintChanges;
import org.gradle.internal.execution.history.changes.ExecutionStateChanges;
import org.gradle.internal.execution.history.changes.InputFileChanges;
import org.gradle.internal.execution.impl.steps.UpToDateResult;
import org.gradle.internal.file.TreeType;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.fingerprint.impl.AbsolutePathFingerprintingStrategy;
import org.gradle.internal.fingerprint.impl.DefaultCurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.impl.OutputFileCollectionFingerprinter;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshotter;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.snapshot.impl.ImplementationSnapshot;
import org.gradle.util.GFileUtils;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class DefaultTransformerInvoker implements TransformerInvoker {

    private final FileSystemSnapshotter fileSystemSnapshotter;
    private final WorkExecutor<UpToDateResult> workExecutor;
    private final ArtifactTransformListener artifactTransformListener;
    private final CachingTransformationWorkspaceProvider immutableTransformationWorkspaceProvider;
    private final OutputFileCollectionFingerprinter outputFileCollectionFingerprinter;
    private final ClassLoaderHierarchyHasher classLoaderHierarchyHasher;
    private final ProjectFinder projectFinder;
    private final boolean useTransformationWorkspaces;

    public DefaultTransformerInvoker(WorkExecutor<UpToDateResult> workExecutor,
                                     FileSystemSnapshotter fileSystemSnapshotter,
                                     ArtifactTransformListener artifactTransformListener,
                                     CachingTransformationWorkspaceProvider immutableTransformationWorkspaceProvider,
                                     OutputFileCollectionFingerprinter outputFileCollectionFingerprinter,
                                     ClassLoaderHierarchyHasher classLoaderHierarchyHasher,
                                     ProjectFinder projectFinder,
                                     boolean useTransformationWorkspaces) {
        this.workExecutor = workExecutor;
        this.fileSystemSnapshotter = fileSystemSnapshotter;
        this.artifactTransformListener = artifactTransformListener;
        this.immutableTransformationWorkspaceProvider = immutableTransformationWorkspaceProvider;
        this.outputFileCollectionFingerprinter = outputFileCollectionFingerprinter;
        this.classLoaderHierarchyHasher = classLoaderHierarchyHasher;
        this.projectFinder = projectFinder;
        this.useTransformationWorkspaces = useTransformationWorkspaces;
    }

    @Override
    public boolean hasCachedResult(File primaryInput, Transformer transformer, TransformationSubject subject) {
        ProjectInternal producerProject = determineProducerProject(subject);
        return determineWorkspaceProvider(producerProject).hasCachedResult(getTransformationIdentity(producerProject, primaryInput, transformer, subject));
    }

    @Override
    public Try<ImmutableList<File>> invoke(Transformer transformer, File primaryInput, TransformationSubject subject, ArtifactTransformDependenciesProvider dependenciesProvider) {
        return Try.ofFailable(() -> {
            ProjectInternal producerProject = determineProducerProject(subject);
            CachingTransformationWorkspaceProvider workspaceProvider = determineWorkspaceProvider(producerProject);
            TransformationIdentity identity = getTransformationIdentity(producerProject, primaryInput, transformer, subject);
            return workspaceProvider.withWorkspace(identity, (identityString, workspace) -> {
                return fireTransformListeners(transformer, subject, () -> {
                    CurrentFileCollectionFingerprint primaryInputFingerprint = DefaultCurrentFileCollectionFingerprint.from(ImmutableList.of(fileSystemSnapshotter.snapshot(primaryInput)), AbsolutePathFingerprintingStrategy.INCLUDE_MISSING);
                    TransformerExecution execution = new TransformerExecution(primaryInput, transformer, workspace, identityString, workspaceProvider.getExecutionHistoryStore(), primaryInputFingerprint, dependenciesProvider);
                    UpToDateResult outcome = workExecutor.execute(execution);
                    return execution.getResult(outcome);
                });
            });
        }).mapFailure(e -> wrapInTransformInvocationException(transformer.getImplementationClass(), primaryInput, e));
    }

    private Exception wrapInTransformInvocationException(Class<? extends ArtifactTransform> transformerType, File primaryInput, Throwable originalFailure) {
        return new TransformationException(primaryInput.getAbsoluteFile(), transformerType, originalFailure);
    }

    private TransformationIdentity getTransformationIdentity(@Nullable ProjectInternal project, File primaryInput, Transformer transformer, TransformationSubject subject) {
        String humanReadableIdentifier = subject.getHumanReadableIdentifier();
        return project == null ? getImmutableTransformationIdentity(primaryInput, transformer, humanReadableIdentifier) :
            getMutableTransformationIdentity(primaryInput, transformer, humanReadableIdentifier);
    }

    private TransformationIdentity getImmutableTransformationIdentity(File primaryInput, Transformer transformer, String humanReadableIdentifier) {
        FileSystemLocationSnapshot snapshot = fileSystemSnapshotter.snapshot(primaryInput);
        return new ImmutableTransformationIdentity(
            humanReadableIdentifier,
            snapshot.getAbsolutePath(),
            snapshot.getHash(),
            transformer.getSecondaryInputHash()
        );
    }

    private TransformationIdentity getMutableTransformationIdentity(File primaryInput, Transformer transformer, String humanReadableIdentifier) {
        return new MutableTransformationIdentity(
            humanReadableIdentifier,
            primaryInput.getAbsolutePath(),
            transformer.getSecondaryInputHash()
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
        if (!useTransformationWorkspaces || !subject.getProducer().isPresent()) {
            return null;
        }
        ProjectComponentIdentifier projectComponentIdentifier = subject.getProducer().get();
        return projectFinder.findProject(projectComponentIdentifier.getBuild(), projectComponentIdentifier.getProjectPath());
    }

    private ImmutableList<File> fireTransformListeners(Transformer transformer, TransformationSubject subject, Supplier<ImmutableList<File>> execution) {
        artifactTransformListener.beforeTransformerInvocation(transformer, subject);
        try {
            return execution.get();
        } finally {
            artifactTransformListener.afterTransformerInvocation(transformer, subject);
        }
    }

    private class TransformerExecution implements UnitOfWork {
        private static final String PRIMARY_INPUT_PROPERTY_NAME = "primaryInput";
        private static final String SECONDARY_INPUTS_HASH_PROPERTY_NAME = "inputPropertiesHash";
        private static final String OUTPUT_DIRECTORY_PROPERTY_NAME = "outputDirectory";
        private static final String RESULTS_FILE_PROPERTY_NAME = "resultsFile";
        private static final String INPUT_FILE_PATH_PREFIX = "i/";
        private static final String OUTPUT_FILE_PATH_PREFIX = "o/";

        private final File primaryInput;
        private final Transformer transformer;
        private final TransformationWorkspace workspace;
        private final String identityString;
        private final ExecutionHistoryStore executionHistoryStore;
        private final ArtifactTransformDependenciesProvider dependenciesProvider;
        private final ImmutableSortedMap<String, ValueSnapshot> inputSnapshots;
        private final ImmutableSortedMap<String, CurrentFileCollectionFingerprint> inputFileFingerprints;

        public TransformerExecution(File primaryInput, Transformer transformer, TransformationWorkspace workspace, String identityString, ExecutionHistoryStore executionHistoryStore, CurrentFileCollectionFingerprint primaryInputFingerprint, ArtifactTransformDependenciesProvider dependenciesProvider) {
            this.primaryInput = primaryInput;
            this.transformer = transformer;
            this.workspace = workspace;
            this.identityString = "transform/" + identityString;
            this.executionHistoryStore = executionHistoryStore;
            this.dependenciesProvider = dependenciesProvider;
            this.inputSnapshots = ImmutableSortedMap.of(
                // Emulate secondary inputs as a single property for now
                SECONDARY_INPUTS_HASH_PROPERTY_NAME, ImplementationSnapshot.of("secondary inputs", transformer.getSecondaryInputHash())
            );
            this.inputFileFingerprints = ImmutableSortedMap.<String, CurrentFileCollectionFingerprint>naturalOrder()
                .put(PRIMARY_INPUT_PROPERTY_NAME, primaryInputFingerprint)
                .build();
        }

        @Override
        public boolean execute() {
            File outputDir = workspace.getOutputDirectory();
            File resultsFile = workspace.getResultsFile();
            GFileUtils.cleanDirectory(outputDir);
            GFileUtils.deleteFileQuietly(resultsFile);
            ImmutableList<File> result = ImmutableList.copyOf(transformer.transform(primaryInput, outputDir, dependenciesProvider));
            writeResultsFile(outputDir, resultsFile, result);
            return true;
        }

        private void writeResultsFile(File outputDir, File resultsFile, ImmutableList<File> result) {
            String outputDirPrefix = outputDir.getPath() + File.separator;
            String inputFilePrefix = primaryInput.getPath() + File.separator;
            Stream<String> relativePaths = result.stream().map(file -> {
                if (file.equals(outputDir)) {
                    return OUTPUT_FILE_PATH_PREFIX;
                }
                if (file.equals(primaryInput)) {
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

        public ImmutableList<File> getResult(UpToDateResult outcome) {
            if (outcome.getFailure() != null) {
                throw UncheckedException.throwAsUncheckedException(outcome.getFailure());
            }
            return loadResultsFile();
        }

        private ImmutableList<File> loadResultsFile() {
            Path transformerResultsPath = workspace.getResultsFile().toPath();
            try {
                ImmutableList.Builder<File> builder = ImmutableList.builder();
                List<String> paths = Files.readAllLines(transformerResultsPath, StandardCharsets.UTF_8);
                for (String path : paths) {
                    if (path.startsWith(OUTPUT_FILE_PATH_PREFIX)) {
                        builder.add(new File(workspace.getOutputDirectory(), path.substring(2)));
                    } else if (path.startsWith(INPUT_FILE_PATH_PREFIX)) {
                        builder.add(new File(primaryInput, path.substring(2)));
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
        public Optional<Duration> getTimeout() {
            return Optional.empty();
        }

        @Override
        public void visitOutputs(OutputVisitor outputVisitor) {
            outputVisitor.visitOutput(OUTPUT_DIRECTORY_PROPERTY_NAME, TreeType.DIRECTORY, ImmutableFileCollection.of(workspace.getOutputDirectory()));
            outputVisitor.visitOutput(RESULTS_FILE_PROPERTY_NAME, TreeType.FILE, ImmutableFileCollection.of(workspace.getResultsFile()));
        }

        @Override
        public long markExecutionTime() {
            // TODO Handle execution time
            return 0;
        }

        @Override
        public FileCollection getLocalState() {
            return ImmutableFileCollection.of();
        }

        @Override
        public void outputsRemovedAfterFailureToLoadFromCache() {
        }

        @Override
        public CacheHandler createCacheHandler() {
            return new CacheHandler() {
                @Override
                public <T> Optional<T> load(Function<BuildCacheKey, T> loader) {
                    return Optional.empty();
                }

                @Override
                public void store(Consumer<BuildCacheKey> storer) {
                }
            };
        }

        @Override
        public void persistResult(ImmutableSortedMap<String, CurrentFileCollectionFingerprint> finalOutputs, boolean successful, OriginMetadata originMetadata) {
            if (successful) {
                executionHistoryStore.store(identityString,
                    originMetadata,
                    ImplementationSnapshot.of(transformer.getImplementationClass(), classLoaderHierarchyHasher),
                    ImmutableList.of(),
                    inputSnapshots,
                    inputFileFingerprints,
                    finalOutputs,
                    successful
                );
            }
        }

        @Override
        public Optional<ExecutionStateChanges> getChangesSincePreviousExecution() {
            Optional<AfterPreviousExecutionState> previousExecution = Optional.ofNullable(executionHistoryStore.load(identityString));
            return previousExecution.map(previous -> {
                ImmutableSortedMap<String, CurrentFileCollectionFingerprint> outputsBeforeExecution = snapshotOutputs();
                InputFileChanges inputFileChanges = new InputFileChanges(previous.getInputFileProperties(), inputFileFingerprints);
                AllOutputFileChanges outputFileChanges = new AllOutputFileChanges(previous.getOutputFileProperties(), outputsBeforeExecution);
                return new TransformerExecutionStateChanges(inputFileChanges, outputFileChanges, previous);
            });
        }

        @Override
        public Optional<? extends Iterable<String>> getChangingOutputs() {
            return Optional.of(ImmutableList.of(workspace.getOutputDirectory().getAbsolutePath(), workspace.getResultsFile().getAbsolutePath()));
        }


        @Override
        public ImmutableSortedMap<String, CurrentFileCollectionFingerprint> snapshotAfterOutputsGenerated() {
            return snapshotOutputs();
        }

        private ImmutableSortedMap<String, CurrentFileCollectionFingerprint> snapshotOutputs() {
            CurrentFileCollectionFingerprint outputFingerprint = outputFileCollectionFingerprinter.fingerprint(ImmutableFileCollection.of(workspace.getOutputDirectory()));
            CurrentFileCollectionFingerprint resultsFileFingerprint = outputFileCollectionFingerprinter.fingerprint(ImmutableFileCollection.of(workspace.getResultsFile()));
            return ImmutableSortedMap.of(
                OUTPUT_DIRECTORY_PROPERTY_NAME, outputFingerprint,
                RESULTS_FILE_PROPERTY_NAME, resultsFileFingerprint);
        }

        @Override
        public String getIdentity() {
            return identityString;
        }

        @Override
        public void visitTrees(CacheableTreeVisitor visitor) {
            throw new UnsupportedOperationException("we don't cache yet");
        }

        @Override
        public String getDisplayName() {
            return transformer.getDisplayName() + ": " + primaryInput;
        }

        private class TransformerExecutionStateChanges implements ExecutionStateChanges {
            private final InputFileChanges inputFileChanges;
            private final AllOutputFileChanges outputFileChanges;
            private final AfterPreviousExecutionState afterPreviousExecutionState;

            public TransformerExecutionStateChanges(InputFileChanges inputFileChanges, AllOutputFileChanges outputFileChanges, AfterPreviousExecutionState afterPreviousExecutionState) {
                this.inputFileChanges = inputFileChanges;
                this.outputFileChanges = outputFileChanges;
                this.afterPreviousExecutionState = afterPreviousExecutionState;
            }

            @Override
            public Iterable<Change> getInputFilesChanges() {
                return ImmutableList.of();
            }

            @Override
            public void visitAllChanges(ChangeVisitor visitor) {
                new SummarizingChangeContainer(inputFileChanges, outputFileChanges).accept(visitor);
            }

            @Override
            public boolean isRebuildRequired() {
                return true;
            }

            @Override
            public AfterPreviousExecutionState getPreviousExecution() {
                return afterPreviousExecutionState;
            }
        }
    }

    private static class AllOutputFileChanges extends AbstractFingerprintChanges {

        public AllOutputFileChanges(ImmutableSortedMap<String, FileCollectionFingerprint> previous, ImmutableSortedMap<String, CurrentFileCollectionFingerprint> current) {
            super(previous, current, "Output");
        }

        @Override
        public boolean accept(ChangeVisitor visitor) {
            return accept(visitor, true);
        }
    }

    public static class ImmutableTransformationIdentity implements TransformationIdentity {
        private final String humanReadableIdentifier;
        private final String primaryInputAbsolutePath;
        private final HashCode primaryInputHash;
        private final HashCode secondaryInputHash;

        public ImmutableTransformationIdentity(String humanReadableIdentifier, String primaryInputAbsolutePath, HashCode primaryInputHash, HashCode secondaryInputHash) {
            this.humanReadableIdentifier = humanReadableIdentifier;
            this.primaryInputAbsolutePath = primaryInputAbsolutePath;
            this.primaryInputHash = primaryInputHash;
            this.secondaryInputHash = secondaryInputHash;
        }

        @Override
        public String getHumanReadableIdentifier() {
            return humanReadableIdentifier;
        }

        @Override
        public String getIdentity() {
            Hasher hasher = Hashing.newHasher();
            hasher.putHash(secondaryInputHash);
            hasher.putString(primaryInputAbsolutePath);
            hasher.putHash(primaryInputHash);
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

            ImmutableTransformationIdentity that = (ImmutableTransformationIdentity) o;

            if (!primaryInputHash.equals(that.primaryInputHash)) {
                return false;
            }
            if (!secondaryInputHash.equals(that.secondaryInputHash)) {
                return false;
            }
            if (!humanReadableIdentifier.equals(that.humanReadableIdentifier)) {
                return false;
            }
            return primaryInputAbsolutePath.equals(that.primaryInputAbsolutePath);
        }

        @Override
        public int hashCode() {
            int result = primaryInputHash.hashCode();
            result = 31 * result + secondaryInputHash.hashCode();
            return result;
        }
    }

    public static class MutableTransformationIdentity implements TransformationIdentity {
        private final String humanReadableIdentifier;
        private final String primaryInputAbsolutePath;
        private final HashCode secondaryInputsHash;

        public MutableTransformationIdentity(String humanReadableIdentifier, String primaryInputAbsolutePath, HashCode secondaryInputsHash) {
            this.humanReadableIdentifier = humanReadableIdentifier;
            this.primaryInputAbsolutePath = primaryInputAbsolutePath;
            this.secondaryInputsHash = secondaryInputsHash;
        }

        @Override
        public String getHumanReadableIdentifier() {
            return humanReadableIdentifier;
        }

        @Override
        public String getIdentity() {
            Hasher hasher = Hashing.newHasher();
            hasher.putString(humanReadableIdentifier);
            hasher.putString(primaryInputAbsolutePath);
            hasher.putHash(secondaryInputsHash);
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

            MutableTransformationIdentity that = (MutableTransformationIdentity) o;

            if (!secondaryInputsHash.equals(that.secondaryInputsHash)) {
                return false;
            }
            if (!humanReadableIdentifier.equals(that.humanReadableIdentifier)) {
                return false;
            }
            return primaryInputAbsolutePath.equals(that.primaryInputAbsolutePath);
        }

        @Override
        public int hashCode() {
            int result = humanReadableIdentifier.hashCode();
            result = 31 * result + humanReadableIdentifier.hashCode();
            result = 31 * result + primaryInputAbsolutePath.hashCode();
            result = 31 * result + secondaryInputsHash.hashCode();
            return result;
        }
    }
}
