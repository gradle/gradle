/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.fingerprint.classpath.impl;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Interner;
import com.google.common.collect.Iterables;
import org.gradle.api.internal.changedetection.state.ManifestFileZipEntryHasher;
import org.gradle.api.internal.changedetection.state.PropertiesFileZipEntryHasher;
import org.gradle.api.internal.changedetection.state.ResourceEntryFilter;
import org.gradle.api.internal.changedetection.state.ResourceFilter;
import org.gradle.api.internal.changedetection.state.ResourceHasher;
import org.gradle.api.internal.changedetection.state.ResourceSnapshotterCacheService;
import org.gradle.api.internal.changedetection.state.RuntimeClasspathResourceHasher;
import org.gradle.api.internal.changedetection.state.MetaInfAwareClasspathResourceHasher;
import org.gradle.api.internal.changedetection.state.ZipHasher;
import org.gradle.internal.file.FileType;
import org.gradle.internal.fingerprint.FileSystemLocationFingerprint;
import org.gradle.internal.fingerprint.FingerprintHashingStrategy;
import org.gradle.internal.fingerprint.impl.AbstractFingerprintingStrategy;
import org.gradle.internal.fingerprint.impl.DefaultFileSystemLocationFingerprint;
import org.gradle.internal.fingerprint.impl.IgnoredPathFileSystemLocationFingerprint;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.snapshot.CompleteDirectorySnapshot;
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshotVisitor;
import org.gradle.internal.snapshot.RegularFileSnapshot;
import org.gradle.internal.snapshot.RelativePathSegmentsTracker;
import org.gradle.internal.snapshot.RelativePathStringTracker;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Map;
import java.util.function.Supplier;

import static org.gradle.internal.fingerprint.classpath.impl.ClasspathFingerprintingStrategy.NonJarFingerprintingStrategy.IGNORE;
import static org.gradle.internal.fingerprint.classpath.impl.ClasspathFingerprintingStrategy.NonJarFingerprintingStrategy.USE_FILE_HASH;

/**
 * Fingerprints classpath-like file collections.
 *
 * <p>
 * This strategy uses a {@link ResourceHasher} to normalize the contents of files and a {@link ResourceFilter} to ignore resources in classpath entries. Zip files are treated as if the contents would be expanded on disk.
 * </p>
 *
 * <p>
 * The order of the entries in the classpath matters, paths do not matter for the entries.
 * For the resources in each classpath entry, normalization takes the relative path of the resource and possibly normalizes its contents.
 * </p>
 */
public class ClasspathFingerprintingStrategy extends AbstractFingerprintingStrategy {

    private final NonJarFingerprintingStrategy nonZipFingerprintingStrategy;
    private final ResourceFilter classpathResourceFilter;
    private final ResourceSnapshotterCacheService cacheService;
    private final ResourceHasher classpathResourceHasher;
    private final ZipHasher zipHasher;
    private final Interner<String> stringInterner;
    private final HashCode zipHasherConfigurationHash;

    private ClasspathFingerprintingStrategy(String identifier, NonJarFingerprintingStrategy nonZipFingerprintingStrategy, ResourceHasher classpathResourceHasher, ResourceFilter classpathResourceFilter, ResourceSnapshotterCacheService cacheService, Interner<String> stringInterner) {
        super(identifier);
        this.nonZipFingerprintingStrategy = nonZipFingerprintingStrategy;
        this.classpathResourceFilter = classpathResourceFilter;
        this.classpathResourceHasher = classpathResourceHasher;
        this.cacheService = cacheService;
        this.stringInterner = stringInterner;
        this.zipHasher = new ZipHasher(classpathResourceHasher, classpathResourceFilter);

        Hasher hasher = Hashing.newHasher();
        zipHasher.appendConfigurationToHasher(hasher);
        this.zipHasherConfigurationHash = hasher.hash();
    }

    public static ClasspathFingerprintingStrategy runtimeClasspath(ResourceFilter classpathResourceFilter, ResourceEntryFilter manifestAttributeResourceEntryFilter, ResourceEntryFilter manifestPropertyResourceEntryFilter, RuntimeClasspathResourceHasher runtimeClasspathResourceHasher, ResourceSnapshotterCacheService cacheService, Interner<String> stringInterner) {
        return new ClasspathFingerprintingStrategy("CLASSPATH", USE_FILE_HASH, metaInfAwareClasspathResourceHasher(runtimeClasspathResourceHasher, manifestAttributeResourceEntryFilter, manifestPropertyResourceEntryFilter), classpathResourceFilter, cacheService, stringInterner);
    }

    public static ClasspathFingerprintingStrategy compileClasspath(ResourceHasher classpathResourceHasher, ResourceSnapshotterCacheService cacheService, Interner<String> stringInterner) {
        return new ClasspathFingerprintingStrategy("COMPILE_CLASSPATH", IGNORE, classpathResourceHasher, ResourceFilter.FILTER_NOTHING, cacheService, stringInterner);
    }

    private static ResourceHasher metaInfAwareClasspathResourceHasher(ResourceHasher resourceHasher, ResourceEntryFilter manifestAttributeResourceEntryFilter, ResourceEntryFilter manifestPropertyResourceEntryFilter) {
        return new MetaInfAwareClasspathResourceHasher(
            resourceHasher,
            new ManifestFileZipEntryHasher(manifestAttributeResourceEntryFilter),
            new PropertiesFileZipEntryHasher(manifestPropertyResourceEntryFilter)
        );
    }

    @Override
    public String normalizePath(CompleteFileSystemLocationSnapshot snapshot) {
        return "";
    }

    @Override
    public Map<String, FileSystemLocationFingerprint> collectFingerprints(Iterable<? extends FileSystemSnapshot> roots) {
        ImmutableMap.Builder<String, FileSystemLocationFingerprint> builder = ImmutableMap.builder();
        HashSet<String> processedEntries = new HashSet<String>();
        for (FileSystemSnapshot root : roots) {
            ClasspathFingerprintVisitor fingerprintVisitor = new ClasspathFingerprintVisitor(processedEntries, builder);
            root.accept(new ClasspathContentFingerprintingVisitor(fingerprintVisitor));
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

    private class ClasspathContentFingerprintingVisitor implements FileSystemSnapshotVisitor {
        private final ClasspathFingerprintVisitor delegate;
        private final RelativePathSegmentsTracker relativePathSegmentsTracker = new RelativePathSegmentsTracker();
        private final Supplier<String[]> relativePathFactory = () ->
            Iterables.toArray(relativePathSegmentsTracker.getRelativePath(), String.class);

        public ClasspathContentFingerprintingVisitor(ClasspathFingerprintVisitor delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean preVisitDirectory(CompleteDirectorySnapshot directorySnapshot) {
            relativePathSegmentsTracker.enter(directorySnapshot);
            return delegate.preVisitDirectory(directorySnapshot);
        }

        @Override
        public void visitFile(CompleteFileSystemLocationSnapshot fileSnapshot) {
            if (fileSnapshot instanceof RegularFileSnapshot) {
                HashCode normalizedContent = fingerprintFile((RegularFileSnapshot) fileSnapshot);
                if (normalizedContent != null) {
                    delegate.visit(fileSnapshot, normalizedContent);
                }
            } else if (!relativePathSegmentsTracker.isRoot()) {
                throw new RuntimeException(String.format("Couldn't read file content: '%s'.", fileSnapshot.getAbsolutePath()));
            }
        }

        @Nullable
        private HashCode fingerprintFile(RegularFileSnapshot fileSnapshot) {
            return relativePathSegmentsTracker.isRoot() ? fingerprintRootFile(fileSnapshot) : fingerprintTreeFile(fileSnapshot);
        }

        @Nullable
        private HashCode fingerprintTreeFile(RegularFileSnapshot fileSnapshot) {
            relativePathSegmentsTracker.enter(fileSnapshot);
            boolean shouldBeIgnored = classpathResourceFilter.shouldBeIgnored(relativePathFactory);
            relativePathSegmentsTracker.leave();
            if (shouldBeIgnored) {
                return null;
            }
            if (ZipHasher.isZipFile(fileSnapshot.getName())) {
                return fingerprintZipContents(fileSnapshot);
            } else {
                return classpathResourceHasher.hash(fileSnapshot);
            }
        }

        @Override
        public void postVisitDirectory(CompleteDirectorySnapshot directorySnapshot) {
            relativePathSegmentsTracker.leave();
            delegate.postVisitDirectory();
        }
    }

    @Nullable
    private HashCode fingerprintRootFile(RegularFileSnapshot fileSnapshot) {
        if (ZipHasher.isZipFile(fileSnapshot.getName())) {
            return fingerprintZipContents(fileSnapshot);
        }
        return nonZipFingerprintingStrategy.determineNonJarFingerprint(fileSnapshot.getHash());
    }

    @Nullable
    private HashCode fingerprintZipContents(RegularFileSnapshot fileSnapshot) {
        return cacheService.hashFile(fileSnapshot, zipHasher, zipHasherConfigurationHash);
    }

    private class ClasspathFingerprintVisitor {
        private final RelativePathStringTracker relativePathStringTracker;
        private final HashSet<String> processedEntries;
        private final ImmutableMap.Builder<String, FileSystemLocationFingerprint> builder;

        public ClasspathFingerprintVisitor(HashSet<String> processedEntries, ImmutableMap.Builder<String, FileSystemLocationFingerprint> builder) {
            this.processedEntries = processedEntries;
            this.builder = builder;
            this.relativePathStringTracker = new RelativePathStringTracker();
        }

        public boolean preVisitDirectory(CompleteFileSystemLocationSnapshot directorySnapshot) {
            relativePathStringTracker.enter(directorySnapshot);
            return true;
        }

        public void visit(CompleteFileSystemLocationSnapshot fileSnapshot, HashCode normalizedContentHash) {
            String absolutePath = fileSnapshot.getAbsolutePath();
            if (processedEntries.add(absolutePath)) {
                FileSystemLocationFingerprint fingerprint = relativePathStringTracker.isRoot() ? IgnoredPathFileSystemLocationFingerprint.create(fileSnapshot.getType(), normalizedContentHash) : createFileFingerprint(fileSnapshot, normalizedContentHash);
                builder.put(
                    absolutePath,
                    fingerprint
                );
            }
        }

        private FileSystemLocationFingerprint createFileFingerprint(CompleteFileSystemLocationSnapshot snapshot, HashCode content) {
            relativePathStringTracker.enter(snapshot);
            FileSystemLocationFingerprint fingerprint = new DefaultFileSystemLocationFingerprint(stringInterner.intern(relativePathStringTracker.getRelativePathString()), FileType.RegularFile, content);
            relativePathStringTracker.leave();
            return fingerprint;
        }

        public void postVisitDirectory() {
            relativePathStringTracker.leave();
        }
    }

    @Override
    public FingerprintHashingStrategy getHashingStrategy() {
        return FingerprintHashingStrategy.KEEP_ORDER;
    }
}
