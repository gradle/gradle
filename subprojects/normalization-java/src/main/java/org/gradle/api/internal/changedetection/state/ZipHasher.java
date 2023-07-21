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

package org.gradle.api.internal.changedetection.state;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import org.apache.commons.io.FilenameUtils;
import org.gradle.api.internal.file.archive.ZipEntry;
import org.gradle.api.internal.file.archive.ZipInput;
import org.gradle.api.internal.file.archive.impl.FileZipInput;
import org.gradle.api.internal.file.archive.impl.StreamZipInput;
import org.gradle.internal.file.FileType;
import org.gradle.internal.fingerprint.FileSystemLocationFingerprint;
import org.gradle.internal.fingerprint.FingerprintHashingStrategy;
import org.gradle.internal.fingerprint.hashing.ConfigurableNormalizer;
import org.gradle.internal.fingerprint.hashing.RegularFileSnapshotContext;
import org.gradle.internal.fingerprint.hashing.RegularFileSnapshotContextHasher;
import org.gradle.internal.fingerprint.hashing.ResourceHasher;
import org.gradle.internal.fingerprint.hashing.ZipEntryContext;
import org.gradle.internal.fingerprint.impl.DefaultFileSystemLocationFingerprint;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.io.IoFunction;
import org.gradle.internal.snapshot.RegularFileSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Computes the fingerprint of a ZIP archive.
 */
public class ZipHasher implements RegularFileSnapshotContextHasher, ConfigurableNormalizer {

    /**
     * A visitor that is called when ZipHasher hashes an archive.
     */
    public interface ArchiveVisitor extends ConfigurableNormalizer {
        /**
         * Called when ZipHasher needs to hash an archive.
         * The provided {@code visitAction} walks the archive computing its hash. It must be provided with an EntryVisitor to hash archive entries.
         * The {@code visitAction} returns a {@link Hasher} with intermediate hashing results or {@code null} if the archive contains no hashable entries.
         *
         * @return the hash of the archive
         * @implNote The Hasher returned by the {@code visitAction} can be updated by this method prior to computing the hash of the archive.
         */
        @Nullable
        HashCode visitArchive(IoFunction<EntryVisitor, Hasher> visitAction) throws IOException;
    }

    /**
     * A visitor that is called for each entry of the archive. The order of visited entries is undefined.
     */
    public interface EntryVisitor {
        /**
         * Called to compute the hash of the given zipEntryContext if it should contribute to the archive hash.
         * Returned {@code null} means that this entry should not be included in the calculation of the final hash.
         *
         * @param zipEntryContext the zip entry to process
         * @return the computed hash of the entry or {@code null} if the entry doesn't affect the archive hash code
         */
        @Nullable
        HashCode visitEntry(ZipEntryContext zipEntryContext) throws IOException;
    }

    private static final Set<String> KNOWN_ZIP_EXTENSIONS = ImmutableSet.of("zip", "jar", "war", "rar", "ear", "apk", "aar", "klib");
    private static final Logger LOGGER = LoggerFactory.getLogger(ZipHasher.class);
    private static final HashCode EMPTY_HASH_MARKER = Hashing.signature(ZipHasher.class);

    public static boolean isZipFile(final String name) {
        return KNOWN_ZIP_EXTENSIONS.contains(FilenameUtils.getExtension(name).toLowerCase(Locale.ROOT));
    }

    private final ArchiveVisitor archiveVisitor;
    private final ZipHasher fallbackZipHasher;
    private final HashingExceptionReporter hashingExceptionReporter;

    private ZipHasher(ArchiveVisitor archiveVisitor, @Nullable ZipHasher fallbackZipHasher, HashingExceptionReporter hashingExceptionReporter) {
        this.archiveVisitor = archiveVisitor;
        this.fallbackZipHasher = fallbackZipHasher;
        this.hashingExceptionReporter = hashingExceptionReporter;
    }

    private ZipHasher(ArchiveVisitor archiveVisitor) {
        this(
            archiveVisitor,
            null,
            (s, e) -> LOGGER.debug("Malformed archive '{}'. Falling back to full content hash instead of entry hashing.", s.getName(), e)
        );
    }

    /**
     * Creates a ZipHasher that hashes archives with the given {@code visitor}. Nested archives are unpacked.
     *
     * @param visitor the visitor to process archives
     * @return the ZipHasher
     */
    public static ZipHasher withArchiveVisitor(ArchiveVisitor visitor) {
        return new ZipHasher(visitor);
    }

    /**
     * Creates an ArchiveVisitor that uses the given {@code resourceHasher} to hash archive entries.
     * @param resourceHasher the hasher
     * @return the archive visitor
     */
    public static ArchiveVisitor visitorFromResourceHasher(ResourceHasher resourceHasher) {
        return new ResourceHasherArchiveVisitor(resourceHasher);
    }

    /**
     * Creates a ZipHasher that hashes archive entries with the given {@code resourceHasher}.
     * If the archive cannot be hashed with this hasher because of exception, retries fingerprinting with {@code fallbackZipHasher}.
     * The {@code hashingExceptionReporter} is notified if the {@code fallbackZipHasher} is used.
     *
     * @param visitor the visitor to process archives
     * @param fallbackZipHasher the ZipHasher to use if hashing with the created one fails
     * @param hashingExceptionReporter the reporter to be notified about hashing exception
     * @return the ZipHasher
     */
    public static ZipHasher withFallback(ArchiveVisitor visitor, ZipHasher fallbackZipHasher, HashingExceptionReporter hashingExceptionReporter) {
        return new ZipHasher(visitor, fallbackZipHasher, hashingExceptionReporter);
    }

    @Nullable
    @Override
    public HashCode hash(RegularFileSnapshotContext fileSnapshotContext) {
        return hashZipContents(fileSnapshotContext.getSnapshot());
    }

    @Override
    public void appendConfigurationToHasher(Hasher hasher) {
        hasher.putString(getClass().getName());
        archiveVisitor.appendConfigurationToHasher(hasher);
    }

    @Nullable
    private HashCode hashZipContents(RegularFileSnapshot zipFileSnapshot) {
        try {
            return archiveVisitor.visitArchive(entryVisitor -> {
                List<FileSystemLocationFingerprint> fingerprints = fingerprintZipEntries(Objects.requireNonNull(entryVisitor), zipFileSnapshot.getAbsolutePath());
                if (fingerprints.isEmpty()) {
                    return null;
                }
                Hasher hasher = Hashing.newHasher();
                FingerprintHashingStrategy.SORT.appendToHasher(hasher, fingerprints);
                return hasher;
            });
        } catch (Exception e) {
            hashingExceptionReporter.report(zipFileSnapshot, e);
            if (fallbackZipHasher != null) {
                return fallbackZipHasher.hashZipContents(zipFileSnapshot);
            }
            return zipFileSnapshot.getHash();
        }
    }

    private static List<FileSystemLocationFingerprint> fingerprintZipEntries(EntryVisitor entryVisitor, String zipFile) throws IOException {
        try (ZipInput input = FileZipInput.create(new File(zipFile))) {
            List<FileSystemLocationFingerprint> fingerprints = Lists.newArrayList();
            fingerprintZipEntries(entryVisitor, "", zipFile, fingerprints, input);
            return fingerprints;
        }
    }

    private static void fingerprintZipEntries(
        EntryVisitor entryVisitor,
        String parentName,
        String rootParentName,
        List<FileSystemLocationFingerprint> fingerprints,
        ZipInput input
    ) throws IOException {
        fingerprints.add(newZipMarker(parentName));
        for (ZipEntry zipEntry : input) {
            if (zipEntry.isDirectory()) {
                continue;
            }
            String fullName = parentName.isEmpty() ? zipEntry.getName() : parentName + "/" + zipEntry.getName();
            ZipEntryContext zipEntryContext = new DefaultZipEntryContext(zipEntry, fullName, rootParentName);
            if (isZipFile(zipEntry.getName())) {
                zipEntryContext.getEntry().withInputStream(inputStream -> {
                    fingerprintZipEntries(entryVisitor, fullName, rootParentName, fingerprints, new StreamZipInput(inputStream));
                    return null;
                });
            } else {
                fingerprintZipEntry(entryVisitor, zipEntryContext, fingerprints);
            }
        }
    }

    private static void fingerprintZipEntry(EntryVisitor entryVisitor, ZipEntryContext zipEntryContext, List<FileSystemLocationFingerprint> fingerprints) throws IOException {
        HashCode hash = entryVisitor.visitEntry(zipEntryContext);
        if (hash != null) {
            fingerprints.add(new DefaultFileSystemLocationFingerprint(zipEntryContext.getFullName(), FileType.RegularFile, hash));
        }
    }

    private static DefaultFileSystemLocationFingerprint newZipMarker(String relativePath) {
        return new DefaultFileSystemLocationFingerprint(relativePath, FileType.RegularFile, EMPTY_HASH_MARKER);
    }

    public interface HashingExceptionReporter {
        void report(RegularFileSnapshot zipFileSnapshot, Exception e);
    }

    private static class ResourceHasherArchiveVisitor implements ArchiveVisitor, EntryVisitor {
        private final ResourceHasher resourceHasher;

        public ResourceHasherArchiveVisitor(ResourceHasher resourceHasher) {
            this.resourceHasher = resourceHasher;
        }

        @Override
        @Nullable
        public HashCode visitArchive(IoFunction<EntryVisitor, Hasher> visitAction) throws IOException {
            @Nullable Hasher hasher = visitAction.apply(this);
            return hasher != null ? hasher.hash() : null;
        }

        @Override
        @Nullable
        public HashCode visitEntry(ZipEntryContext zipEntryContext) throws IOException {
            return resourceHasher.hash(zipEntryContext);
        }

        @Override
        public void appendConfigurationToHasher(Hasher hasher) {
            resourceHasher.appendConfigurationToHasher(hasher);
        }
    }
}
