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
import org.gradle.internal.fingerprint.hashing.RegularFileSnapshotContextHasher;
import org.gradle.internal.fingerprint.hashing.RegularFileSnapshotContext;
import org.gradle.internal.fingerprint.hashing.ResourceHasher;
import org.gradle.internal.fingerprint.hashing.ZipEntryContext;
import org.gradle.internal.fingerprint.impl.DefaultFileSystemLocationFingerprint;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.snapshot.RegularFileSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Computes the fingerprint of a ZIP archive.
 */
public class ZipHasher implements RegularFileSnapshotContextHasher, ConfigurableNormalizer {

    private static final Set<String> KNOWN_ZIP_EXTENSIONS = ImmutableSet.of("zip", "jar", "war", "rar", "ear", "apk", "aar", "klib");
    private static final Logger LOGGER = LoggerFactory.getLogger(ZipHasher.class);
    private static final HashCode EMPTY_HASH_MARKER = Hashing.signature(ZipHasher.class);

    public static boolean isZipFile(final String name) {
        return KNOWN_ZIP_EXTENSIONS.contains(FilenameUtils.getExtension(name).toLowerCase(Locale.ROOT));
    }

    private final ResourceHasher resourceHasher;
    private final ZipHasher fallbackZipHasher;
    private final HashingExceptionReporter hashingExceptionReporter;

    private ZipHasher(ResourceHasher resourceHasher, @Nullable ZipHasher fallbackZipHasher, HashingExceptionReporter hashingExceptionReporter) {
        this.resourceHasher = resourceHasher;
        this.fallbackZipHasher = fallbackZipHasher;
        this.hashingExceptionReporter = hashingExceptionReporter;
    }

    /**
     * Creates a ZipHasher that hashes archive entries with the given {@code resourceHasher}. Nested archives are unpacked.
     *
     * @param resourceHasher the hasher to hash archive entries
     * @return the ZipHasher
     */
    public static ZipHasher withResourceHasher(ResourceHasher resourceHasher) {
        return new ZipHasher(
            resourceHasher,
            null,
            (s, e) -> LOGGER.debug("Malformed archive '{}'. Falling back to full content hash instead of entry hashing.", s.getName(), e)
        );
    }

    /**
     * Creates a ZipHasher that hashes archive entries with the given {@code resourceHasher}.
     * If the archive cannot be hashed with this hasher because of exception, retries fingerprinting with {@code fallbackZipHasher}.
     * The {@code hashingExceptionReporter} is notified if the {@code fallbackZipHasher} is used.
     *
     * @param resourceHasher the hasher to hash archive entries
     * @param fallbackZipHasher the ZipHasher to use if hashing with the created one fails
     * @param hashingExceptionReporter the reporter to be notified about hashing exception
     * @return the ZipHasher
     */
    public static ZipHasher withResourceHasherAndFallback(ResourceHasher resourceHasher, ZipHasher fallbackZipHasher, HashingExceptionReporter hashingExceptionReporter) {
        return new ZipHasher(resourceHasher, fallbackZipHasher, hashingExceptionReporter);
    }

    @Nullable
    @Override
    public HashCode hash(RegularFileSnapshotContext fileSnapshotContext) {
        return hashZipContents(fileSnapshotContext.getSnapshot());
    }

    @Override
    public void appendConfigurationToHasher(Hasher hasher) {
        hasher.putString(getClass().getName());
        resourceHasher.appendConfigurationToHasher(hasher);
    }

    @Nullable
    private HashCode hashZipContents(RegularFileSnapshot zipFileSnapshot) {
        try {
            List<FileSystemLocationFingerprint> fingerprints = fingerprintZipEntries(zipFileSnapshot.getAbsolutePath());
            if (fingerprints.isEmpty()) {
                return null;
            }
            Hasher hasher = Hashing.newHasher();
            FingerprintHashingStrategy.SORT.appendToHasher(hasher, fingerprints);
            return hasher.hash();
        } catch (Exception e) {
            hashingExceptionReporter.report(zipFileSnapshot, e);
            if (fallbackZipHasher != null) {
                return fallbackZipHasher.hashZipContents(zipFileSnapshot);
            }
            return zipFileSnapshot.getHash();
        }
    }

    private List<FileSystemLocationFingerprint> fingerprintZipEntries(String zipFile) throws IOException {
        try (ZipInput input = FileZipInput.create(new File(zipFile))) {
            List<FileSystemLocationFingerprint> fingerprints = Lists.newArrayList();
            fingerprintZipEntries("", zipFile, fingerprints, input);
            return fingerprints;
        }
    }

    private void fingerprintZipEntries(String parentName, String rootParentName, List<FileSystemLocationFingerprint> fingerprints, ZipInput input) throws IOException {
        fingerprints.add(newZipMarker(parentName));
        for (ZipEntry zipEntry : input) {
            if (zipEntry.isDirectory()) {
                continue;
            }
            String fullName = parentName.isEmpty() ? zipEntry.getName() : parentName + "/" + zipEntry.getName();
            ZipEntryContext zipEntryContext = new DefaultZipEntryContext(zipEntry, fullName, rootParentName);
            if (isZipFile(zipEntry.getName())) {
                zipEntryContext.getEntry().withInputStream(inputStream -> {
                    fingerprintZipEntries(fullName, rootParentName, fingerprints, new StreamZipInput(inputStream));
                    return null;
                });
            } else {
                fingerprintZipEntry(zipEntryContext, fingerprints);
            }
        }
    }

    private void fingerprintZipEntry(ZipEntryContext zipEntryContext, List<FileSystemLocationFingerprint> fingerprints) throws IOException {
        HashCode hash = resourceHasher.hash(zipEntryContext);
        if (hash != null) {
            fingerprints.add(new DefaultFileSystemLocationFingerprint(zipEntryContext.getFullName(), FileType.RegularFile, hash));
        }
    }

    private DefaultFileSystemLocationFingerprint newZipMarker(String relativePath) {
        return new DefaultFileSystemLocationFingerprint(relativePath, FileType.RegularFile, EMPTY_HASH_MARKER);
    }

    public interface HashingExceptionReporter {
        void report(RegularFileSnapshot zipFileSnapshot, Exception e);
    }
}
