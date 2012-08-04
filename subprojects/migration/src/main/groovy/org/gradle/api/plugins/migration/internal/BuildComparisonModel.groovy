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

package org.gradle.api.plugins.migration.internal

class BuildComparison {
    ComparedBuild build1
    ComparedBuild build2

    List<ComparedProject> orphanProjects = []
    List<ProjectComparison> projectComparisons = []
}

class ComparedBuild {
    String displayName
}

class ComparedProject {
    ComparedBuild parent

    String name
    String path
}

class ProjectComparison {
    BuildComparison parent

    ComparedProject project1
    ComparedProject project2

    List<ComparedArchive> orphanArchives = []
    List<ArchiveComparison> archiveComparisons = []
}

class ComparedArchive {
    ComparedProject parent

    File archiveFile
    int numberOfEntries
}

class ArchiveComparison {
    ProjectComparison parent

    ComparedArchive archive1
    ComparedArchive archive2

    List<ComparedArchiveEntry> orphanEntries = []
    List<ArchiveEntryComparison> entryDifferences = []
}

class ComparedArchiveEntry {
    ComparedArchive parent

    String path
    boolean directory
    long size
    long crc

    String getType() {
        directory ? "directory" : "file"
    }
}

class ArchiveEntryComparison {
    ArchiveComparison parent

    ComparedArchiveEntry entry1
    ComparedArchiveEntry entry2
}
