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

package org.gradle.api.internal.filestore;

import org.gradle.api.Namer;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;
import org.gradle.internal.file.FileAccessTimeJournal;
import org.gradle.internal.hash.ChecksumService;
import org.gradle.internal.resource.local.GroupedAndNamedUniqueFileStore;

import java.io.File;

public class DefaultArtifactIdentifierFileStore extends GroupedAndNamedUniqueFileStore<ModuleComponentArtifactIdentifier> implements ArtifactIdentifierFileStore {

    private static final int NUMBER_OF_GROUPING_DIRS = 3;
    public static final int FILE_TREE_DEPTH_TO_TRACK_AND_CLEANUP = NUMBER_OF_GROUPING_DIRS + NUMBER_OF_CHECKSUM_DIRS;

    private static final Grouper<ModuleComponentArtifactIdentifier> GROUPER = new Grouper<ModuleComponentArtifactIdentifier>() {
        @Override
        public String determineGroup(ModuleComponentArtifactIdentifier artifactId) {
            return artifactId.getComponentIdentifier().getGroup() + '/' + artifactId.getComponentIdentifier().getModule() + '/' + artifactId.getComponentIdentifier().getVersion();
        }

        @Override
        public int getNumberOfGroupingDirs() {
            return NUMBER_OF_GROUPING_DIRS;
        }
    };

    private static final Namer<ModuleComponentArtifactIdentifier> NAMER = ModuleComponentArtifactIdentifier::getFileName;

    public DefaultArtifactIdentifierFileStore(File baseDir, TemporaryFileProvider temporaryFileProvider, FileAccessTimeJournal fileAccessTimeJournal, ChecksumService checksumService) {
        super(baseDir, temporaryFileProvider, fileAccessTimeJournal, GROUPER, NAMER, checksumService);
    }
}
