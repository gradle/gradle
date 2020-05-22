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

import org.gradle.api.internal.file.archive.ZipEntry;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.snapshot.RegularFileSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;

public class MetaInfAwareClasspathResourceHasher implements ResourceHasher {
    private static final Logger LOGGER = LoggerFactory.getLogger(MetaInfAwareClasspathResourceHasher.class);

    private final ResourceHasher delegate;
    private final ManifestFileZipEntryHasher manifestFileZipEntryHasher;
    private final PropertiesFileZipEntryHasher propertiesFileZipEntryHasher;

    public MetaInfAwareClasspathResourceHasher(ResourceHasher delegate, ManifestFileZipEntryHasher manifestFileZipEntryHasher, PropertiesFileZipEntryHasher propertiesFileZipEntryHasher) {
        this.delegate = delegate;
        this.manifestFileZipEntryHasher = manifestFileZipEntryHasher;
        this.propertiesFileZipEntryHasher = propertiesFileZipEntryHasher;
    }

    @Override
    public void appendConfigurationToHasher(Hasher hasher) {
        delegate.appendConfigurationToHasher(hasher);
        manifestFileZipEntryHasher.appendConfigurationToHasher(hasher);
        propertiesFileZipEntryHasher.appendConfigurationToHasher(hasher);
    }

    @Nullable
    @Override
    public HashCode hash(RegularFileSnapshot snapshot) {
        return delegate.hash(snapshot);
    }

    @Nullable
    @Override
    public HashCode hash(ZipEntry zipEntry) throws IOException {
        if (isManifestFile(zipEntry.getName())) {
            return tryHashWithFallback(zipEntry, manifestFileZipEntryHasher);
        } else if (isManifestPropertyFile(zipEntry.getName())) {
            return tryHashWithFallback(zipEntry, propertiesFileZipEntryHasher);
        } else {
            return delegate.hash(zipEntry);
        }
    }

    @Nullable
    private HashCode tryHashWithFallback(ZipEntry zipEntry, ZipEntryHasher hasher) throws IOException {
        try {
            return hasher.hash(zipEntry);
        } catch (IOException e) {
            LOGGER.warn("Could not load fingerprint for " + zipEntry.getName() + ". Falling back to regular fingerprinting", e);
            return delegate.hash(zipEntry);
        }
    }

    private static boolean isManifestFile(final String name) {
        return name.equals("META-INF/MANIFEST.MF");
    }

    private static boolean isManifestPropertyFile(final String name) {
        return name.startsWith("META-INF/") && name.endsWith(".properties");
    }
}
