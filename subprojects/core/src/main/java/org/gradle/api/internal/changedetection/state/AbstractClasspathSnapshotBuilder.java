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

package org.gradle.api.internal.changedetection.state;

import org.gradle.api.GradleException;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.caching.internal.BuildCacheHasher;
import org.gradle.caching.internal.DefaultBuildCacheHasher;
import org.gradle.internal.FileUtils;
import org.gradle.internal.hash.HashCode;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Collection;

public abstract class AbstractClasspathSnapshotBuilder implements VisitingFileCollectionSnapshotBuilder {
    private static final Logger LOGGER = Logging.getLogger(AbstractClasspathSnapshotBuilder.class);

    protected final CollectingFileCollectionSnapshotBuilder builder;
    private final ResourceHasher classpathResourceHasher;
    private final StringInterner stringInterner;
    private final ResourceSnapshotterCacheService cacheService;
    private final JarHasher jarHasher;
    private final HashCode jarHasherConfigurationHash;

    public AbstractClasspathSnapshotBuilder(ResourceHasher classpathResourceHasher, ResourceSnapshotterCacheService cacheService, StringInterner stringInterner) {
        this.builder = new CollectingFileCollectionSnapshotBuilder(TaskFilePropertyCompareStrategy.ORDERED, InputPathNormalizationStrategy.NONE, stringInterner);
        this.cacheService = cacheService;
        this.stringInterner = stringInterner;
        this.classpathResourceHasher = classpathResourceHasher;
        this.jarHasher = new JarHasher();
        DefaultBuildCacheHasher hasher = new DefaultBuildCacheHasher();
        jarHasher.appendConfigurationToHasher(hasher);
        this.jarHasherConfigurationHash = hasher.hash();
    }

    protected abstract void visitNonJar(RegularFileSnapshot file);

    @Override
    public void visitDirectorySnapshot(DirectoryFileSnapshot directory) {
    }

    @Override
    public void visitMissingFileSnapshot(MissingFileSnapshot missingFile) {
    }

    @Override
    public void visitFileTreeSnapshot(Collection<FileSnapshot> descendants) {
        ClasspathEntrySnapshotBuilder entryResourceCollectionBuilder = newClasspathEntrySnapshotBuilder();
        try {
            new FileTree(descendants).visit(entryResourceCollectionBuilder);
        } catch (IOException e) {
            throw new GradleException("Error while snapshotting directory in classpath", e);
        }
        entryResourceCollectionBuilder.collectNormalizedSnapshots(builder);
    }

    @Override
    public void visitFileSnapshot(RegularFileSnapshot file) {
        if (FileUtils.hasExtensionIgnoresCase(file.getName(), ".jar")) {
            visitJar(file);
        } else {
            visitNonJar(file);
        }
    }

    private void visitJar(RegularFileSnapshot jarFile) {
        HashCode hash = cacheService.hashFile(jarFile, jarHasher, jarHasherConfigurationHash);
        if (hash != null) {
            builder.collectFileSnapshot(jarFile.withContentHash(hash));
        }
    }

    private class JarHasher implements RegularFileHasher, ConfigurableNormalizer {
        @Nullable
        @Override
        public HashCode hash(RegularFileSnapshot fileSnapshot) {
            return hashJarContents(fileSnapshot);
        }

        @Override
        public void appendConfigurationToHasher(BuildCacheHasher hasher) {
            hasher.putString(getClass().getName());
            classpathResourceHasher.appendConfigurationToHasher(hasher);
        }

        private HashCode hashJarContents(RegularFileSnapshot jarFile) {
            try {
                ClasspathEntrySnapshotBuilder classpathEntrySnapshotBuilder = newClasspathEntrySnapshotBuilder();
                new ZipTree(jarFile).visit(classpathEntrySnapshotBuilder);
                return classpathEntrySnapshotBuilder.getHash();
            } catch (Exception e) {
                return hashMalformedZip(jarFile, e);
            }
        }

        private HashCode hashMalformedZip(FileSnapshot fileSnapshot, Exception e) {
            LOGGER.debug("Malformed jar '" + fileSnapshot.getName() + "' found on classpath. Falling back to full content hash instead of classpath hashing.", e);
            return fileSnapshot.getContent().getContentMd5();
        }
    }

    private ClasspathEntrySnapshotBuilder newClasspathEntrySnapshotBuilder() {
        return new ClasspathEntrySnapshotBuilder(classpathResourceHasher, stringInterner);
    }

    @Override
    public FileCollectionSnapshot build() {
        return builder.build();
    }
}
