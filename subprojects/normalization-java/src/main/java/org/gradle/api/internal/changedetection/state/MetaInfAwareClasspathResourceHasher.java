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

import org.gradle.internal.fingerprint.hashing.RegularFileSnapshotContext;
import org.gradle.internal.fingerprint.hashing.ResourceHasher;
import org.gradle.internal.fingerprint.hashing.ZipEntryContext;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

import static java.lang.String.join;

public class MetaInfAwareClasspathResourceHasher extends FallbackHandlingResourceHasher {
    private static final Logger LOGGER = LoggerFactory.getLogger(MetaInfAwareClasspathResourceHasher.class);

    private final ResourceEntryFilter attributeResourceFilter;

    public MetaInfAwareClasspathResourceHasher(ResourceHasher delegate, ResourceEntryFilter attributeResourceFilter) {
        super(delegate);
        this.attributeResourceFilter = attributeResourceFilter;
    }

    @Override
    public void appendConfigurationToHasher(Hasher hasher) {
        super.appendConfigurationToHasher(hasher);
        hasher.putString(getClass().getName());
        attributeResourceFilter.appendConfigurationToHasher(hasher);
    }

    @Override
    boolean filter(RegularFileSnapshotContext context) {
        return isManifestFile(join("/", context.getRelativePathSegments().get()));
    }

    @Override
    boolean filter(ZipEntryContext context) {
        return !context.getEntry().isDirectory() && isManifestFile(context.getEntry().getName());
    }

    @Override
    Optional<HashCode> tryHash(RegularFileSnapshotContext snapshotContext) {
        return Optional.of(snapshotContext)
            .map(context -> {
                try (FileInputStream manifestFileInputStream = new FileInputStream(context.getSnapshot().getAbsolutePath())) {
                    return hashManifest(manifestFileInputStream);
                } catch (IOException e) {
                    LOGGER.debug("Could not load fingerprint for " + context.getSnapshot().getAbsolutePath() + ". Falling back to full entry fingerprinting", e);
                    return null;
                }
            });
    }

    @Override
    Optional<HashCode> tryHash(ZipEntryContext zipEntryContext) {
        return Optional.of(zipEntryContext)
            .map(context -> {
                try {
                    return zipEntryContext.getEntry().withInputStream(this::hashManifest);
                } catch (IOException e) {
                    LOGGER.debug("Could not load fingerprint for " + zipEntryContext.getRootParentName() + "!" + zipEntryContext.getFullName() + ". Falling back to full entry fingerprinting", e);
                    return null;
                }
            });
    }

    private static boolean isManifestFile(final String name) {
        return name.equals("META-INF/MANIFEST.MF");
    }

    private HashCode hashManifest(InputStream inputStream) throws IOException {
        Manifest manifest = new Manifest(inputStream);
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
}
