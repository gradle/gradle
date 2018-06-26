/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.changedetection.state.mirror.logical;

import com.google.common.collect.ListMultimap;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.state.ClasspathEntrySnapshotBuilder;
import org.gradle.api.internal.changedetection.state.ConfigurableNormalizer;
import org.gradle.api.internal.changedetection.state.DirectoryFileSnapshot;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshot;
import org.gradle.api.internal.changedetection.state.FileContentSnapshot;
import org.gradle.api.internal.changedetection.state.FileHashSnapshot;
import org.gradle.api.internal.changedetection.state.MissingFileSnapshot;
import org.gradle.api.internal.changedetection.state.RegularFileHasher;
import org.gradle.api.internal.changedetection.state.RegularFileSnapshot;
import org.gradle.api.internal.changedetection.state.ResourceHasher;
import org.gradle.api.internal.changedetection.state.ResourceSnapshotterCacheService;
import org.gradle.api.internal.changedetection.state.ZipTree;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.caching.internal.BuildCacheHasher;
import org.gradle.caching.internal.DefaultBuildCacheHasher;
import org.gradle.internal.FileUtils;
import org.gradle.internal.hash.HashCode;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Deque;

public abstract class AbstractClasspathRootFileCollectionSnapshotBuilder extends RootFileCollectionSnapshotBuilder {
    private static final Logger LOGGER = Logging.getLogger(AbstractClasspathRootFileCollectionSnapshotBuilder.class);

    private final ResourceSnapshotterCacheService cacheService;
    private final ResourceHasher classpathResourceHasher;
    private final StringInterner stringInterner;
    private final JarHasher jarHasher;
    private final HashCode jarHasherConfigurationHash;

    public AbstractClasspathRootFileCollectionSnapshotBuilder(ResourceHasher classpathResourceHasher, ResourceSnapshotterCacheService cacheService, StringInterner stringInterner) {
        this.cacheService = cacheService;
        this.classpathResourceHasher = classpathResourceHasher;
        this.stringInterner = stringInterner;
        this.jarHasher = new JarHasher();
        DefaultBuildCacheHasher hasher = new DefaultBuildCacheHasher();
        jarHasher.appendConfigurationToHasher(hasher);
        this.jarHasherConfigurationHash = hasher.hash();
    }

    @Override
    protected FileCollectionSnapshot build(ListMultimap<String, LogicalSnapshot> roots) {
        return new ClasspathSnapshot(roots);
    }

    @Nullable
    @Override
    public FileContentSnapshot snapshotFileContents(Path path, Deque<String> relativePath, FileContentSnapshot contentSnapshot) {
        HashCode hashCode = classpathResourceHasher.hash(path, relativePath, contentSnapshot);
        return hashCode == null ? null : new FileHashSnapshot(hashCode);
    }

    @Override
    public void visitFileSnapshot(RegularFileSnapshot file) {
        FileContentSnapshot newContentSnapshot = snapshotRootFile(file);
        if (newContentSnapshot != null) {
            addRoot(file.getPath(), file.getName(), newContentSnapshot);
        }
    }

    @Nullable
    private FileContentSnapshot snapshotRootFile(RegularFileSnapshot file) {
        if (FileUtils.hasExtensionIgnoresCase(file.getName(), ".jar")) {
            Path path = Paths.get(file.getPath());
            return snapshotJarContents(path, file.getRelativePath(), file.getContent());
        }
        return snapshotNonJarContents(file.getContent());
    }

    @Nullable
    protected abstract FileContentSnapshot snapshotNonJarContents(FileContentSnapshot contentSnapshot);

    @Nullable
    private FileContentSnapshot snapshotJarContents(Path path, Iterable<String> relativePath, FileContentSnapshot contentSnapshot) {
        HashCode hash = cacheService.hashFile(path, relativePath, contentSnapshot, jarHasher, jarHasherConfigurationHash);
        return hash == null ? null : new FileHashSnapshot(hash);
    }

    private class JarHasher implements RegularFileHasher, ConfigurableNormalizer {
        @Nullable
        @Override
        public HashCode hash(Path path, Iterable<String> relativePath, FileContentSnapshot content) {
            return hashJarContents(path, content);
        }

        @Override
        public void appendConfigurationToHasher(BuildCacheHasher hasher) {
            hasher.putString(getClass().getName());
            classpathResourceHasher.appendConfigurationToHasher(hasher);
        }

        private HashCode hashJarContents(Path jarFile, FileContentSnapshot content) {
            try {
                ClasspathEntrySnapshotBuilder classpathEntrySnapshotBuilder = newClasspathEntrySnapshotBuilder();
                new ZipTree(jarFile).visit(classpathEntrySnapshotBuilder);
                return classpathEntrySnapshotBuilder.getHash();
            } catch (Exception e) {
                return hashMalformedZip(jarFile, content, e);
            }
        }

        private HashCode hashMalformedZip(Path path, FileContentSnapshot content, Exception e) {
            LOGGER.debug("Malformed jar '{}' found on classpath. Falling back to full content hash instead of classpath hashing.", path.getFileName(), e);
            return content.getContentMd5();
        }
    }

    private ClasspathEntrySnapshotBuilder newClasspathEntrySnapshotBuilder() {
        return new ClasspathEntrySnapshotBuilder(classpathResourceHasher, stringInterner);
    }

    @Override
    public void visitDirectorySnapshot(DirectoryFileSnapshot directory) {
    }

    @Override
    public void visitMissingFileSnapshot(MissingFileSnapshot missingFile) {
    }
}
