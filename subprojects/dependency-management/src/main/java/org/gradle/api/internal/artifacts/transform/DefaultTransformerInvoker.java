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
import org.gradle.api.Describable;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.transform.TransformInvocationException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder;
import org.gradle.api.internal.file.collections.ImmutableFileCollection;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.internal.Try;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.change.Change;
import org.gradle.internal.change.ChangeVisitor;
import org.gradle.internal.change.SummarizingChangeContainer;
import org.gradle.internal.execution.CacheHandler;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.WorkExecutor;
import org.gradle.internal.execution.history.AfterPreviousExecutionState;
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
import org.gradle.internal.snapshot.impl.ImplementationSnapshot;
import org.gradle.util.GFileUtils;

import java.io.File;
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
    private final TransformerExecutionHistoryRepository gradleUserHomeHistoryRepository;
    private final OutputFileCollectionFingerprinter outputFileCollectionFingerprinter;
    private final ProjectFinder projectFinder;

    public DefaultTransformerInvoker(WorkExecutor<UpToDateResult> workExecutor,
                                     FileSystemSnapshotter fileSystemSnapshotter,
                                     ArtifactTransformListener artifactTransformListener,
                                     GradleUserHomeTransformerExecutionHistoryRepository gradleUserHomeHistoryRepository, OutputFileCollectionFingerprinter outputFileCollectionFingerprinter, ProjectFinder projectFinder) {
        this.workExecutor = workExecutor;
        this.fileSystemSnapshotter = fileSystemSnapshotter;
        this.artifactTransformListener = artifactTransformListener;
        this.gradleUserHomeHistoryRepository = gradleUserHomeHistoryRepository;
        this.outputFileCollectionFingerprinter = outputFileCollectionFingerprinter;
        this.projectFinder = projectFinder;
    }

    @Override
    public boolean hasCachedResult(File primaryInput, Transformer transformer, TransformationSubject subject) {
        return determineHistoryRepository(subject).hasCachedResult(getTransformationIdentity(primaryInput, transformer, subject));
    }

    @Override
    public Try<ImmutableList<File>> invoke(TransformerInvocation invocation) {
        return Try.ofFailable(() ->
            doInvoke(invocation.getPrimaryInput(), invocation.getTransformer(), invocation.getSubjectBeingTransformed()).get()
        ).mapFailure(e -> wrapInTransformInvocationException(invocation, e));
    }

    private Exception wrapInTransformInvocationException(TransformerInvocation invocation, Throwable originalFailure) {
        return new TransformInvocationException(invocation.getPrimaryInput().getAbsoluteFile(), invocation.getTransformer().getImplementationClass(), originalFailure);
    }

    private Try<ImmutableList<File>> doInvoke(File primaryInput, Transformer transformer, TransformationSubject subject) {
        TransformerExecutionHistoryRepository historyRepository = determineHistoryRepository(subject);
        TransformationIdentity identity = getTransformationIdentity(primaryInput, transformer, subject);
        return historyRepository.withWorkspace(identity, (identityString, workspace) -> {
            return fireTransformListeners(transformer, subject, () -> {
                TransformerExecution execution = new TransformerExecution(primaryInput, transformer, workspace, identityString, historyRepository);
                UpToDateResult outcome = workExecutor.execute(execution);
                return execution.getResult(outcome);
            });
        });
    }

    private TransformationIdentity getTransformationIdentity(File primaryInput, Transformer transformer, TransformationSubject subject) {
        return subject.getProducer().map(project -> getMutableTransformationIdentity(primaryInput, transformer, project))
            .orElseGet(() -> getImmutableTransformationIdentity(primaryInput, transformer));
    }

    private TransformationIdentity getImmutableTransformationIdentity(File primaryInput, Transformer transformer) {
        FileSystemLocationSnapshot snapshot = fileSystemSnapshotter.snapshot(primaryInput);
        return new ImmutableTransformationIdentity(
            primaryInput.getName(), // TODO: Capture initial file name in subject
            snapshot.getAbsolutePath(),
            snapshot.getHash(),
            transformer.getSecondaryInputHash()
        );
    }

    private TransformationIdentity getMutableTransformationIdentity(File primaryInput, Transformer transformer, ProjectComponentIdentifier initalSubjectProducer) {
        return new MutableTransformationIdentity(
            initalSubjectProducer.getProjectPath(),
            primaryInput.getName(), // TODO: Capture inital file name in subject
            ImmutableList.of(), // TODO: Capture previous transformers in subject
            primaryInput.getName(),
            transformer.getSecondaryInputHash()
        );
    }

    private TransformerExecutionHistoryRepository determineHistoryRepository(TransformationSubject subject) {
        Optional<ProjectInternal> producerProject = determineProducerProject(subject);
        return producerProject
            .map(project -> (TransformerExecutionHistoryRepository) project.getServices().get(ProjectTransformerExecutionHistoryRepository.class))
            .orElse(gradleUserHomeHistoryRepository);
    }

    private Optional<ProjectInternal> determineProducerProject(TransformationSubject subject) {
        return subject.getProducer()
            .filter(identifier -> identifier.getBuild().isCurrentBuild())
            .map(identifier -> projectFinder.findProject(identifier.getProjectPath()));
    }

    private Try<ImmutableList<File>> fireTransformListeners(Transformer transformer, Describable subject, Supplier<Try<ImmutableList<File>>> execution) {
        artifactTransformListener.beforeTransformerInvocation(transformer, subject);
        try {
            return execution.get();
        } finally {
            artifactTransformListener.afterTransformerInvocation(transformer, subject);
        }
    }

    private class TransformerExecution implements UnitOfWork {
        private static final String PRIMARY_INPUT_PROPERTY_NAME = "primaryInput";
        private static final String OUTPUT_DIRECTORY_PROPERTY_NAME = "outputDirectory";
        private static final String RESULTS_FILE_PROPERTY_NAME = "resultsFile";
        private static final String INPUT_FILE_PATH_PREFIX = "i/";
        private static final String OUTPUT_FILE_PATH_PREFIX = "o/";

        private final File primaryInput;
        private final Transformer transformer;
        private final File outputDir;
        private final File resultsFile;
        private final String identityString;
        private final TransformerExecutionHistoryRepository historyRepository;
        private final ImmutableSortedMap<String, CurrentFileCollectionFingerprint> inputFileFingerprints;

        public TransformerExecution(File primaryInput, Transformer transformer, File workspace, String identityString, TransformerExecutionHistoryRepository historyRepository) {
            this.primaryInput = primaryInput;
            this.transformer = transformer;
            this.identityString = "transform/" + identityString;
            this.historyRepository = historyRepository;
            this.outputDir = new File(workspace, "outputDirectory");
            this.resultsFile = new File(workspace,  "results.bin");
            CurrentFileCollectionFingerprint primaryInputFingerprint = DefaultCurrentFileCollectionFingerprint.from(ImmutableList.of(fileSystemSnapshotter.snapshot(primaryInput)), AbsolutePathFingerprintingStrategy.INCLUDE_MISSING);
            this.inputFileFingerprints = ImmutableSortedMap.<String, CurrentFileCollectionFingerprint>naturalOrder()
                .put(PRIMARY_INPUT_PROPERTY_NAME, primaryInputFingerprint)
                .build();
        }

        @Override
        public boolean execute() {
            GFileUtils.cleanDirectory(outputDir);
            GFileUtils.deleteFileQuietly(resultsFile);
            ImmutableList<File> result = ImmutableList.copyOf(transformer.transform(primaryInput, outputDir));
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

        public Try<ImmutableList<File>> getResult(UpToDateResult outcome) {
            if (outcome.getFailure() != null) {
                return Try.failure(outcome.getFailure());
            }
            return Try.ofFailable(() -> {
                Path transformerResultsPath = resultsFile.toPath();
                ImmutableList.Builder<File> builder = ImmutableList.builder();
                List<String> paths = Files.readAllLines(transformerResultsPath, StandardCharsets.UTF_8);
                for (String path : paths) {
                    if (path.startsWith(OUTPUT_FILE_PATH_PREFIX)) {
                        builder.add(new File(outputDir, path.substring(2)));
                    } else if (path.startsWith(INPUT_FILE_PATH_PREFIX)) {
                        builder.add(new File(primaryInput, path.substring(2)));
                    } else {
                        throw new IllegalStateException("Cannot parse result path string: " + path);
                    }
                }
                return builder.build();
            });
        }

        @Override
        public Optional<Duration> getTimeout() {
            return Optional.empty();
        }

        @Override
        public void visitOutputs(OutputVisitor outputVisitor) {
            outputVisitor.visitOutput(OUTPUT_DIRECTORY_PROPERTY_NAME, TreeType.DIRECTORY, ImmutableFileCollection.of(outputDir));
            outputVisitor.visitOutput(RESULTS_FILE_PROPERTY_NAME, TreeType.FILE, ImmutableFileCollection.of(resultsFile));
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
                historyRepository.persist(identityString,
                    originMetadata,
                    // TODO: only use implementation hash
                    ImplementationSnapshot.of(transformer.getImplementationClass().getName(), transformer.getSecondaryInputHash()),
                    inputFileFingerprints,
                    finalOutputs,
                    successful
                );
            }
        }

        @Override
        public Optional<ExecutionStateChanges> getChangesSincePreviousExecution() {
            Optional<AfterPreviousExecutionState> previousExecution = historyRepository.getPreviousExecution(identityString);
            return previousExecution.map(previous -> {
                ImmutableSortedMap<String, CurrentFileCollectionFingerprint> outputsBeforeExecution = snapshotOutputs();
                InputFileChanges inputFileChanges = new InputFileChanges(previous.getInputFileProperties(), inputFileFingerprints);
                AllOutputFileChanges outputFileChanges = new AllOutputFileChanges(previous.getOutputFileProperties(), outputsBeforeExecution);
                return new TransformerExecutionStateChanges(inputFileChanges, outputFileChanges, previous);
                }
            );
        }

        @Override
        public Optional<? extends Iterable<String>> getChangingOutputs() {
            return Optional.of(ImmutableList.of(outputDir.getAbsolutePath()));
        }


        @Override
        public ImmutableSortedMap<String, CurrentFileCollectionFingerprint> snapshotAfterOutputsGenerated() {
            return snapshotOutputs();
        }

        private ImmutableSortedMap<String, CurrentFileCollectionFingerprint> snapshotOutputs() {
            CurrentFileCollectionFingerprint outputFingerprint = outputFileCollectionFingerprinter.fingerprint(ImmutableFileCollection.of(outputDir));
            CurrentFileCollectionFingerprint resultsFileFingerprint = outputFileCollectionFingerprinter.fingerprint(ImmutableFileCollection.of(resultsFile));
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
        private final String initialSubjectFileName;
        private final String primaryInputAbsolutePath;
        private final HashCode primaryInputHash;
        private final HashCode secondaryInputHash;

        public ImmutableTransformationIdentity(String initialSubjectFileName, String primaryInputAbsolutePath, HashCode primaryInputHash, HashCode secondaryInputHash) {
            this.initialSubjectFileName = initialSubjectFileName;
            this.primaryInputAbsolutePath = primaryInputAbsolutePath;
            this.primaryInputHash = primaryInputHash;
            this.secondaryInputHash = secondaryInputHash;
        }

        @Override
        public String getInitialSubjectFileName() {
            return initialSubjectFileName;
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
            if (!initialSubjectFileName.equals(that.initialSubjectFileName)) {
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
        private final String projectPath;
        private final String initialSubjectFileName;
        private final ImmutableList<HashCode> previousTransformers;
        private final String currentSubjectFileName;
        private final HashCode secondaryInputsHash;

        public MutableTransformationIdentity(String projectPath, String initialSubjectFileName, ImmutableList<HashCode> previousTransformers, String currentSubjectFileName, HashCode secondaryInputsHash) {
            this.projectPath = projectPath;
            this.initialSubjectFileName = initialSubjectFileName;
            this.previousTransformers = previousTransformers;
            this.currentSubjectFileName = currentSubjectFileName;
            this.secondaryInputsHash = secondaryInputsHash;
        }

        @Override
        public String getInitialSubjectFileName() {
            return initialSubjectFileName;
        }

        @Override
        public String getIdentity() {
            Hasher hasher = Hashing.newHasher();
            hasher.putString(projectPath);
            hasher.putString(initialSubjectFileName);
            for (HashCode previousTransformer : previousTransformers) {
                hasher.putHash(previousTransformer);
            }
            hasher.putString(currentSubjectFileName);
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

            if (!projectPath.equals(that.projectPath)) {
                return false;
            }
            if (!initialSubjectFileName.equals(that.initialSubjectFileName)) {
                return false;
            }
            if (!previousTransformers.equals(that.previousTransformers)) {
                return false;
            }
            if (!currentSubjectFileName.equals(that.currentSubjectFileName)) {
                return false;
            }
            return secondaryInputsHash.equals(that.secondaryInputsHash);
        }

        @Override
        public int hashCode() {
            int result = projectPath.hashCode();
            result = 31 * result + initialSubjectFileName.hashCode();
            result = 31 * result + previousTransformers.hashCode();
            result = 31 * result + currentSubjectFileName.hashCode();
            result = 31 * result + secondaryInputsHash.hashCode();
            return result;
        }
    }
}
