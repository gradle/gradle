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

package org.gradle.api.internal.filestore.ivy;

import org.gradle.api.Namer;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;
import org.gradle.internal.resource.local.GroupedAndNamedUniqueFileStore;

import java.io.File;

public class ArtifactIdentifierFileStore extends GroupedAndNamedUniqueFileStore<ModuleComponentArtifactIdentifier> {

    public static final int FILE_TREE_DEPTH_TO_TRACK_AND_CLEANUP = 4;

    private static final Grouper<ModuleComponentArtifactIdentifier> GROUPER = new Grouper<ModuleComponentArtifactIdentifier>() {
        @Override
        public String determineGroup(ModuleComponentArtifactIdentifier artifactId) {
            return artifactId.getComponentIdentifier().getGroup() + '/' + artifactId.getComponentIdentifier().getModule() + '/' + artifactId.getComponentIdentifier().getVersion();
        }

        @Override
        public int getDepth() {
            return FILE_TREE_DEPTH_TO_TRACK_AND_CLEANUP - 1;
        }
    };

    private static final Namer<ModuleComponentArtifactIdentifier> NAMER = new Namer<ModuleComponentArtifactIdentifier>() {
        @Override
        public String determineName(ModuleComponentArtifactIdentifier artifactId) {
            return artifactId.getFileName();
        }
    };

    public ArtifactIdentifierFileStore(File baseDir, TemporaryFileProvider temporaryFileProvider) {
        super(baseDir, temporaryFileProvider, GROUPER, NAMER);
    }
}
