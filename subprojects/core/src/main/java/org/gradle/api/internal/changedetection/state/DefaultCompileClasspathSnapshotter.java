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

package org.gradle.api.internal.changedetection.state;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteStreams;
import org.apache.commons.io.IOUtils;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.resources.AbstractSnapshotter;
import org.gradle.api.internal.changedetection.resources.CachingResourceSnapshotter;
import org.gradle.api.internal.changedetection.resources.ClasspathResourceSnapshotter;
import org.gradle.api.internal.changedetection.resources.ResourceSnapshotter;
import org.gradle.api.internal.changedetection.resources.SnapshotCollector;
import org.gradle.api.internal.changedetection.resources.SnapshottableReadableResource;
import org.gradle.api.internal.changedetection.resources.SnapshottableResource;
import org.gradle.api.internal.tasks.compile.ApiClassExtractor;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.util.DeprecationLogger;
import org.gradle.util.internal.Java9ClassReader;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;

import static com.google.common.base.Charsets.UTF_8;

public class DefaultCompileClasspathSnapshotter extends AbstractFileCollectionSnapshotter implements CompileClasspathSnapshotter {
    private final StringInterner stringInterner;
    private final ResourceSnapshotter resourceSnapshotter;

    public DefaultCompileClasspathSnapshotter(FileSystemSnapshotter fileSystemSnapshotter, StringInterner stringInterner, PersistentIndexedCache<HashCode, HashCode> signatureCache) {
        super(fileSystemSnapshotter, stringInterner);
        this.stringInterner = stringInterner;
        this.resourceSnapshotter = new CachingResourceSnapshotter(
            new ClasspathResourceSnapshotter(new CompileClasspathEntrySnapshotter(signatureCache), stringInterner),
            signatureCache
        );
    }

    @Override
    public Class<? extends FileCollectionSnapshotter> getRegisteredType() {
        return CompileClasspathSnapshotter.class;
    }

    @Override
    protected FileCollectionSnapshotBuilder createFileCollectionSnapshotBuilder(SnapshotNormalizationStrategy normalizationStrategy, TaskFilePropertyCompareStrategy compareStrategy) {
        return new FileCollectionSnapshotBuilder(TaskFilePropertySnapshotNormalizationStrategy.NONE, TaskFilePropertyCompareStrategy.ORDERED, stringInterner);
    }

    @Override
    protected ResourceSnapshotter createSnapshotter(SnapshotNormalizationStrategy normalizationStrategy, TaskFilePropertyCompareStrategy compareStrategy) {
        return resourceSnapshotter;
    }

    private static class CompileClasspathEntrySnapshotter extends AbstractSnapshotter {
        private final PersistentIndexedCache<HashCode, HashCode> signatureCache;
        private final ApiClassExtractor apiClassExtractor = new ApiClassExtractor(Collections.<String>emptySet());
        private static final HashCode IGNORED = Hashing.md5().hashString("Ignored ABI", UTF_8);

        CompileClasspathEntrySnapshotter(PersistentIndexedCache<HashCode, HashCode> signatureCache) {
            this.signatureCache = signatureCache;
        }

        @Override
        protected void snapshotTree(SnapshottableResourceTree snapshottable, SnapshotCollector collector) {
            throw new UnsupportedOperationException("Trees cannot be classpath entries");
        }

        @Override
        protected void snapshotResource(SnapshottableResource resource, SnapshotCollector collector) {
            if (resource instanceof SnapshottableReadableResource && resource.getName().endsWith(".class")) {
                if (resource instanceof FileSnapshot) {
                    HashCode hashCode = resource.getContent().getContentMd5();
                    HashCode signatureHash = signatureCache.get(hashCode);
                    if (signatureHash != null) {
                        if (signatureHash != IGNORED) {
                            collector.recordSnapshot(resource, signatureHash);
                        }
                        return;
                    }
                }
                hashClassSignature((SnapshottableReadableResource) resource, collector);
            }
        }

        private void hashClassSignature(SnapshottableReadableResource resource, SnapshotCollector collector) {
            // Use the ABI as the hash
            InputStream inputStream = null;
            try {
                inputStream = resource.read();
                hashApi(resource, ByteStreams.toByteArray(inputStream), collector);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } finally {
                IOUtils.closeQuietly(inputStream);
            }
        }

        private void hashApi(SnapshottableResource resource, byte[] classBytes, SnapshotCollector collector) {
            try {
                Java9ClassReader reader = new Java9ClassReader(classBytes);
                if (apiClassExtractor.shouldExtractApiClassFrom(reader)) {
                    byte[] signature = apiClassExtractor.extractApiClassFrom(reader);
                    if (signature != null) {
                        HashCode signatureHash = Hashing.md5().hashBytes(signature);
                        collector.recordSnapshot(resource, signatureHash);
                        putToCache(resource, signatureHash);
                    }
                    return;
                }
                putToCache(resource, IGNORED);
            } catch (Exception e) {
                HashCode contentsHash = Hashing.md5().hashBytes(classBytes);
                collector.recordSnapshot(resource, contentsHash);
                putToCache(resource, contentsHash);
                DeprecationLogger.nagUserWith("Malformed class file [" + resource.getName() + "] found on compile classpath, which means that this class will cause a compile error if referenced in a source file. Gradle 5.0 will no longer allow malformed classes on compile classpath.");
            }
        }

        private void putToCache(SnapshottableResource resource, HashCode signatureHash) {
            if (resource instanceof FileSnapshot) {
                HashCode hashCode = resource.getContent().getContentMd5();
                signatureCache.put(hashCode, signatureHash);
            }
        }
    }
}
