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

import com.google.common.io.ByteStreams;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

public class ManifestFileZipEntryHasher implements ZipEntryHasher, ConfigurableNormalizer {
    private final ResourceEntryFilter attributeResourceFilter;

    public ManifestFileZipEntryHasher(ResourceEntryFilter attributeResourceFilter) {
        this.attributeResourceFilter = attributeResourceFilter;
    }

    @Override
    public void appendConfigurationToHasher(Hasher hasher) {
        attributeResourceFilter.appendConfigurationToHasher(hasher);
    }

    @Nullable
    @Override
    public HashCode hash(ZipEntryContext zipEntryContext) throws IOException {
        return hashManifest(ByteStreams.toByteArray(zipEntryContext.getEntry().getInputStream()));
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
}
