/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.internal.artifacts.repositories.metadata;

import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;
import org.gradle.internal.component.model.PersistentModuleSource;
import org.gradle.internal.hash.HashCode;

import java.io.File;

/**
 * This module source stores information about the metadata file
 * from which a module resolve metadata has been built. In particular,
 * it stores the artifact id corresponding to the metadata file and
 * gives access to the file stored in the local artifact cache.
 *
 * This information is used during dependency verification (either
 * writing or validating).
 */
public interface MetadataFileSource extends PersistentModuleSource {
    int CODEC_ID = 1;

    File getArtifactFile();

    ModuleComponentArtifactIdentifier getArtifactId();

    HashCode getSha1();

    @Override
    default int getCodecId() {
        return CODEC_ID;
    }
}
