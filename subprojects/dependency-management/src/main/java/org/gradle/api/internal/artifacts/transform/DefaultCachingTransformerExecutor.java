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
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.artifacts.transform.TransformerExecutionHistoryRepository.PreviousTransformerExecution;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.api.internal.file.collections.ImmutableFileCollection;
import org.gradle.cache.internal.ProducerGuard;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.initialization.RootBuildLifecycleListener;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.change.Change;
import org.gradle.internal.change.ChangeVisitor;
import org.gradle.internal.execution.CacheHandler;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.WorkExecutor;
import org.gradle.internal.execution.history.AfterPreviousExecutionState;
import org.gradle.internal.execution.history.changes.ExecutionStateChanges;
import org.gradle.internal.execution.impl.steps.UpToDateResult;
import org.gradle.internal.file.TreeType;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.fingerprint.impl.OutputFileCollectionFingerprinter;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.id.UniqueId;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshotter;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.snapshot.impl.ImplementationSnapshot;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;

public class DefaultCachingTransformerExecutor implements CachingTransformerExecutor, RootBuildLifecycleListener {

    private final FileSystemSnapshotter fileSystemSnapshotter;
    private final WorkExecutor<UpToDateResult> workExecutor;
    private final ProducerGuard<CacheKey> producing = ProducerGuard.adaptive();
    private final Map<CacheKey, List<File>> resultHashToResult = new ConcurrentHashMap<CacheKey, List<File>>();
    private final ArtifactTransformListener artifactTransformListener;
    private final TransformerExecutionHistoryRepository historyRepository;
    private final OutputFileCollectionFingerprinter outputFileCollectionFingerprinter;
    private final TemporaryFileProvider temporaryFileProvider;

    public DefaultCachingTransformerExecutor(WorkExecutor<UpToDateResult> workExecutor,
                                             FileSystemSnapshotter fileSystemSnapshotter,
                                             ArtifactTransformListener artifactTransformListener,
                                             TransformerExecutionHistoryRepository historyRepository,
                                             OutputFileCollectionFingerprinter outputFileCollectionFingerprinter,
                                             TemporaryFileProvider temporaryFileProvider) {
        this.workExecutor = workExecutor;
        this.fileSystemSnapshotter = fileSystemSnapshotter;
        this.artifactTransformListener = artifactTransformListener;
        this.historyRepository = historyRepository;
        this.outputFileCollectionFingerprinter = outputFileCollectionFingerprinter;
        this.temporaryFileProvider = temporaryFileProvider;
    }

    @Override
    public void afterStart() {
    }

    @Override
    public void beforeComplete() {
        resultHashToResult.clear();
    }

    @Override
    public boolean contains(File absoluteFile, HashCode inputsHash) {
        return resultHashToResult.containsKey(getCacheKey(absoluteFile, inputsHash));
    }

    @Override
    public List<File> getResult(File primaryInput, Transformer transformer, Describable subject) {
        return transform(primaryInput, transformer, subject);
    }

    private List<File> transform(File primaryInput, Transformer transformer, Describable subject) {
        CacheKey cacheKey = getCacheKey(primaryInput, transformer);
        List<File> results = resultHashToResult.get(cacheKey);
        if (results != null) {
            return results;
        }
        TransformerExecution execution = new TransformerExecution(primaryInput, transformer, cacheKey);
        return fireTransformListeners(transformer, subject, () -> {
                UpToDateResult result = producing.guardByKey(cacheKey, () -> workExecutor.execute(execution));
                if (result.getFailure() != null) {
                    throw UncheckedException.throwAsUncheckedException(result.getFailure());
                }
                List<File> transformerResult = execution.getResult().get();
                resultHashToResult.put(cacheKey, transformerResult);
                return transformerResult;
            });
    }

    private List<File> fireTransformListeners(Transformer transformer, Describable subject, Supplier<List<File>> execution) {
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
        private static final String RESULT_FILE_PROPERTY_NAME = "resultFile";
        private final File primaryInput;
        private final Transformer transformer;
        private final CacheKey cacheKey;
        private List<File> result;
        private File temporaryResultsFile;

        public TransformerExecution(File primaryInput, Transformer transformer, CacheKey cacheKey) {
            this.primaryInput = primaryInput;
            this.transformer = transformer;
            this.cacheKey = cacheKey;
        }

        private File getOutputDir() {
            return historyRepository.getOutputDirectory(primaryInput, cacheKey.getPersistentCacheKey());
        }

        @Override
        public boolean execute() {
            File outputDir = getOutputDir();
            GFileUtils.cleanDirectory(outputDir);
            result = ImmutableList.copyOf(transformer.transform(primaryInput, outputDir));
            return true;
        }

        public Optional<List<File>> getResult() {
            return Optional.ofNullable(result);
        }

        @Override
        public Optional<Duration> getTimeout() {
            return Optional.empty();
        }

        @Override
        public void visitOutputs(OutputVisitor outputVisitor) {
            outputVisitor.visitOutput("output", TreeType.DIRECTORY, ImmutableFileCollection.of(getOutputDir()));
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
                    if (!transformer.isCacheable()) {
                        return Optional.empty();
                    }
                    Optional<T> loadedArtifact = Optional.ofNullable(loader.apply(new TransformerExecutionBuildCacheKey(cacheKey, transformer)));
                    loadedArtifact.ifPresent(artifact -> loadResultFromFile());
                    return loadedArtifact;
                }

                @Override
                public void store(Consumer<BuildCacheKey> storer) {
                    if (transformer.isCacheable()) {
                        storer.accept(new TransformerExecutionBuildCacheKey(cacheKey, transformer));
                    }
                }
            };
        }

        @Override
        public void persistResult(ImmutableSortedMap<String, CurrentFileCollectionFingerprint> finalOutputs, boolean successful, OriginMetadata originMetadata) {
            if (temporaryResultsFile != null) {
                GFileUtils.deleteFileQuietly(temporaryResultsFile);
            }
            getResult().ifPresent(result -> {
                historyRepository.persist(cacheKey.getPersistentCacheKey(), result, finalOutputs.get(OUTPUT_DIRECTORY_PROPERTY_NAME));
            });
        }

        private void loadResultFromFile() {
            try {
                File outputDir = getOutputDir();
                List<String> resultPaths = Files.readAllLines(temporaryResultsFile.toPath(), UTF_8);
                result = ImmutableList.copyOf(resultPaths.stream().map(path -> new File(outputDir, path)).collect(Collectors.toList()));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public Optional<ExecutionStateChanges> getChangesSincePreviousExecution() {
            Optional<PreviousTransformerExecution> previousExecution = historyRepository.getPreviousExecution(cacheKey.getPersistentCacheKey());
            return previousExecution.map(previous -> {
                    CurrentFileCollectionFingerprint fingerprintBeforeExecution = outputFileCollectionFingerprinter.fingerprint(ImmutableFileCollection.of(getOutputDir()));
                    ImmutableList.Builder<Change> builder = ImmutableList.builder();
                    fingerprintBeforeExecution.visitChangesSince(previous.getOutputDirectoryFingerprint(), "outputDirectory", true, new ChangeVisitor() {
                        @Override
                        public boolean visitChange(Change change) {
                            builder.add(change);
                            return false;
                        }
                    });
                    ImmutableList<Change> changes = builder.build();
                    if (changes.isEmpty()) {
                        this.result = previous.getResult();
                    }
                    return new TransformerExecutionStateChanges(changes);
                }
            );
        }

        @Override
        public Optional<? extends Iterable<String>> getChangingOutputs() {
            return Optional.of(ImmutableList.of(getOutputDir().getAbsolutePath()));
        }


        @Override
        public ImmutableSortedMap<String, CurrentFileCollectionFingerprint> snapshotAfterOutputsGenerated() {
            File outputDir = getOutputDir();
            Path outputPath = getOutputDir().toPath();
            temporaryResultsFile = temporaryFileProvider.createTemporaryFile("transform-results", cacheKey.getPersistentCacheKey().toString());
            if (result != null) {
                try {
                    Files.write(temporaryResultsFile.toPath(), result.stream().map(file -> outputPath.relativize(file.toPath()).toString()).collect(Collectors.toList()), UTF_8);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
            CurrentFileCollectionFingerprint outputDirectoryFingerprint = outputFileCollectionFingerprinter.fingerprint(ImmutableFileCollection.of(outputDir));
            CurrentFileCollectionFingerprint resultsFileFingerprint = outputFileCollectionFingerprinter.fingerprint(ImmutableFileCollection.of(temporaryResultsFile));
            return ImmutableSortedMap.of(OUTPUT_DIRECTORY_PROPERTY_NAME, outputDirectoryFingerprint, RESULT_FILE_PROPERTY_NAME, resultsFileFingerprint);
        }

        @Override
        public String getIdentity() {
            return "fake";
        }

        @Override
        public void visitTrees(CacheableTreeVisitor visitor) {
            File outputDir = getOutputDir();
            visitor.visitTree(OUTPUT_DIRECTORY_PROPERTY_NAME, TreeType.DIRECTORY, outputDir);
            if (temporaryResultsFile == null) {
                temporaryResultsFile = temporaryFileProvider.createTemporaryFile("transform-results", cacheKey.getPersistentCacheKey().toString());
            }
            visitor.visitTree(RESULT_FILE_PROPERTY_NAME, TreeType.FILE, temporaryResultsFile);
        }

        @Override
        public String getDisplayName() {
            return transformer.getDisplayName() + ": " + primaryInput.getName();
        }

        private class TransformerExecutionStateChanges implements ExecutionStateChanges {
            private final ImmutableList<Change> changes;

            public TransformerExecutionStateChanges(ImmutableList<Change> changes) {
                this.changes = changes;
            }

            @Override
            public Iterable<Change> getInputFilesChanges() {
                return ImmutableList.of();
            }

            @Override
            public void visitAllChanges(ChangeVisitor visitor) {
                for (Change change : changes) {
                    boolean result = visitor.visitChange(change);
                    if (!result) {
                        return;
                    }
                }
            }

            @Override
            public boolean isRebuildRequired() {
                return true;
            }

            @Override
            public AfterPreviousExecutionState getPreviousExecution() {
                return new AfterPreviousExecutionState() {
                    @Override
                    public OriginMetadata getOriginMetadata() {
                        return OriginMetadata.fromPreviousBuild(UniqueId.generate(), 0);
                    }

                    @Override
                    public boolean isSuccessful() {
                        return true;
                    }

                    @Override
                    public ImmutableSortedMap<String, FileCollectionFingerprint> getInputFileProperties() {
                        return ImmutableSortedMap.of();
                    }

                    @Override
                    public ImmutableSortedMap<String, FileCollectionFingerprint> getOutputFileProperties() {
                        return ImmutableSortedMap.of();
                    }

                    @Override
                    public ImplementationSnapshot getImplementation() {
                        return ImplementationSnapshot.of(transformer.getImplementationClass().getName(), transformer.getSecondaryInputHash());
                    }

                    @Override
                    public ImmutableList<ImplementationSnapshot> getAdditionalImplementations() {
                        return ImmutableList.of();
                    }

                    @Override
                    public ImmutableSortedMap<String, ValueSnapshot> getInputProperties() {
                        return ImmutableSortedMap.of();
                    }
                };
            }
        }
    }
    
    private static class TransformerExecutionBuildCacheKey implements BuildCacheKey {
        private final CacheKey cacheKey;
        private final Transformer transformer;

        public TransformerExecutionBuildCacheKey(CacheKey cacheKey, Transformer transformer) {

            this.cacheKey = cacheKey;
            this.transformer = transformer;
        }

        @Override
        public String getHashCode() {
            return cacheKey.getPersistentCacheKey().toString();
        }

        @Override
        public String getDisplayName() {
            return cacheKey.getPersistentCacheKey() + " for transformer " + transformer.getDisplayName();
        }
    }
}
