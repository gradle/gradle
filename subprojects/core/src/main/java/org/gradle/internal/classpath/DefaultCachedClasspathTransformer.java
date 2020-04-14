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
import org.gradle.util.GFileUtils;

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
    private final VirtualFileSystem virtualFileSystem;
    private final ClasspathWalker classpathWalker;
    private final ClasspathBuilder classpathBuilder;

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
    public ClassPath transform(ClassPath classPath, Usage usage) {
        return cache.useCache(() -> {
            List<File> originalFiles = classPath.getAsFiles();
            List<File> cachedFiles = new ArrayList<>(originalFiles.size());
            for (File file : originalFiles) {
                cached(file, usage, cachedFiles::add);
            }
            return DefaultClassPath.of(cachedFiles);
        });
    }

    @Override
    public Collection<URL> transform(Collection<URL> urls, Usage usage) {
        return cache.useCache(() -> {
            List<URL> cachedFiles = new ArrayList<>(urls.size());
            for (URL url : urls) {
                if (url.getProtocol().equals("file")) {
                    try {
                        cached(new File(url.toURI()), usage, f -> {
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

    private void cached(File original, Usage usage, Consumer<File> dest) {
        if (shouldUseFromCache(original)) {
            File result = getCachedJar(usage, original, cache.getBaseDir());
            if (result != null) {
                dest.accept(result);
            }
        } else if (original.exists()) {
            dest.accept(original);
        }
    }

    @Nullable
    public File getCachedJar(CachedClasspathTransformer.Usage usage, File original, File cacheDir) {
        CompleteFileSystemLocationSnapshot snapshot = virtualFileSystem.read(original.getAbsolutePath(), s -> s);
        if (snapshot.getType() == FileType.Missing) {
            return null;
        }
        if (usage == CachedClasspathTransformer.Usage.BuildLogic) {
            String name = snapshot.getType() == FileType.Directory ? original.getName() + ".jar" : original.getName();
            File transformed = new File(cacheDir, snapshot.getHash().toString() + '/' + name);
            if (!transformed.isFile()) {
                // Unpack and rebuild the jar. Later, this will apply some transformations to the classes
                classpathBuilder.jar(transformed, builder -> classpathWalker.visit(original, entry -> {
                    builder.put(entry.getName(), entry.getContent());
                }));
            }
            fileAccessTracker.markAccessed(transformed);
            return transformed;
        } else if (usage == CachedClasspathTransformer.Usage.Other) {
            if (snapshot.getType() != FileType.RegularFile) {
                return original;
            }
            File cachedFile = new File(cacheDir, "o_" + snapshot.getHash().toString() + '/' + original.getName());
            if (!cachedFile.isFile()) {
                // Just copy the jar
                GFileUtils.copyFile(original, cachedFile);
            }
            fileAccessTracker.markAccessed(cachedFile);
            return cachedFile;
        } else {
            throw new IllegalArgumentException(String.format("Unknown usage %s", usage));
        }
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
