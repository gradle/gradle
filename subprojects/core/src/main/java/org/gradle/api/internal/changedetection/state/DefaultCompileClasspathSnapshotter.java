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
import org.gradle.api.internal.changedetection.resources.CachingResourceSnapshotter;
import org.gradle.api.internal.changedetection.resources.ClasspathResourceSnapshotter;
import org.gradle.api.internal.changedetection.resources.ResourceSnapshotter;
import org.gradle.api.internal.changedetection.resources.SnapshotCollector;
import org.gradle.api.internal.changedetection.resources.SnapshottableResource;
import org.gradle.api.internal.tasks.compile.ApiClassExtractor;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.internal.nativeintegration.filesystem.FileType;
import org.gradle.util.DeprecationLogger;
import org.gradle.util.internal.Java9ClassReader;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;

public class DefaultCompileClasspathSnapshotter extends AbstractFileCollectionSnapshotter implements CompileClasspathSnapshotter {
    private final StringInterner stringInterner;
    private final PersistentIndexedCache<HashCode, HashCode> signatureCache;

    public DefaultCompileClasspathSnapshotter(FileSnapshotTreeFactory fileSnapshotTreeFactory, StringInterner stringInterner, PersistentIndexedCache<HashCode, HashCode> signatureCache) {
        super(fileSnapshotTreeFactory, stringInterner);
        this.stringInterner = stringInterner;
        this.signatureCache = signatureCache;
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
        return new CachingResourceSnapshotter(
            new ClasspathResourceSnapshotter(new CompileClasspathEntrySnapshotter(), stringInterner),
            signatureCache
        );
    }

    private static class CompileClasspathEntrySnapshotter implements ResourceSnapshotter {
        private ApiClassExtractor apiClassExtractor = new ApiClassExtractor(Collections.<String>emptySet());

        @Override
        public void snapshot(SnapshotTree details, SnapshotCollector collector) {
            SnapshottableResource root = details.getRoot();
            if (root != null && root.getType() == FileType.RegularFile && root.getName().endsWith(".class")) {
                hashClassSignature(root, collector);
            }
        }

        private void hashClassSignature(SnapshottableResource resource, SnapshotCollector collector) {
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
                ApiClassExtractor extractor = apiClassExtractor;
                Java9ClassReader reader = new Java9ClassReader(classBytes);
                if (extractor.shouldExtractApiClassFrom(reader)) {
                    byte[] signature = extractor.extractApiClassFrom(reader);
                    if (signature != null) {
                        collector.recordSnapshot(resource, Hashing.md5().hashBytes(signature));
                    }
                }
            } catch (Exception e) {
                collector.recordSnapshot(resource, Hashing.md5().hashBytes(classBytes));
                DeprecationLogger.nagUserWith("Malformed class file [" + resource.getName() + "] found on compile classpath, which means that this class will cause a compile error if referenced in a source file. Gradle 5.0 will no longer allow malformed classes on compile classpath.");
            }
        }
    }
}
