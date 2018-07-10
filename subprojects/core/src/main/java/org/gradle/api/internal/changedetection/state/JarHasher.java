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

import com.google.common.collect.Lists;
import org.apache.commons.io.IOUtils;
import org.gradle.api.internal.changedetection.state.mirror.logical.NormalizedPathFingerprintCompareStrategy;
import org.gradle.caching.internal.BuildCacheHasher;
import org.gradle.caching.internal.DefaultBuildCacheHasher;
import org.gradle.internal.file.FileType;
import org.gradle.internal.hash.HashCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class JarHasher implements RegularFileHasher, ConfigurableNormalizer {
    private static final Logger LOGGER = LoggerFactory.getLogger(JarHasher.class);

    private final ResourceHasher classpathResourceHasher;

    public JarHasher(ResourceHasher classpathResourceHasher) {
        this.classpathResourceHasher = classpathResourceHasher;
    }

    @Nullable
    @Override
    public HashCode hash(String absolutePath, Iterable<String> relativePath, FileContentSnapshot content) {
        return hashJarContents(absolutePath, content);
    }

    @Override
    public void appendConfigurationToHasher(BuildCacheHasher hasher) {
        hasher.putString(getClass().getName());
        classpathResourceHasher.appendConfigurationToHasher(hasher);        }

    private HashCode hashJarContents(String jarFile, FileContentSnapshot content) {
        try {
            List<NormalizedFileSnapshot> snapshots = snapshotZipEntries(jarFile);
            if (snapshots.isEmpty()) {
                return null;
            }
            DefaultBuildCacheHasher hasher = new DefaultBuildCacheHasher();
            NormalizedPathFingerprintCompareStrategy.appendSortedToHasher(hasher, snapshots);
            return hasher.hash();
        } catch (Exception e) {
            return hashMalformedZip(new File(jarFile).getName(), content, e);
        }
    }

    @SuppressWarnings("Since15")
    private List<NormalizedFileSnapshot> snapshotZipEntries(String jarFile) throws IOException {
        List<NormalizedFileSnapshot> snapshots = Lists.newArrayList();
        InputStream fileInputStream = null;
        try {
            fileInputStream = Files.newInputStream(Paths.get(jarFile));
            ZipInputStream zipInput = new ZipInputStream(fileInputStream);
            ZipEntry zipEntry;

            while ((zipEntry = zipInput.getNextEntry()) != null) {
                if (zipEntry.isDirectory()) {
                    continue;
                }
                HashCode hash = classpathResourceHasher.hash(zipEntry, zipInput);
                if (hash != null) {
                    snapshots.add(new DefaultNormalizedFileSnapshot(zipEntry.getName(), FileType.RegularFile, hash));
                }
            }

            return snapshots;
        } finally {
            IOUtils.closeQuietly(fileInputStream);
        }
    }


    private HashCode hashMalformedZip(String jarName, FileContentSnapshot content, Exception e) {
        LOGGER.debug("Malformed jar '{}' found on classpath. Falling back to full content hash instead of classpath hashing.", jarName, e);
        return content.getContentMd5();
    }
}
