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

package org.gradle.api.tasks.migration

import org.gradle.api.logging.Logging
import org.gradle.api.logging.Logger

class LoggingBuildComparisonListener implements BuildComparisonListener {
    private static final Logger LOGGER = Logging.getLogger(LoggingBuildComparisonListener)

    void buildComparisonStarted(BuildComparison comparison) {
        LOGGER.lifecycle("Comparing build '$comparison.build2.displayName' with '$comparison.build1.displayName'")
    }

    void buildComparisonFinished(BuildComparison comparison) {
        LOGGER.lifecycle("Finished comparing build '$comparison.build2.displayName' with '$comparison.build1.displayName'")
    }

    void projectComparisonStarted(ProjectComparison comparison) {
        LOGGER.lifecycle("Comparing outputs of project '$comparison.project1.path'")
    }

    void projectComparisonFinished(ProjectComparison comparison) {
        LOGGER.lifecycle("Finished comparing outputs of project '$comparison.project1.path'")
    }

    void orphanProjectFound(ComparedProject project) {
        LOGGER.lifecycle("Project '$project.path' only exists in build '$project.parent.displayName'")
    }

    void archiveComparisonStarted(ArchiveComparison comparison) {
        LOGGER.lifecycle("Comparing archive '$comparison.archive1.archiveFile.name'")
    }

    void archiveComparisonFinished(ArchiveComparison comparison) {
        LOGGER.lifecycle("Finished comparing archive '$comparison.archive1.archiveFile.name'")
    }

    void orphanArchiveFound(ComparedArchive archive) {
        LOGGER.lifecycle("Archive '$archive.archiveFile.name' only exists in build '$archive.parent.parent.displayName'")
    }

    void archiveEntryDifferenceFound(ArchiveEntryComparison comparison) {
        def entry1 = comparison.entry1
        def entry2 = comparison.entry2

        assert entry1.path == entry2.path

        if (entry1.directory != entry2.directory) {
            LOGGER.lifecycle("Archive entry '$entry1.path' has different types: $entry1.type vs. $entry2.type")
            return
        }
        if (entry1.size != entry2.size) {
            LOGGER.lifecycle("Archive entry '$entry1.path' has different sizes: $entry1.size vs. $entry2.size")
            return
        }
        if (entry1.crc != entry2.crc) {
            LOGGER.lifecycle("Archive entry '$entry1.path' has different CRCs: $entry1.crc vs. $entry2.crc")
        }
    }

    void orphanArchiveEntryFound(ComparedArchiveEntry entry) {
        LOGGER.lifecycle("Archive entry '$entry.path' only exists in build '$entry.parent.parent.parent.displayName'")
    }
}
