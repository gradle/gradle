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
import org.gradle.api.internal.file.archive.impl.FileZipInput;
import org.gradle.api.internal.file.archive.impl.StreamZipInput;
import org.gradle.api.internal.file.archive.ZipEntry;
import org.gradle.api.internal.file.archive.ZipInput;
import org.gradle.internal.file.FilePathUtil;
import org.gradle.internal.file.FileType;
import org.gradle.internal.fingerprint.FileSystemLocationFingerprint;
import org.gradle.internal.fingerprint.FingerprintHashingStrategy;
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
import java.util.function.Supplier;

public class ZipHasher implements RegularFileHasher, ConfigurableNormalizer {

    private static final Set<String> KNOWN_ZIP_EXTENSIONS = ImmutableSet.of("zip", "jar", "war", "rar", "ear", "apk", "aar");
    private static final Logger LOGGER = LoggerFactory.getLogger(ZipHasher.class);

    public static boolean isZipFile(final String name) {
        return KNOWN_ZIP_EXTENSIONS.contains(FilenameUtils.getExtension(name).toLowerCase(Locale.ROOT));
    }

    private final ResourceHasher resourceHasher;
    private final ResourceFilter resourceFilter;

    public ZipHasher(ResourceHasher resourceHasher, ResourceFilter resourceFilter) {
        this.resourceHasher = resourceHasher;
        this.resourceFilter = resourceFilter;
    }

    @Nullable
    @Override
    public HashCode hash(RegularFileSnapshot fileSnapshot) {
        return hashZipContents(fileSnapshot);
    }

    @Override
    public void appendConfigurationToHasher(Hasher hasher) {
        hasher.putString(getClass().getName());
        resourceHasher.appendConfigurationToHasher(hasher);
        resourceFilter.appendConfigurationToHasher(hasher);
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
            return hashMalformedZip(zipFileSnapshot, e);
        }
    }

    private HashCode hashMalformedZip(RegularFileSnapshot zipFileSnapshot, Exception e) {
        LOGGER.debug("Malformed archive '{}'. Falling back to full content hash instead of entry hashing.", zipFileSnapshot.getName(), e);
        return zipFileSnapshot.getHash();
    }

    private List<FileSystemLocationFingerprint> fingerprintZipEntries(String zipFile) throws IOException {
        try(ZipInput input = FileZipInput.create(new File(zipFile))) {
            List<FileSystemLocationFingerprint> fingerprints = Lists.newArrayList();
            fingerprintZipEntries("", zipFile, fingerprints, input);
            return fingerprints;
        }
    }

    private void fingerprintZipEntries(String parentName, String rootParentName, List<FileSystemLocationFingerprint> fingerprints, ZipInput input) throws IOException {
        fingerprints.add(newZipMarker(parentName));
        for (ZipEntry zipEntry : input) {
            ZipEntryRelativePath relativePath = new ZipEntryRelativePath(zipEntry);
            if (zipEntry.isDirectory() || resourceFilter.shouldBeIgnored(relativePath)) {
                continue;
            }
            String fullName = parentName.isEmpty() ? zipEntry.getName() : parentName + "/" + zipEntry.getName();
            ZipEntryContext zipEntryContext = new ZipEntryContext(zipEntry, fullName, rootParentName);
            if (isZipFile(zipEntry.getName())) {
                fingerprintZipEntries(fullName, rootParentName, fingerprints, new StreamZipInput(zipEntry.getInputStream()));
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
        return new DefaultFileSystemLocationFingerprint(relativePath, FileType.RegularFile, HashCode.fromInt(0));
    }

    private static class ZipEntryRelativePath implements Supplier<String[]> {
        private final ZipEntry zipEntry;

        private ZipEntryRelativePath(ZipEntry zipEntry) {
            this.zipEntry = zipEntry;
        }

        @Override
        public String[] get() {
            return FilePathUtil.getPathSegments(zipEntry.getName());
        }
    }
}
