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
import org.gradle.internal.UncheckedException;
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

import static com.google.common.collect.Sets.newConcurrentHashSet;
import static java.util.Optional.empty;
import static java.util.stream.Collectors.toList;
import static org.gradle.internal.UncheckedException.callUnchecked;

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
        return transformFiles(classPath, fileTransformerFor(transform));
    }

    @Override
    public ClassPath transform(ClassPath classPath, StandardTransform transform, Transform additional) {
        if (classPath.isEmpty()) {
            return classPath;
        }
        return transformFiles(classPath, instrumentingClasspathFileTransformerFor(new CompositeTransformer(additional, transformerFor(transform))));
    }

    @Override
    public Collection<URL> transform(Collection<URL> urls, StandardTransform transform) {
        if (urls.isEmpty()) {
            return ImmutableList.of();
        }
        ClasspathFileTransformer transformer = fileTransformerFor(transform);
        return transformAll(
            urls,
            (url, seen) -> cachedURL(url, transformer, seen)
        );
    }

    private ClassPath transformFiles(ClassPath classPath, ClasspathFileTransformer transformer) {
        return DefaultClassPath.of(
            transformAll(
                classPath.getAsFiles(),
                (file, seen) -> cachedFile(file, transformer, seen)
            )
        );
    }

    @FunctionalInterface
    private interface ConditionalTransform<T, U> {
        Optional<U> apply(T input, Set<HashCode> seen);
    }

    private <T, U> List<U> transformAll(Collection<T> inputs, ConditionalTransform<T, U> transform) {
        assert !inputs.isEmpty();
        return cache.useCache(() -> {
            final List<Try<Optional<U>>> results = unchecked(() ->
                executor.invokeAll(transformOperationsFor(inputs, transform)).stream().map(
                    result -> Try.ofFailable(result::get)
                ).collect(toList())
            );
            List<U> successes = new ArrayList<>(results.size());
            List<Throwable> failures = new ArrayList<>();
            for (Try<Optional<U>> result : results) {
                result.ifSuccessfulOrElse(
                    successful -> successful.ifPresent(successes::add),
                    failures::add
                );
            }
            switch (failures.size()) {
                case 0:
                    return successes;
                case 1:
                    throw UncheckedException.throwAsUncheckedException(failures.get(0));
                default:
                    throw new DefaultMultiCauseException(
                        "Operation could not be completed due to multiple failures.",
                        failures
                    );
            }
        });
    }

    private <T, U> List<Callable<Optional<U>>> transformOperationsFor(Collection<T> inputs, ConditionalTransform<T, U> transform) {
        Set<HashCode> seen = newConcurrentHashSet();
        List<Callable<Optional<U>>> operations = new ArrayList<>(inputs.size());
        for (T input : inputs) {
            operations.add(() -> transform.apply(input, seen));
        }
        return operations;
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

    private Optional<URL> cachedURL(URL original, ClasspathFileTransformer transformer, Set<HashCode> seen) {
        if (original.getProtocol().equals("file")) {
            return unchecked(() ->
                cachedFile(new File(original.toURI()), transformer, seen).map(
                    DefaultCachedClasspathTransformer::fileToURL
                )
            );
        }
        return Optional.of(original);
    }

    private Optional<File> cachedFile(File original, ClasspathFileTransformer transformer, Set<HashCode> seen) {
        FileSystemLocationSnapshot snapshot = snapshotOf(original);
        if (snapshot.getType() == FileType.Missing) {
            return empty();
        }
        if (shouldUseFromCache(original)) {
            if (!seen.add(snapshot.getHash())) {
                // Already seen an entry with the same content hash, so skip it
                return empty();
            }
            final File result = transformer.transform(original, snapshot, cache.getBaseDir());
            markAccessed(result, original);
            return Optional.of(result);
        }
        return Optional.of(original);
    }

    private FileSystemLocationSnapshot snapshotOf(File file) {
        return fileSystemAccess.read(file.getAbsolutePath(), s -> s);
    }

    private boolean shouldUseFromCache(File original) {
        // Transform everything that has not already been transformed
        return !original.toPath().startsWith(cache.getBaseDir().toPath());
    }

    private static URL fileToURL(File file) {
        return unchecked(() -> file.toURI().toURL());
    }

    private void markAccessed(File result, File original) {
        if (!result.equals(original)) {
            fileAccessTracker.markAccessed(result);
        }
    }

    private static <T> T unchecked(Callable<T> callable) {
        return callUnchecked(callable);
    }
}
