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

package org.gradle.internal.fingerprint.impl;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.state.DefaultNormalizedFileSnapshot;
import org.gradle.api.internal.changedetection.state.JarHasher;
import org.gradle.api.internal.changedetection.state.ResourceFilter;
import org.gradle.api.internal.changedetection.state.ResourceHasher;
import org.gradle.api.internal.changedetection.state.ResourceSnapshotterCacheService;
import org.gradle.api.internal.changedetection.state.RuntimeClasspathResourceHasher;
import org.gradle.api.internal.changedetection.state.mirror.FileSystemSnapshot;
import org.gradle.api.internal.changedetection.state.mirror.PhysicalDirectorySnapshot;
import org.gradle.api.internal.changedetection.state.mirror.PhysicalFileSnapshot;
import org.gradle.api.internal.changedetection.state.mirror.PhysicalSnapshot;
import org.gradle.api.internal.changedetection.state.mirror.PhysicalSnapshotVisitor;
import org.gradle.api.internal.changedetection.state.mirror.RelativePathSegmentsTracker;
import org.gradle.api.internal.changedetection.state.mirror.RelativePathStringTracker;
import org.gradle.caching.internal.DefaultBuildCacheHasher;
import org.gradle.internal.Factory;
import org.gradle.internal.FileUtils;
import org.gradle.internal.file.FileType;
import org.gradle.internal.fingerprint.FingerprintingStrategy;
import org.gradle.internal.fingerprint.NormalizedFileSnapshot;
import org.gradle.internal.hash.HashCode;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Map;

import static org.gradle.internal.fingerprint.impl.ClasspathFingerprintingStrategy.NonJarFingerprintingStrategy.IGNORE;
import static org.gradle.internal.fingerprint.impl.ClasspathFingerprintingStrategy.NonJarFingerprintingStrategy.USE_FILE_HASH;

/**
 * Fingerprints classpath-like {@link org.gradle.api.file.FileCollection}s.
 *
 * <p>
 * This strategy uses a {@link ResourceHasher} to normalize the contents of files and a {@link ResourceFilter} to ignore resources in classpath entries. {@code .jar} files are treated as if the contents would be expanded on disk.
 * </p>
 *
 * <p>
 * The order of the entries in the classpath matters, paths do not matter for the entries.
 * For the resources in each classpath entry, normalization takes the relative path of the resource and possibly normalizes its contents.
 * </p>
 */
public class ClasspathFingerprintingStrategy implements FingerprintingStrategy {

    private final NonJarFingerprintingStrategy nonJarFingerprintingStrategy;
    private final ResourceFilter classpathResourceFilter;
    private final ResourceSnapshotterCacheService cacheService;
    private final ResourceHasher classpathResourceHasher;
    private final JarHasher jarHasher;
    private final StringInterner stringInterner;
    private final HashCode jarHasherConfigurationHash;
    private final FingerprintingStrategy.Identifier identifier;

    private ClasspathFingerprintingStrategy(Identifier identifier, NonJarFingerprintingStrategy nonJarFingerprintingStrategy, ResourceHasher classpathResourceHasher, ResourceFilter classpathResourceFilter, ResourceSnapshotterCacheService cacheService, StringInterner stringInterner) {
        this.identifier = identifier;
        this.nonJarFingerprintingStrategy = nonJarFingerprintingStrategy;
        this.classpathResourceFilter = classpathResourceFilter;
        this.classpathResourceHasher = classpathResourceHasher;
        this.cacheService = cacheService;
        this.stringInterner = stringInterner;
        this.jarHasher = new JarHasher(classpathResourceHasher, classpathResourceFilter);
        DefaultBuildCacheHasher hasher = new DefaultBuildCacheHasher();
        jarHasher.appendConfigurationToHasher(hasher);
        this.jarHasherConfigurationHash = hasher.hash();
    }

    public static ClasspathFingerprintingStrategy runtimeClasspath(ResourceFilter classpathResourceFilter, RuntimeClasspathResourceHasher runtimeClasspathResourceHasher, ResourceSnapshotterCacheService cacheService, StringInterner stringInterner) {
        return new ClasspathFingerprintingStrategy(Identifier.CLASSPATH, USE_FILE_HASH, runtimeClasspathResourceHasher, classpathResourceFilter, cacheService, stringInterner);
    }

    public static ClasspathFingerprintingStrategy compileClasspath(ResourceHasher classpathResourceHasher, ResourceSnapshotterCacheService cacheService, StringInterner stringInterner) {
        return new ClasspathFingerprintingStrategy(Identifier.COMPILE_CLASSPATH, IGNORE, classpathResourceHasher, ResourceFilter.FILTER_NOTHING, cacheService, stringInterner);
    }

    @Override
    public Map<String, NormalizedFileSnapshot> collectSnapshots(Iterable<FileSystemSnapshot> roots) {
        ImmutableMap.Builder<String, NormalizedFileSnapshot> builder = ImmutableMap.builder();
        HashSet<String> processedEntries = new HashSet<String>();
        for (FileSystemSnapshot root : roots) {
            ClasspathSnapshotVisitor snapshotVisitor = new ClasspathSnapshotVisitor(processedEntries, builder);
            root.accept(new ClasspathContentSnapshottingVisitor(snapshotVisitor));
        }
        return builder.build();
    }

    public enum NonJarFingerprintingStrategy {
        IGNORE {
            @Nullable
            @Override
            public HashCode determineNonJarFingerprint(HashCode original) {
                return null;
            }
        },
        USE_FILE_HASH {
            @Override
            public HashCode determineNonJarFingerprint(HashCode original) {
                return original;
            }
        };

        @Nullable
        public abstract HashCode determineNonJarFingerprint(HashCode original);
    }

    private class ClasspathContentSnapshottingVisitor implements PhysicalSnapshotVisitor {
        private final ClasspathSnapshotVisitor delegate;
        private final RelativePathSegmentsTracker relativePathSegmentsTracker = new RelativePathSegmentsTracker();
        private final Factory<String[]> relativePathFactory = new Factory<String[]>() {
            @Override
            public String[] create() {
                return Iterables.toArray(relativePathSegmentsTracker.getRelativePath(), String.class);
            }
        };

        public ClasspathContentSnapshottingVisitor(ClasspathSnapshotVisitor delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean preVisitDirectory(PhysicalDirectorySnapshot directorySnapshot) {
            relativePathSegmentsTracker.enter(directorySnapshot);
            return delegate.preVisitDirectory(directorySnapshot);
        }

        @Override
        public void visit(PhysicalSnapshot fileSnapshot) {
            if (fileSnapshot.getType() == FileType.RegularFile) {
                HashCode normalizedContent = fingerprintFile((PhysicalFileSnapshot) fileSnapshot);
                if (normalizedContent != null) {
                    delegate.visit(fileSnapshot, normalizedContent);
                }
            }
        }

        @Nullable
        private HashCode fingerprintFile(PhysicalFileSnapshot fileSnapshot) {
            return relativePathSegmentsTracker.isRoot() ? fingerprintRootFile(fileSnapshot) : fingerprintTreeFile(fileSnapshot);
        }

        @Nullable
        private HashCode fingerprintTreeFile(PhysicalFileSnapshot fileSnapshot) {
            relativePathSegmentsTracker.enter(fileSnapshot);
            boolean shouldBeIgnored = classpathResourceFilter.shouldBeIgnored(relativePathFactory);
            relativePathSegmentsTracker.leave();
            if (shouldBeIgnored) {
                return null;
            }
            return classpathResourceHasher.hash(fileSnapshot);
        }

        @Override
        public void postVisitDirectory(PhysicalDirectorySnapshot directorySnapshot) {
            relativePathSegmentsTracker.leave();
            delegate.postVisitDirectory();
        }
    }


    @Nullable
    private HashCode fingerprintRootFile(PhysicalFileSnapshot fileSnapshot) {
        if (FileUtils.hasExtensionIgnoresCase(fileSnapshot.getName(), ".jar")) {
            return snapshotJarContents(fileSnapshot);
        }
        return nonJarFingerprintingStrategy.determineNonJarFingerprint(fileSnapshot.getHash());
    }

    @Nullable
    private HashCode snapshotJarContents(PhysicalFileSnapshot fileSnapshot) {
        return cacheService.hashFile(fileSnapshot, jarHasher, jarHasherConfigurationHash);
    }

    @Override
    public FingerprintCompareStrategy getCompareStrategy() {
        return FingerprintCompareStrategy.CLASSPATH;
    }

    @Override
    public Identifier getIdentifier() {
        return identifier;
    }

    private class ClasspathSnapshotVisitor {
        private final RelativePathStringTracker relativePathStringTracker;
        private final HashSet<String> processedEntries;
        private final ImmutableMap.Builder<String, NormalizedFileSnapshot> builder;

        public ClasspathSnapshotVisitor(HashSet<String> processedEntries, ImmutableMap.Builder<String, NormalizedFileSnapshot> builder) {
            this.processedEntries = processedEntries;
            this.builder = builder;
            this.relativePathStringTracker = new RelativePathStringTracker();
        }

        public boolean preVisitDirectory(PhysicalSnapshot directorySnapshot) {
            relativePathStringTracker.enter(directorySnapshot);
            return true;
        }

        public void visit(PhysicalSnapshot fileSnapshot, HashCode normalizedContentHash) {
            String absolutePath = fileSnapshot.getAbsolutePath();
            if (processedEntries.add(absolutePath)) {
                NormalizedFileSnapshot normalizedFileSnapshot = relativePathStringTracker.isRoot() ? IgnoredPathFingerprint.create(fileSnapshot.getType(), normalizedContentHash) : createNormalizedSnapshot(fileSnapshot, normalizedContentHash);
                builder.put(
                    absolutePath,
                    normalizedFileSnapshot);
            }
        }

        private NormalizedFileSnapshot createNormalizedSnapshot(PhysicalSnapshot snapshot, HashCode content) {
            relativePathStringTracker.enter(snapshot);
            NormalizedFileSnapshot normalizedFileSnapshot = new DefaultNormalizedFileSnapshot(stringInterner.intern(relativePathStringTracker.getRelativePathString()), FileType.RegularFile, content);
            relativePathStringTracker.leave();
            return normalizedFileSnapshot;
        }

        public void postVisitDirectory() {
            relativePathStringTracker.leave();
        }
    }
}
