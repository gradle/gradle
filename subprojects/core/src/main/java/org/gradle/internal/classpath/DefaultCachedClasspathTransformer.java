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

import org.gradle.cache.CacheRepository;
import org.gradle.cache.PersistentCache;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.file.FileAccessTimeJournal;
import org.gradle.internal.file.FileType;
import org.gradle.internal.resource.local.FileAccessTracker;
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot;
import org.gradle.internal.vfs.VirtualFileSystem;

import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

public class DefaultCachedClasspathTransformer implements CachedClasspathTransformer, Closeable {

    private final PersistentCache cache;
    private final FileAccessTracker fileAccessTracker;
    private final ClasspathWalker classpathWalker;
    private final ClasspathBuilder classpathBuilder;
    private final VirtualFileSystem virtualFileSystem;

    public DefaultCachedClasspathTransformer(
        CacheRepository cacheRepository,
        ClasspathTransformerCacheFactory classpathTransformerCacheFactory,
        FileAccessTimeJournal fileAccessTimeJournal,
        ClasspathWalker classpathWalker,
        ClasspathBuilder classpathBuilder,
        VirtualFileSystem virtualFileSystem
    ) {
        this.classpathWalker = classpathWalker;
        this.classpathBuilder = classpathBuilder;
        this.virtualFileSystem = virtualFileSystem;
        this.cache = classpathTransformerCacheFactory.createCache(cacheRepository, fileAccessTimeJournal);
        this.fileAccessTracker = classpathTransformerCacheFactory.createFileAccessTracker(fileAccessTimeJournal);
    }

    @Override
    public ClassPath transform(ClassPath classPath, StandardTransform transform) {
        return transform(classPath, fileTransformerFor(transform));
    }

    @Override
    public ClassPath transform(ClassPath classPath, StandardTransform transform, Transform additional) {
        return transform(classPath, new InstrumentingClasspathFileTransformer(classpathWalker, classpathBuilder, new CompositeTransformer(additional, tranformerFor(transform))));
    }

    @Override
    public Collection<URL> transform(Collection<URL> urls, StandardTransform transform) {
        ClasspathFileTransformer transformer = fileTransformerFor(transform);
        return cache.useCache(() -> {
            List<URL> cachedFiles = new ArrayList<>(urls.size());
            for (URL url : urls) {
                if (url.getProtocol().equals("file")) {
                    try {
                        cached(new File(url.toURI()), transformer, f -> {
                            try {
                                cachedFiles.add(f.toURI().toURL());
                            } catch (MalformedURLException e) {
                                throw UncheckedException.throwAsUncheckedException(e);
                            }
                        });
                    } catch (URISyntaxException e) {
                        throw UncheckedException.throwAsUncheckedException(e);
                    }
                } else {
                    cachedFiles.add(url);
                }
            }
            return cachedFiles;
        });
    }

    private Transform tranformerFor(StandardTransform transform) {
        if (transform == StandardTransform.BuildLogic) {
            return new InstrumentingTransformer();
        } else {
            throw new UnsupportedOperationException("Not implemented yet.");
        }
    }

    private ClassPath transform(ClassPath classPath, ClasspathFileTransformer transformer) {
        return cache.useCache(() -> {
            List<File> originalFiles = classPath.getAsFiles();
            List<File> cachedFiles = new ArrayList<>(originalFiles.size());
            for (File file : originalFiles) {
                cached(file, transformer, cachedFiles::add);
            }
            return DefaultClassPath.of(cachedFiles);
        });
    }

    private ClasspathFileTransformer fileTransformerFor(StandardTransform transform) {
        switch (transform) {
            case BuildLogic:
                return new InstrumentingClasspathFileTransformer(classpathWalker, classpathBuilder, new InstrumentingTransformer());
            case None:
                return new CopyingClasspathFileTransformer();
            default:
                throw new IllegalArgumentException();
        }
    }

    private void cached(File original, ClasspathFileTransformer transformer, Consumer<File> dest) {
        if (shouldUseFromCache(original)) {
            File result = getCachedJar(transformer, original, cache.getBaseDir());
            if (result != null) {
                dest.accept(result);
            }
        } else if (original.exists()) {
            dest.accept(original);
        }
    }

    @Nullable
    private File getCachedJar(ClasspathFileTransformer transformer, File original, File cacheDir) {
        CompleteFileSystemLocationSnapshot snapshot = virtualFileSystem.read(original.getAbsolutePath(), s -> s);
        if (snapshot.getType() == FileType.Missing) {
            return null;
        }
        File result = transformer.transform(original, snapshot, cacheDir);
        if (!result.equals(original)) {
            fileAccessTracker.markAccessed(result);
        }
        return result;
    }

    private boolean shouldUseFromCache(File original) {
        // Transform everything that has not already been transformed
        return !original.toPath().startsWith(cache.getBaseDir().toPath());
    }

    @Override
    public void close() {
        cache.close();
    }
}
