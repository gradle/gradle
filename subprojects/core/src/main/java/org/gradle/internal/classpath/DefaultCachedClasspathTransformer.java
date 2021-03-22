/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.classpath;

import com.google.common.collect.ImmutableList;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.GlobalCacheLocations;
import org.gradle.cache.PersistentCache;
import org.gradle.internal.Try;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.ManagedExecutor;
import org.gradle.internal.exceptions.DefaultMultiCauseException;
import org.gradle.internal.file.FileAccessTimeJournal;
import org.gradle.internal.file.FileAccessTracker;
import org.gradle.internal.file.FileType;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.vfs.FileSystemAccess;

import java.io.Closeable;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.function.Function;

import static com.google.common.collect.Sets.newConcurrentHashSet;
import static java.lang.String.format;
import static java.util.Optional.empty;
import static org.gradle.internal.UncheckedException.throwAsUncheckedException;
import static org.gradle.internal.UncheckedException.unchecked;
import static org.gradle.internal.classpath.Either.left;
import static org.gradle.internal.classpath.Either.right;

public class DefaultCachedClasspathTransformer implements CachedClasspathTransformer, Closeable {

    private final PersistentCache cache;
    private final FileAccessTracker fileAccessTracker;
    private final ClasspathWalker classpathWalker;
    private final ClasspathBuilder classpathBuilder;
    private final FileSystemAccess fileSystemAccess;
    private final GlobalCacheLocations globalCacheLocations;
    private final FileLockManager fileLockManager;
    private final ManagedExecutor executor;

    public DefaultCachedClasspathTransformer(
        CacheRepository cacheRepository,
        ClasspathTransformerCacheFactory classpathTransformerCacheFactory,
        FileAccessTimeJournal fileAccessTimeJournal,
        ClasspathWalker classpathWalker,
        ClasspathBuilder classpathBuilder,
        FileSystemAccess fileSystemAccess,
        ExecutorFactory executorFactory,
        GlobalCacheLocations globalCacheLocations,
        FileLockManager fileLockManager
    ) {
        this.classpathWalker = classpathWalker;
        this.classpathBuilder = classpathBuilder;
        this.fileSystemAccess = fileSystemAccess;
        this.globalCacheLocations = globalCacheLocations;
        this.fileLockManager = fileLockManager;
        this.cache = classpathTransformerCacheFactory.createCache(cacheRepository, fileAccessTimeJournal);
        this.fileAccessTracker = classpathTransformerCacheFactory.createFileAccessTracker(fileAccessTimeJournal);
        this.executor = executorFactory.create("jar transforms", Runtime.getRuntime().availableProcessors());
    }

    @Override
    public void close() {
        CompositeStoppable.stoppable(executor, cache).stop();
    }

    @Override
    public ClassPath transform(ClassPath classPath, StandardTransform transform) {
        if (classPath.isEmpty()) {
            return classPath;
        }
        return transformFiles(
            classPath,
            fileTransformerFor(transform)
        );
    }

    @Override
    public ClassPath transform(ClassPath classPath, StandardTransform transform, Transform additional) {
        if (classPath.isEmpty()) {
            return classPath;
        }
        return transformFiles(
            classPath,
            instrumentingClasspathFileTransformerFor(
                new CompositeTransformer(additional, transformerFor(transform))
            )
        );
    }

    @Override
    public List<URL> transform(Collection<URL> urls, StandardTransform transform) {
        if (urls.isEmpty()) {
            return ImmutableList.of();
        }
        ClasspathFileTransformer transformer = fileTransformerFor(transform);
        return transformAll(
            urls,
            (url, context) -> cachedURL(url, transformer, context),
            Convert::fileToURL
        );
    }

    private ClassPath transformFiles(ClassPath classPath, ClasspathFileTransformer transformer) {
        return DefaultClassPath.of(
            transformAll(
                classPath.getAsFiles(),
                (file, context) -> cachedFile(file, transformer, context),
                file -> file
            )
        );
    }

    private Transform transformerFor(StandardTransform transform) {
        if (transform == StandardTransform.BuildLogic) {
            return new InstrumentingTransformer();
        } else {
            throw new UnsupportedOperationException("Not implemented yet.");
        }
    }

    private ClasspathFileTransformer fileTransformerFor(StandardTransform transform) {
        switch (transform) {
            case BuildLogic:
                return instrumentingClasspathFileTransformerFor(new InstrumentingTransformer());
            case None:
                return new CopyingClasspathFileTransformer(globalCacheLocations);
            default:
                throw new IllegalArgumentException();
        }
    }

    private InstrumentingClasspathFileTransformer instrumentingClasspathFileTransformerFor(CachedClasspathTransformer.Transform transform) {
        return new InstrumentingClasspathFileTransformer(fileLockManager, classpathWalker, classpathBuilder, transform);
    }

    private Optional<Either<HashCode, URL>> cachedURL(URL original, ClasspathFileTransformer transformer, ConcurrentTransformContext seen) {
        if (original.getProtocol().equals("file")) {
            return unchecked(() ->
                cachedFile(new File(original.toURI()), transformer, seen).map(
                    result -> result.map(Convert::fileToURL)
                )
            );
        }
        return Optional.of(right(original));
    }

    private Optional<Either<HashCode, File>> cachedFile(File original, ClasspathFileTransformer transformer, ConcurrentTransformContext context) {
        FileSystemLocationSnapshot snapshot = snapshotOf(original);
        if (snapshot.getType() == FileType.Missing) {
            return empty();
        }
        if (shouldUseFromCache(original)) {
            final HashCode contentHash = snapshot.getHash();
            if (context.register(contentHash)) {
                // It's a new content hash, transform it
                final File result = transformer.transform(original, snapshot, cache.getBaseDir());
                context.commit(contentHash, result);
                markAccessed(result, original);
                return Optional.of(right(result));
            }
            // Already seen an entry with the same content hash, just return it
            return Optional.of(left(contentHash));
        }
        return Optional.of(right(original));
    }

    private FileSystemLocationSnapshot snapshotOf(File file) {
        return fileSystemAccess.read(file.getAbsolutePath(), s -> s);
    }

    private boolean shouldUseFromCache(File original) {
        // Transform everything that has not already been transformed
        return !original.toPath().startsWith(cache.getBaseDir().toPath());
    }

    private void markAccessed(File result, File original) {
        if (!result.equals(original)) {
            fileAccessTracker.markAccessed(result);
        }
    }

    @FunctionalInterface
    private interface ConcurrentTransform<T, U> {
        Optional<Either<HashCode, U>> apply(T input, ConcurrentTransformContext context);
    }

    private <T, U> List<U> transformAll(
        Collection<T> inputs,
        ConcurrentTransform<T, U> transform,
        Function<File, U> load
    ) {
        assert !inputs.isEmpty();
        return cache.useCache(() -> {

            // Start all transforms at once
            final ConcurrentTransformContext context = new ConcurrentTransformContext();
            final List<Future<Optional<Either<HashCode, U>>>> futureResults =
                unchecked(() -> executor.invokeAll(transformOperationsFor(inputs, transform, context)));

            // Collect the results
            final ImmutableList.Builder<U> results = ImmutableList.builderWithExpectedSize(futureResults.size());
            final List<Throwable> failures = new ArrayList<>();
            for (Future<Optional<Either<HashCode, U>>> future : futureResults) {
                Try.ofFailable(() ->
                    future.get().map(
                        hashOrResult -> hashOrResult.fold(
                            hash -> load.apply(context.get(hash)),
                            r -> r
                        )
                    )
                ).ifSuccessfulOrElse(
                    optionalResult -> optionalResult.ifPresent(results::add),
                    failures::add
                );
            }

            // Group failures
            switch (failures.size()) {
                case 0:
                    return results.build();
                case 1:
                    throw throwAsUncheckedException(failures.get(0));
                default:
                    throw new DefaultMultiCauseException(
                        "Operation could not be completed due to multiple failures.",
                        failures
                    );
            }
        });
    }

    private <T, U> List<Callable<Optional<Either<HashCode, U>>>> transformOperationsFor(
        Collection<T> inputs,
        ConcurrentTransform<T, U> transform,
        ConcurrentTransformContext context
    ) {
        List<Callable<Optional<Either<HashCode, U>>>> operations = new ArrayList<>(inputs.size());
        for (T input : inputs) {
            operations.add(() -> transform.apply(input, context));
        }
        return operations;
    }

    private static class ConcurrentTransformContext {
        private final Set<HashCode> jobs = newConcurrentHashSet();
        private final ConcurrentHashMap<HashCode, File> results = new ConcurrentHashMap<>();

        public boolean register(HashCode hash) {
            return jobs.add(hash);
        }

        public void commit(HashCode hash, File result) {
            results.put(hash, result);
        }

        public File get(HashCode hash) {
            final File file = results.get(hash);
            if (file == null) {
                throw new IllegalStateException(format("Instrumentation result for hash %s is missing.", hash));
            }
            return file;
        }
    }

    private static class Convert {
        public static URL fileToURL(File file) {
            return unchecked(() -> file.toURI().toURL());
        }
    }
}
