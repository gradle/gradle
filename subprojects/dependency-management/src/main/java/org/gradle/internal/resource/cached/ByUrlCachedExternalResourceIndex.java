/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.internal.resource.cached;

import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheLockingManager;
import org.gradle.internal.file.FileAccessTracker;
import org.gradle.internal.resource.metadata.ExternalResourceMetaData;
import org.gradle.internal.serialize.BaseSerializerFactory;
import org.gradle.util.internal.BuildCommencedTimeProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

public class ByUrlCachedExternalResourceIndex extends DefaultCachedExternalResourceIndex<String> {
    private final static Logger LOGGER = LoggerFactory.getLogger(ByUrlCachedExternalResourceIndex.class);

    public ByUrlCachedExternalResourceIndex(String persistentCacheFile, BuildCommencedTimeProvider timeProvider, ArtifactCacheLockingManager artifactCacheLockingManager, FileAccessTracker fileAccessTracker, Path commonRootPath) {
        super(persistentCacheFile, BaseSerializerFactory.STRING_SERIALIZER, timeProvider, artifactCacheLockingManager, fileAccessTracker, commonRootPath);
    }

    /**
     * Creates a resource index which will mirror every download to a local
     * directory.
     */
    public CachedExternalResourceIndex<String> withMirrorDirectory(File path) {
        return new FileMirroringExternalResourceIndex(path);
    }

    private class FileMirroringExternalResourceIndex implements CachedExternalResourceIndex<String> {
        private final File mirrorDirectory;

        public FileMirroringExternalResourceIndex(File path) {
            this.mirrorDirectory = path;
        }

        @Override
        public void store(String key, File artifactFile, @Nullable ExternalResourceMetaData metaData) {
            ByUrlCachedExternalResourceIndex.this.store(key, artifactFile, metaData);
            writeArtifactToMirrorDirectory(artifactFile, metaData);
        }

        private void writeArtifactToMirrorDirectory(File artifactFile, @Nullable ExternalResourceMetaData metaData) {
            if (metaData != null) {
                URI location = metaData.getLocation();
                File repoDir = new File(mirrorDirectory, location.getHost());
                File filePath = new File(repoDir, location.getPath()).getAbsoluteFile();
                if (!filePath.exists()) {
                    File parentDir = filePath.getParentFile();
                    if (parentDir.isDirectory() || parentDir.mkdirs()) {
                        try {
                            Files.copy(artifactFile.toPath(), filePath.toPath());
                        } catch (IOException e) {
                            LOGGER.warn("Unable to copy file {} to {}", artifactFile, filePath);
                        }
                    }
                }
            }
        }

        @Override
        public void storeMissing(String key) {
            ByUrlCachedExternalResourceIndex.this.storeMissing(key);
        }

        @Nullable
        @Override
        public CachedExternalResource lookup(String key) {
            CachedExternalResource lookup = ByUrlCachedExternalResourceIndex.this.lookup(key);
            if (lookup != null) {
                File cachedFile = lookup.getCachedFile();
                ExternalResourceMetaData externalResourceMetaData = lookup.getExternalResourceMetaData();
                writeArtifactToMirrorDirectory(cachedFile, externalResourceMetaData);
            }
            return lookup;
        }

        @Override
        public void clear(String key) {
            ByUrlCachedExternalResourceIndex.this.clear(key);
        }
    }
}
