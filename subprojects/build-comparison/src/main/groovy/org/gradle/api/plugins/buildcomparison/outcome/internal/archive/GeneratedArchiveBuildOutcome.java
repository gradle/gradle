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

package org.gradle.api.plugins.buildcomparison.outcome.internal.archive;

import org.gradle.internal.filestore.FileStoreEntry;
import org.gradle.api.plugins.buildcomparison.outcome.internal.BuildOutcomeSupport;

import java.io.File;

public class GeneratedArchiveBuildOutcome extends BuildOutcomeSupport {

    private final FileStoreEntry fileStoreEntry;
    private final String rootRelativePath;

    public GeneratedArchiveBuildOutcome(String name, String description, FileStoreEntry fileStoreEntry, String rootRelativePath) {
        super(name, description);
        this.fileStoreEntry = fileStoreEntry;
        this.rootRelativePath = rootRelativePath;
    }

    /**
     * The generated archive, may be null.
     *
     * If null, the archives was expected to have been generated but was not.
     *
     * @return The generated archive, or null if no archive was generated.
     */
    public File getArchiveFile() {
        return fileStoreEntry == null ? null : fileStoreEntry.getFile();
    }

    /**
     * The relative path to where this archive was generated, relative to some meaningful root.
     *
     * @return The relative path.
     */
    public String getRootRelativePath() {
        return rootRelativePath;
    }
}
