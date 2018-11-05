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
import org.gradle.api.artifacts.transform.TransformInvocationException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.file.collections.ImmutableFileCollection;
import org.gradle.cache.internal.ProducerGuard;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.internal.Try;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.change.Change;
import org.gradle.internal.change.ChangeVisitor;
import org.gradle.internal.execution.CacheHandler;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.WorkExecutor;
import org.gradle.internal.execution.history.AfterPreviousExecutionState;
import org.gradle.internal.execution.history.changes.AbstractFingerprintChanges;
import org.gradle.internal.execution.history.changes.ExecutionStateChanges;
import org.gradle.internal.execution.impl.steps.UpToDateResult;
import org.gradle.internal.file.TreeType;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Stream;

public class DefaultTransformerInvoker implements TransformerInvoker {

    private final FileSystemSnapshotter fileSystemSnapshotter;
    private final WorkExecutor<UpToDateResult> workExecutor;
    private final ProducerGuard<CacheKey> producing = ProducerGuard.adaptive();
    private final Map<CacheKey, ImmutableList<File>> resultHashToResult = new ConcurrentHashMap<CacheKey, ImmutableList<File>>();
    private final ArtifactTransformListener artifactTransformListener;
    private final TransformerExecutionHistoryRepository historyRepository;
    private final OutputFileCollectionFingerprinter outputFileCollectionFingerprinter;

    public DefaultTransformerInvoker(WorkExecutor<UpToDateResult> workExecutor,
                                     FileSystemSnapshotter fileSystemSnapshotter,
                                     ArtifactTransformListener artifactTransformListener,
                                     TransformerExecutionHistoryRepository historyRepository, OutputFileCollectionFingerprinter outputFileCollectionFingerprinter) {
        this.workExecutor = workExecutor;
        this.fileSystemSnapshotter = fileSystemSnapshotter;
        this.artifactTransformListener = artifactTransformListener;
        this.historyRepository = historyRepository;
        this.outputFileCollectionFingerprinter = outputFileCollectionFingerprinter;
    }

    public void clearInMemoryCache() {
        resultHashToResult.clear();
    }

    @Override
    public boolean hasCachedResult(File primaryInput, Transformer transformer) {
        return resultHashToResult.containsKey(getCacheKey(primaryInput.getAbsoluteFile(), transformer.getSecondaryInputHash()));
    }

    @Override
    public Try<ImmutableList<File>> invoke(TransformerInvocation invocation) {
        return Try.ofFailable(() ->
            invoke(invocation.getPrimaryInput(), invocation.getTransformer(), invocation.getSubjectBeingTransformed()).get()
        ).mapFailure(e -> wrapInTransformInvocationException(invocation, e));
    }

    private Exception wrapInTransformInvocationException(TransformerInvocation invocation, Throwable originalFailure) {
        return new TransformInvocationException(invocation.getPrimaryInput().getAbsoluteFile(), invocation.getTransformer().getImplementationClass(), originalFailure);
    }

    private Try<ImmutableList<File>> invoke(File primaryInput, Transformer transformer, Describable subject) {
        CacheKey cacheKey = getCacheKey(primaryInput, transformer);
        ImmutableList<File> results = resultHashToResult.get(cacheKey);
        if (results != null) {
            return Try.successful(results);
        }
        return fireTransformListeners(transformer, subject, () -> {
            TransformerExecution execution = new TransformerExecution(primaryInput, transformer, cacheKey);
            UpToDateResult outcome = producing.guardByKey(cacheKey, () -> workExecutor.execute(execution));
            Try<ImmutableList<File>> result = execution.getResult(outcome);
            result.ifSuccessful(transformerResult -> resultHashToResult.put(cacheKey, transformerResult));
            return result;
        });
    }

    private Try<ImmutableList<File>> fireTransformListeners(Transformer transformer, Describable subject, Supplier<Try<ImmutableList<File>>> execution) {
        artifactTransformListener.beforeTransformerInvocation(transformer, subject);
        try {
            return execution.get();
        } finally {
            artifactTransformListener.afterTransformerInvocation(transformer, subject);
        }
    }

    private CacheKey getCacheKey(File primaryInput, Transformer transformer) {
        return getCacheKey(primaryInput, transformer.getSecondaryInputHash());
    }

    private CacheKey getCacheKey(File inputFile, HashCode inputsHash) {
        FileSystemLocationSnapshot snapshot = fileSystemSnapshotter.snapshot(inputFile);
        return new CacheKey(inputsHash, snapshot.getAbsolutePath(), snapshot.getHash());
    }

    /**
     * A lightweight key for in-memory caching of transformation results.
     * Computing the hash key for the persistent cache is a rather expensive
     * operation, so we only calculate it when we have a cache miss in memory.
     */
    private static class CacheKey {
        private final String absolutePath;
        private final HashCode fileContentHash;
        private final HashCode inputHash;
        private HashCode persistentHashCode;

        public CacheKey(HashCode inputHash, String absolutePath, HashCode fileContentHash) {
            this.absolutePath = absolutePath;
            this.fileContentHash = fileContentHash;
            this.inputHash = inputHash;
        }

        public HashCode getPersistentCacheKey() {
            if (persistentHashCode == null) {
                Hasher hasher = Hashing.newHasher();
                hasher.putHash(inputHash);
                hasher.putString(absolutePath);
                hasher.putHash(fileContentHash);
                persistentHashCode = hasher.hash();
            }
            return persistentHashCode;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            CacheKey cacheKey = (CacheKey) o;

            if (!fileContentHash.equals(cacheKey.fileContentHash)) {
                return false;
            }
            if (!inputHash.equals(cacheKey.inputHash)) {
                return false;
            }
            return absolutePath.equals(cacheKey.absolutePath);
        }

        @Override
        public int hashCode() {
            int result = fileContentHash.hashCode();
            result = 31 * result + absolutePath.hashCode();
            result = 31 * result + inputHash.hashCode();
            return result;
        }
    }

    private class TransformerExecution implements UnitOfWork {
        private static final String OUTPUT_DIRECTORY_PROPERTY_NAME = "outputDirectory";
        private static final String RESULTS_FILE_PROPERTY_NAME = "resultsFile";
        private static final String INPUT_FILE_PATH_PREFIX = "i/";
        private static final String OUTPUT_FILE_PATH_PREFIX = "o/";

        private final File primaryInput;
        private final Transformer transformer;
        private final CacheKey cacheKey;

        public TransformerExecution(File primaryInput, Transformer transformer, CacheKey cacheKey) {
            this.primaryInput = primaryInput;
            this.transformer = transformer;
            this.cacheKey = cacheKey;
        }

        private File getOutputDir() {
            return historyRepository.getOutputDirectory(primaryInput, cacheKey.getPersistentCacheKey().toString());
        }

        private File getResultsFile() {
            return historyRepository.getOutputDirectory(primaryInput, cacheKey.getPersistentCacheKey() + "-results");
        }

        @Override
        public boolean execute() {
            File outputDir = getOutputDir();
            File resultsFile = getResultsFile();
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
            UncheckedException.asUnchecked(() -> Files.write(resultsFile.toPath(), (Iterable<String>) relativePaths::iterator));
        }

        public Try<ImmutableList<File>> getResult(UpToDateResult outcome) {
            if (outcome.getFailure() != null) {
                return Try.failure(outcome.getFailure());
            }
            return Try.ofFailable(() -> {
                Path transformerResultsPath = getResultsFile().toPath();
                return Files.lines(transformerResultsPath, StandardCharsets.UTF_8).map(path -> {
                    if (path.startsWith(OUTPUT_FILE_PATH_PREFIX)) {
                        return new File(getOutputDir(), path.substring(2));
                    }
                    if (path.startsWith(INPUT_FILE_PATH_PREFIX)) {
                        return new File(primaryInput, path.substring(2));
                    }
                    throw new IllegalStateException("Cannot parse result path string: " + path);
                }).collect(toImmutableList());
            });
        }

        @Override
        public Optional<Duration> getTimeout() {
            return Optional.empty();
        }

        @Override
        public void visitOutputs(OutputVisitor outputVisitor) {
            outputVisitor.visitOutput(OUTPUT_DIRECTORY_PROPERTY_NAME, TreeType.DIRECTORY, ImmutableFileCollection.of(getOutputDir()));
            outputVisitor.visitOutput(RESULTS_FILE_PROPERTY_NAME, TreeType.FILE, ImmutableFileCollection.of(getResultsFile()));
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
            historyRepository.persist(cacheKey.getPersistentCacheKey(),
                originMetadata,
                // TODO: only use implementation hash
                ImplementationSnapshot.of(transformer.getImplementationClass().getName(), transformer.getSecondaryInputHash()),
                finalOutputs,
                successful
            );
        }

        @Override
        public Optional<ExecutionStateChanges> getChangesSincePreviousExecution() {
            Optional<AfterPreviousExecutionState> previousExecution = historyRepository.getPreviousExecution(cacheKey.getPersistentCacheKey());
            return previousExecution.filter(AfterPreviousExecutionState::isSuccessful).map(previous -> {
                ImmutableSortedMap<String, CurrentFileCollectionFingerprint> outputsBeforeExecution = snapshotOutputs();
                AllOutputFileChanges outputFileChanges = new AllOutputFileChanges(previous.getOutputFileProperties(), outputsBeforeExecution);
                return new TransformerExecutionStateChanges(outputFileChanges, previous);
                }
            );
        }

        @Override
        public Optional<? extends Iterable<String>> getChangingOutputs() {
            return Optional.of(ImmutableList.of(getOutputDir().getAbsolutePath()));
        }


        @Override
        public ImmutableSortedMap<String, CurrentFileCollectionFingerprint> snapshotAfterOutputsGenerated() {
            return snapshotOutputs();
        }

        private ImmutableSortedMap<String, CurrentFileCollectionFingerprint> snapshotOutputs() {
            CurrentFileCollectionFingerprint outputFingerprint = outputFileCollectionFingerprinter.fingerprint(ImmutableFileCollection.of(getOutputDir()));
            CurrentFileCollectionFingerprint resultsFileFingerprint = outputFileCollectionFingerprinter.fingerprint(ImmutableFileCollection.of(getResultsFile()));
            return ImmutableSortedMap.of(
                OUTPUT_DIRECTORY_PROPERTY_NAME, outputFingerprint,
                RESULTS_FILE_PROPERTY_NAME, resultsFileFingerprint);
        }

        @Override
        public String getIdentity() {
            return "fake";
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
            private final AllOutputFileChanges outputFileChanges;
            private final AfterPreviousExecutionState afterPreviousExecutionState;

            public TransformerExecutionStateChanges(AllOutputFileChanges outputFileChanges, AfterPreviousExecutionState afterPreviousExecutionState) {
                this.outputFileChanges = outputFileChanges;
                this.afterPreviousExecutionState = afterPreviousExecutionState;
            }

            @Override
            public Iterable<Change> getInputFilesChanges() {
                return ImmutableList.of();
            }

            @Override
            public void visitAllChanges(ChangeVisitor visitor) {
                outputFileChanges.accept(visitor);
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
    
    private static <T> Collector<T, ?, ImmutableList<T>> toImmutableList() {
        return Collector.of(
            ImmutableList::<T>builder,
            (builder, value) -> builder.add(value),
            (builder1, builder2) -> builder1.addAll(builder2.build()),
            builder -> builder.build()
        );
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
}
