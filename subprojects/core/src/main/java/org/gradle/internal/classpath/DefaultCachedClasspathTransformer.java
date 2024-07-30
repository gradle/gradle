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
import org.gradle.api.NonNullApi;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.GlobalCacheLocations;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory;
import org.gradle.internal.Either;
import org.gradle.internal.classpath.transforms.ClassTransform;
import org.gradle.internal.classpath.transforms.ClasspathElementTransformFactory;
import org.gradle.internal.classpath.transforms.ClasspathElementTransformFactoryForLegacy;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.ManagedExecutor;
import org.gradle.internal.file.FileAccessTimeJournal;
import org.gradle.internal.file.FileAccessTracker;
import org.gradle.internal.file.FileType;
import org.gradle.internal.fingerprint.classpath.ClasspathFingerprinter;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.vfs.FileSystemAccess;

import java.io.Closeable;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import static java.util.Optional.empty;
import static org.gradle.internal.Either.left;
import static org.gradle.internal.Either.right;
import static org.gradle.internal.UncheckedException.unchecked;

public class DefaultCachedClasspathTransformer implements CachedClasspathTransformer, Closeable {

    private final PersistentCache cache;
    private final FileAccessTracker fileAccessTracker;
    private final ClasspathFingerprinter classpathFingerprinter;
    private final FileSystemAccess fileSystemAccess;
    private final GlobalCacheLocations globalCacheLocations;
    private final FileLockManager fileLockManager;
    private final ManagedExecutor executor;
    private final ParallelTransformExecutor parallelTransformExecutor;
    private final ClasspathElementTransformFactoryForLegacy classpathElementTransformFactoryForLegacy;

    public DefaultCachedClasspathTransformer(
        GlobalScopedCacheBuilderFactory cacheBuilderFactory,
        ClasspathTransformerCacheFactory classpathTransformerCacheFactory,
        FileAccessTimeJournal fileAccessTimeJournal,
        ClasspathFingerprinter classpathFingerprinter,
        FileSystemAccess fileSystemAccess,
        ExecutorFactory executorFactory,
        GlobalCacheLocations globalCacheLocations,
        FileLockManager fileLockManager,
        ClasspathElementTransformFactoryForLegacy classpathElementTransformFactoryForLegacy
    ) {
        this.classpathFingerprinter = classpathFingerprinter;
        this.fileSystemAccess = fileSystemAccess;
        this.globalCacheLocations = globalCacheLocations;
        this.fileLockManager = fileLockManager;
        this.cache = classpathTransformerCacheFactory.createCache(cacheBuilderFactory, fileAccessTimeJournal);
        this.fileAccessTracker = classpathTransformerCacheFactory.createFileAccessTracker(cache, fileAccessTimeJournal);
        this.executor = executorFactory.create("jar transforms", Runtime.getRuntime().availableProcessors());
        this.parallelTransformExecutor = new ParallelTransformExecutor(cache, executor);
        this.classpathElementTransformFactoryForLegacy = classpathElementTransformFactoryForLegacy;
    }

    @Override
    public void close() {
        CompositeStoppable.stoppable(executor, cache).stop();
    }

    @Override
    public ClassPath copyingTransform(ClassPath classPath) {
        if (classPath.isEmpty()) {
            return classPath;
        }
        return transformFiles(classPath, new CopyingClasspathFileTransformer(globalCacheLocations));

    }

    @Override
    public List<URL> copyingTransform(Collection<URL> urls) {
        if (urls.isEmpty()) {
            return ImmutableList.of();
        }
        ClasspathFileTransformer transformer = new CopyingClasspathFileTransformer(globalCacheLocations);
        return parallelTransformExecutor.transformAll(
            urls,
            (url, seen) -> cachedURL(url, transformer, seen)
        );
    }

    @Override
    public ClassPath transform(ClassPath classPath, ClassTransform classTransform) {
        if (classPath.isEmpty()) {
            return classPath;
        }
        return transformFiles(
            classPath,
            customClasspathFileTransformerFor(
                classpathElementTransformFactoryForLegacy,
                classTransform
            )
        );
    }

    private CustomClasspathFileTransformer customClasspathFileTransformerFor(ClasspathElementTransformFactory classpathElementTransformFactory, ClassTransform transform) {
        return new CustomClasspathFileTransformer(
            fileLockManager,
            locationSnapshot -> classpathFingerprinter.fingerprint(locationSnapshot, null).getHash(),
            classpathElementTransformFactory,
            transform
        );
    }

    private ClassPath transformFiles(ClassPath classPath, ClasspathFileTransformer transformer) {
        return DefaultClassPath.of(
            parallelTransformExecutor.transformAll(
                classPath.getAsFiles(),
                (file, seen) -> cachedFile(file, transformer, seen)
            )
        );
    }


    private Optional<Either<URL, Callable<URL>>> cachedURL(URL original, ClasspathFileTransformer transformer, Set<HashCode> seen) {
        if (original.getProtocol().equals("file")) {
            return cachedFile(Convert.urlToFile(original), transformer, seen).map(
                result -> result.fold(
                    file -> left(Convert.fileToURL(file)),
                    transform -> right(() -> Convert.fileToURL(transform.call()))
                )
            );
        }
        return Optional.of(left(original));
    }

    private Optional<Either<File, Callable<File>>> cachedFile(
        File original,
        ClasspathFileTransformer transformer,
        Set<HashCode> seen
    ) {
        FileSystemLocationSnapshot snapshot = snapshotOf(original);
        if (snapshot.getType() == FileType.Missing) {
            return empty();
        }
        if (shouldUseFromCache(original)) {
            final HashCode contentHash = snapshot.getHash();
            if (!seen.add(contentHash)) {
                // Already seen an entry with the same content hash, ignore it
                return empty();
            }
            // It's a new content hash, transform it
            return Optional.of(
                right(() -> transformFile(original, snapshot, transformer))
            );
        }
        return Optional.of(left(original));
    }

    private File transformFile(File original, FileSystemLocationSnapshot snapshot, ClasspathFileTransformer transformer) {
        final File result = transformer.transform(original, snapshot, cache.getBaseDir());
        markAccessed(result, original);
        return result;
    }

    private FileSystemLocationSnapshot snapshotOf(File file) {
        return fileSystemAccess.read(file.getAbsolutePath());
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
    private interface ValueOrTransformProvider<T, U> {
        Optional<Either<U, Callable<U>>> apply(T input, Set<HashCode> seen);
    }

    @NonNullApi
    private static class ParallelTransformExecutor {

        private final PersistentCache cache;
        private final ManagedExecutor executor;

        private ParallelTransformExecutor(PersistentCache cache, ManagedExecutor executor) {
            this.cache = cache;
            this.executor = executor;
        }

        public <T, U> List<U> transformAll(Collection<T> inputs, ValueOrTransformProvider<T, U> valueOrTransformProvider) {
            assert !inputs.isEmpty();
            return cache.useCache(() -> {

                final List<U> results = new ArrayList<>(inputs.size());
                final List<Callable<Void>> transforms = new ArrayList<>(inputs.size());
                final Set<HashCode> seen = new HashSet<>();
                for (T input : inputs) {
                    valueOrTransformProvider.apply(input, seen).ifPresent(valueOrTransform ->
                        valueOrTransform.apply(
                            value -> results.add(value),
                            transform -> {
                                final int index = results.size();
                                results.add(null);
                                transforms.add(() -> {
                                    results.set(index, unchecked(transform));
                                    return null;
                                });
                            }
                        )
                    );
                }

                // Execute all transforms at once
                for (Future<Void> result : unchecked(() -> executor.invokeAll(transforms))) {
                    // Propagate first failure
                    unchecked(result::get);
                }

                return results;
            });
        }
    }

    public static class Convert {

        public static File urlToFile(URL original) {
            return new File(unchecked(original::toURI));
        }

        public static URL fileToURL(File file) {
            return unchecked(file.toURI()::toURL);
        }
    }
}
