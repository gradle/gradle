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

package org.gradle.api.internal.changedetection.state;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.io.ByteStreams;
import org.apache.commons.compress.utils.Lists;
import org.gradle.api.internal.file.archive.FileZipInput;
import org.gradle.api.internal.file.archive.StreamZipInput;
import org.gradle.api.internal.file.archive.ZipEntry;
import org.gradle.api.internal.file.archive.ZipInput;
import org.gradle.internal.Factory;
import org.gradle.internal.FileUtils;
import org.gradle.internal.IoActions;
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
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

public class ZipHasher implements RegularFileHasher, ConfigurableNormalizer {

    private static final Set<String> KNOWN_ZIP_EXTENSIONS = ImmutableSet.of(".zip", ".jar", ".war", ".rar", ".ear", ".apk", ".aar");
    private static final Logger LOGGER = LoggerFactory.getLogger(ZipHasher.class);

    public static boolean isZipFile(final String name) {
        for (String extension : KNOWN_ZIP_EXTENSIONS) {
            if (FileUtils.hasExtensionIgnoresCase(name, extension)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isManifestFile(final String name) {
        return name.equals("META-INF/MANIFEST.MF");
    }

    public static boolean isManifestPropertyFile(final String name) {
        return name.startsWith("META-INF/") && name.endsWith(".properties");
    }

    private final ResourceHasher resourceHasher;
    private final ResourceFilter resourceFilter;
    private final ResourceEntryFilter attributeResourceFilter;
    private final ResourceEntryFilter propertyResourceFilter;

    public ZipHasher(ResourceHasher resourceHasher, ResourceFilter resourceFilter, ResourceEntryFilter manifestAttributeResourceFilter, ResourceEntryFilter manifestPropertyResourceFilter) {
        this.resourceHasher = resourceHasher;
        this.resourceFilter = resourceFilter;
        this.attributeResourceFilter = manifestAttributeResourceFilter;
        this.propertyResourceFilter = manifestPropertyResourceFilter;
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
        attributeResourceFilter.appendConfigurationToHasher(hasher);
        propertyResourceFilter.appendConfigurationToHasher(hasher);
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
        ZipInput input = null;
        try {
            input = FileZipInput.create(new File(zipFile));
            List<FileSystemLocationFingerprint> fingerprints = Lists.newArrayList();
            fingerprintZipEntries("", zipFile, fingerprints, input);
            return fingerprints;
        } finally {
            IoActions.closeQuietly(input);
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
            if (isZipFile(zipEntry.getName())) {
                fingerprintZipEntries(fullName, rootParentName, fingerprints, new StreamZipInput(zipEntry.getInputStream()));
            } else if (isManifestFile(zipEntry.getName())) {
                fingerprintZipEntryContentWithFallback(rootParentName, fingerprints, zipEntry, fullName, this::hashManifest);
            } else if (isManifestPropertyFile(zipEntry.getName())) {
                fingerprintZipEntryContentWithFallback(rootParentName, fingerprints, zipEntry, fullName, this::hashProperties);
            } else {
                fingerprintZipEntry(zipEntry, fullName, fingerprints);
            }
        }
    }

    private void fingerprintZipEntry(ZipEntry zipEntry, String fullName, List<FileSystemLocationFingerprint> fingerprints) throws IOException {
        HashCode hash = resourceHasher.hash(zipEntry);
        if (hash != null) {
            fingerprints.add(new DefaultFileSystemLocationFingerprint(fullName, FileType.RegularFile, hash));
        }
    }

    private void fingerprintZipEntryContentWithFallback(String rootParentName, List<FileSystemLocationFingerprint> fingerprints, ZipEntry zipEntry, String fullName, ByteContentHasher hasher) throws IOException {
        // JdkZipEntry can be backed by a stream, so we assume that getInputStream is a single shot and read the manifest to a byte array so we can fallback should content hashing fail
        byte[] entryBytes = ByteStreams.toByteArray(zipEntry.getInputStream());
        try {
            HashCode hash = hasher.hash(entryBytes);
            if (hash != null) {
                fingerprints.add(new DefaultFileSystemLocationFingerprint(fullName, FileType.RegularFile, hash));
            }
        } catch (Exception e) {
            LOGGER.warn("Could not load fingerprint " + rootParentName + ". Falling back to regular fingerprinting", e);
            ZipEntry manifestZipEntry = new ZipEntry() {
                @Override
                public boolean isDirectory() {
                    return false;
                }

                @Override
                public String getName() {
                    return zipEntry.getName();
                }

                @Override
                public InputStream getInputStream() {
                    return new ByteArrayInputStream(entryBytes);
                }

                @Override
                public byte[] getContent() {
                    return entryBytes;
                }

                @Override
                public int size() {
                    return entryBytes.length;
                }
            };
            fingerprintZipEntry(manifestZipEntry, fullName, fingerprints);
        }
    }

    private HashCode hashManifest(byte[] entryBytes) throws IOException {
        Manifest manifest = new Manifest(new ByteArrayInputStream(entryBytes));
        Hasher hasher = Hashing.newHasher();
        Attributes mainAttributes = manifest.getMainAttributes();
        hashManifestAttributes(mainAttributes, "main", hasher);
        Map<String, Attributes> entries = manifest.getEntries();
        Set<String> names = new TreeSet<>(manifest.getEntries().keySet());
        for (String name : names) {
            hashManifestAttributes(entries.get(name), name, hasher);
        }
        return hasher.hash();
    }

    private void hashManifestAttributes(Attributes attributes, String name, Hasher hasher) {
        Map<String, String> entries = attributes
            .entrySet()
            .stream()
            .collect(Collectors.toMap(
                entry -> entry.getKey().toString().toLowerCase(Locale.ROOT),
                entry -> (String) entry.getValue()
            ));
        List<Map.Entry<String, String>> normalizedEntries = entries.
            entrySet()
            .stream()
            .filter(entry -> !attributeResourceFilter.shouldBeIgnored(entry.getKey()))
            .sorted(Map.Entry.comparingByKey())
            .collect(Collectors.toList());

        // Short-circuiting when there's no matching entries allows empty manifest sections to be ignored
        // that allows an manifest without those sections to hash identically to the one with effectively empty sections
        if (!normalizedEntries.isEmpty()) {
            hasher.putString(name);
            for (Map.Entry<String, String> entry : normalizedEntries) {
                hasher.putString(entry.getKey());
                hasher.putString(entry.getValue());
            }
        }
    }

    private HashCode hashProperties(byte[] entryBytes) throws IOException {
        Hasher hasher = Hashing.newHasher();
        Properties properties = new Properties();
        properties.load(new ByteArrayInputStream(entryBytes));
        Map<String, String> entries = Maps.fromProperties(properties);
        entries
            .entrySet()
            .stream()
            .filter(entry -> !propertyResourceFilter.shouldBeIgnored(entry.getKey()))
            .sorted()
            .forEach(entry -> {
                hasher.putString(entry.getKey());
                hasher.putString(entry.getValue());
            });
        return hasher.hash();
    }

    private DefaultFileSystemLocationFingerprint newZipMarker(String relativePath) {
        return new DefaultFileSystemLocationFingerprint(relativePath, FileType.RegularFile, HashCode.fromInt(0));
    }

    private static class ZipEntryRelativePath implements Factory<String[]> {
        private final ZipEntry zipEntry;

        private ZipEntryRelativePath(ZipEntry zipEntry) {
            this.zipEntry = zipEntry;
        }

        @Override
        public String[] create() {
            return FilePathUtil.getPathSegments(zipEntry.getName());
        }
    }

    private interface ByteContentHasher {
        @Nullable
        HashCode hash(byte[] bytes) throws IOException;
    }
}
