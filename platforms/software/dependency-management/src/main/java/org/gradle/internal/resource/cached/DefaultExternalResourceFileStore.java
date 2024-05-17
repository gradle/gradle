/*
 * Copyright 2017 the original author or authors.
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

import org.apache.commons.lang.StringUtils;
import org.gradle.api.Namer;
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheMetadata;
import org.gradle.api.internal.file.temp.TemporaryFileProvider;
import org.gradle.internal.file.FileAccessTimeJournal;
import org.gradle.internal.hash.ChecksumService;
import org.gradle.internal.resource.local.GroupedAndNamedUniqueFileStore;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.inject.Inject;
import java.io.File;

public class DefaultExternalResourceFileStore extends GroupedAndNamedUniqueFileStore<String> implements ExternalResourceFileStore {

    private static final int NUMBER_OF_GROUPING_DIRS = 1;
    public static final int FILE_TREE_DEPTH_TO_TRACK_AND_CLEANUP = NUMBER_OF_GROUPING_DIRS + NUMBER_OF_CHECKSUM_DIRS;

    private static final Grouper<String> GROUPER = new Grouper<String>() {
        @Override
        public String determineGroup(String s) {
            return String.valueOf(Math.abs(s.hashCode()) % 100);
        }

        @Override
        public int getNumberOfGroupingDirs() {
            return NUMBER_OF_GROUPING_DIRS;
        }
    };

    private static final Namer<String> NAMER = s -> StringUtils.substringAfterLast(s, "/");

    private DefaultExternalResourceFileStore(File baseDir, TemporaryFileProvider tmpProvider, FileAccessTimeJournal fileAccessTimeJournal, ChecksumService checksumService) {
        super(baseDir, tmpProvider, fileAccessTimeJournal, GROUPER, NAMER, checksumService);
    }

    @ServiceScope(Scopes.BuildTree.class)
    public static class Factory {
        private final TemporaryFileProvider temporaryFileProvider;
        private final FileAccessTimeJournal fileAccessTimeJournal;
        private final ChecksumService checksumService;

        @Inject
        public Factory(TemporaryFileProvider temporaryFileProvider, FileAccessTimeJournal fileAccessTimeJournal, ChecksumService checksumService) {
            this.temporaryFileProvider = temporaryFileProvider;
            this.fileAccessTimeJournal = fileAccessTimeJournal;
            this.checksumService = checksumService;
        }

        public DefaultExternalResourceFileStore create(ArtifactCacheMetadata artifactCacheMetadata) {
            return new DefaultExternalResourceFileStore(
                artifactCacheMetadata.getExternalResourcesStoreDirectory(),
                temporaryFileProvider,
                fileAccessTimeJournal,
                checksumService
            );
        }
    }
}
