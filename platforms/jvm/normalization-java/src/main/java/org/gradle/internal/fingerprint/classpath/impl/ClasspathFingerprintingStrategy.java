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
import org.gradle.api.internal.changedetection.state.DefaultRegularFileSnapshotContext;
import org.gradle.api.internal.changedetection.state.IgnoringResourceHasher;
import org.gradle.api.internal.changedetection.state.LineEndingNormalizingResourceHasher;
import org.gradle.api.internal.changedetection.state.MetaInfAwareClasspathResourceHasher;
import org.gradle.api.internal.changedetection.state.PropertiesFileAwareClasspathResourceHasher;
import org.gradle.api.internal.changedetection.state.ResourceEntryFilter;
import org.gradle.api.internal.changedetection.state.ResourceFilter;
import org.gradle.api.internal.changedetection.state.ResourceSnapshotterCacheService;
import org.gradle.api.internal.changedetection.state.RuntimeClasspathResourceHasher;
import org.gradle.api.internal.changedetection.state.ZipHasher;
import org.gradle.internal.RelativePathSupplier;
import org.gradle.internal.file.FileType;
import org.gradle.internal.fingerprint.FileSystemLocationFingerprint;
import org.gradle.internal.fingerprint.FingerprintHashingStrategy;
import org.gradle.internal.fingerprint.LineEndingSensitivity;
import org.gradle.internal.fingerprint.hashing.RegularFileSnapshotContext;
import org.gradle.internal.fingerprint.hashing.ResourceHasher;
import org.gradle.internal.fingerprint.impl.AbstractFingerprintingStrategy;
import org.gradle.internal.fingerprint.impl.DefaultFileSystemLocationFingerprint;
import org.gradle.internal.fingerprint.impl.IgnoredPathFileSystemLocationFingerprint;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot.FileSystemLocationSnapshotVisitor;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.MissingFileSnapshot;
import org.gradle.internal.snapshot.RegularFileSnapshot;
import org.gradle.internal.snapshot.RelativePathTracker;
import org.gradle.internal.snapshot.RelativePathTrackingFileSystemSnapshotHierarchyVisitor;
import org.gradle.internal.snapshot.SnapshotVisitResult;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashSet;
import java.util.Map;

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
    private final ResourceSnapshotterCacheService cacheService;
    private final ResourceHasher classpathResourceHasher;
    private final ZipHasher zipHasher;
    private final Interner<String> stringInterner;
    private final HashCode zipHasherConfigurationHash;

    private ClasspathFingerprintingStrategy(
        String identifier,
        NonJarFingerprintingStrategy nonZipFingerprintingStrategy,
        ResourceHasher classpathResourceHasher,
        ZipHasher zipHasher,
        ResourceSnapshotterCacheService cacheService,
        Interner<String> stringInterner
    ) {
        super(identifier, zipHasher);
        this.nonZipFingerprintingStrategy = nonZipFingerprintingStrategy;
        this.classpathResourceHasher = classpathResourceHasher;
        this.cacheService = cacheService;
        this.stringInterner = stringInterner;
        this.zipHasher = zipHasher;

        Hasher hasher = Hashing.newHasher();
        zipHasher.appendConfigurationToHasher(hasher);
        this.zipHasherConfigurationHash = hasher.hash();
    }

    public static ClasspathFingerprintingStrategy runtimeClasspath(
        ResourceFilter classpathResourceFilter,
        ResourceEntryFilter manifestAttributeResourceEntryFilter,
        Map<String, ResourceEntryFilter> propertiesFileFilters,
        RuntimeClasspathResourceHasher runtimeClasspathResourceHasher,
        ResourceSnapshotterCacheService cacheService,
        Interner<String> stringInterner,
        LineEndingSensitivity lineEndingSensitivity
    ) {
        ResourceHasher resourceHasher = runtimeClasspathResourceHasher(runtimeClasspathResourceHasher, lineEndingSensitivity, propertiesFileFilters, manifestAttributeResourceEntryFilter, classpathResourceFilter);
        ZipHasher zipHasher = new ZipHasher(resourceHasher);
        return new ClasspathFingerprintingStrategy(CLASSPATH_IDENTIFIER, USE_FILE_HASH, resourceHasher, zipHasher, cacheService, stringInterner);
    }

    public static ClasspathFingerprintingStrategy compileClasspath(ResourceHasher classpathResourceHasher, ResourceSnapshotterCacheService cacheService, Interner<String> stringInterner) {
        ZipHasher zipHasher = new ZipHasher(classpathResourceHasher);
        return new ClasspathFingerprintingStrategy(COMPILE_CLASSPATH_IDENTIFIER, IGNORE, classpathResourceHasher, zipHasher, cacheService, stringInterner);
    }

    public static ClasspathFingerprintingStrategy compileClasspathFallbackToRuntimeClasspath(
        ResourceHasher classpathResourceHasher,
        ResourceHasher runtimeClasspathResourceHasher,
        ResourceSnapshotterCacheService cacheService,
        Interner<String> stringInterner,
        ZipHasher.HashingExceptionReporter hashingExceptionReporter
    ) {
        ZipHasher fallbackZipHasher = new ZipHasher(runtimeClasspathResourceHasher);
        ZipHasher zipHasher = new ZipHasher(classpathResourceHasher, fallbackZipHasher, hashingExceptionReporter);
        return new ClasspathFingerprintingStrategy(COMPILE_CLASSPATH_IDENTIFIER, IGNORE, classpathResourceHasher, zipHasher, cacheService, stringInterner);
    }

    public static ResourceHasher runtimeClasspathResourceHasher(
        RuntimeClasspathResourceHasher runtimeClasspathResourceHasher,
        LineEndingSensitivity lineEndingSensitivity,
        Map<String, ResourceEntryFilter> propertiesFileFilters,
        ResourceEntryFilter manifestAttributeResourceEntryFilter,
        ResourceFilter classpathResourceFilter
    ) {
        ResourceHasher resourceHasher = LineEndingNormalizingResourceHasher.wrap(runtimeClasspathResourceHasher, lineEndingSensitivity);
        resourceHasher = propertiesFileHasher(resourceHasher, propertiesFileFilters);
        resourceHasher = metaInfAwareClasspathResourceHasher(resourceHasher, manifestAttributeResourceEntryFilter);
        return ignoringResourceHasher(resourceHasher, classpathResourceFilter);
    }

    private static ResourceHasher ignoringResourceHasher(ResourceHasher delegate, ResourceFilter resourceFilter) {
        return new IgnoringResourceHasher(delegate, resourceFilter);
    }

    private static ResourceHasher propertiesFileHasher(ResourceHasher delegate, Map<String, ResourceEntryFilter> propertiesFileFilters) {
        return new PropertiesFileAwareClasspathResourceHasher(delegate, propertiesFileFilters);
    }

    private static ResourceHasher metaInfAwareClasspathResourceHasher(ResourceHasher delegate, ResourceEntryFilter manifestAttributeResourceEntryFilter) {
        return new MetaInfAwareClasspathResourceHasher(delegate, manifestAttributeResourceEntryFilter);
    }

    @Override
    public Map<String, FileSystemLocationFingerprint> collectFingerprints(FileSystemSnapshot roots) {
        ImmutableMap.Builder<String, FileSystemLocationFingerprint> builder = ImmutableMap.builder();
        HashSet<String> processedEntries = new HashSet<>();
        roots.accept(new RelativePathTracker(), new ClasspathFingerprintingVisitor(processedEntries, builder));
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

    private class ClasspathFingerprintingVisitor implements RelativePathTrackingFileSystemSnapshotHierarchyVisitor {
        private final HashSet<String> processedEntries;
        private final ImmutableMap.Builder<String, FileSystemLocationFingerprint> builder;


        public ClasspathFingerprintingVisitor(HashSet<String> processedEntries, ImmutableMap.Builder<String, FileSystemLocationFingerprint> builder) {
            this.processedEntries = processedEntries;
            this.builder = builder;
        }

        @Override
        public SnapshotVisitResult visitEntry(FileSystemLocationSnapshot snapshot, RelativePathSupplier relativePath) {
            snapshot.accept(new FileSystemLocationSnapshotVisitor() {
                @Override
                public void visitRegularFile(RegularFileSnapshot fileSnapshot) {
                    HashCode normalizedContentHash = hashContent(fileSnapshot, relativePath);
                    if (normalizedContentHash == null) {
                        return;
                    }

                    String absolutePath = snapshot.getAbsolutePath();
                    if (!processedEntries.add(absolutePath)) {
                        return;
                    }

                    FileSystemLocationFingerprint fingerprint;
                    if (relativePath.isRoot()) {
                        fingerprint = IgnoredPathFileSystemLocationFingerprint.create(snapshot.getType(), normalizedContentHash);
                    } else {
                        String internedRelativePath = stringInterner.intern(relativePath.toRelativePath());
                        fingerprint = new DefaultFileSystemLocationFingerprint(internedRelativePath, FileType.RegularFile, normalizedContentHash);
                    }
                    builder.put(absolutePath, fingerprint);
                }

                @Override
                public void visitMissing(MissingFileSnapshot missingSnapshot) {
                    if (!relativePath.isRoot()) {
                        throw new RuntimeException(String.format("Couldn't read file content: '%s'.", missingSnapshot.getAbsolutePath()));
                    }
                }
            });
            return SnapshotVisitResult.CONTINUE;
        }

        /**
         * Returns either the normalized content hash of the given regular file,
         * or {@code null} if a resource filter has filtered the file out.
         */
        @Nullable
        private HashCode hashContent(RegularFileSnapshot fileSnapshot, RelativePathSupplier relativePath) {
            RegularFileSnapshotContext fileSnapshotContext = new DefaultRegularFileSnapshotContext(() -> Iterables.toArray(relativePath.getSegments(), String.class), fileSnapshot);
            try {
                if (ZipHasher.isZipFile(fileSnapshotContext.getSnapshot().getName())) {
                    return cacheService.hashFile(fileSnapshotContext, zipHasher, zipHasherConfigurationHash);
                } else if (relativePath.isRoot()) {
                    return nonZipFingerprintingStrategy.determineNonJarFingerprint(fileSnapshot.getHash());
                } else {
                    return classpathResourceHasher.hash(fileSnapshotContext);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(failedToNormalize(fileSnapshot), e);
            } catch (UncheckedIOException e) {
                throw new UncheckedIOException(failedToNormalize(fileSnapshot), e.getCause());
            }
        }

        private String failedToNormalize(RegularFileSnapshot snapshot) {
            return String.format("Failed to normalize content of '%s'.", snapshot.getAbsolutePath());
        }
    }

    @Override
    public FingerprintHashingStrategy getHashingStrategy() {
        return FingerprintHashingStrategy.KEEP_ORDER;
    }
}
