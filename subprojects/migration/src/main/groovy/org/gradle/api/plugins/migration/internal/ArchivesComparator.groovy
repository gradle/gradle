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

import org.gradle.tooling.model.internal.migration.Archive

import com.google.common.collect.Sets

class ArchivesComparator {
    private final ProjectComparison projectComparison
    private final BuildComparisonListener listener

    ArchivesComparator(ProjectComparison projectComparison, BuildComparisonListener listener) {
        this.projectComparison = projectComparison
        this.listener = listener
    }

    void compareArchives(Set<Archive> archives1, Set<Archive> archives2) {
        def archivesByName1 = archives1.collectEntries { [it.file.name, it] }
        def archivesByName2 = archives2.collectEntries { [it.file.name, it] }

        handleCommonArchives(archivesByName1, archivesByName2)
        handleOrphanArchives(projectComparison.project1, archivesByName1, archivesByName2)
        handleOrphanArchives(projectComparison.project2, archivesByName2, archivesByName1)
    }

    private void handleCommonArchives(Map<String, Archive> archivesByName1, Map<String, Archive> archivesByName2) {
        def commonArchiveNames = Sets.intersection(archivesByName1.keySet(), archivesByName2.keySet())
        for (name in commonArchiveNames) {
            def archive1 = archivesByName1[name]
            def archive2 = archivesByName2[name]
            def archiveComparison = new ArchiveComparison(parent: projectComparison)
            archiveComparison.archive1 = new ComparedArchive(parent: projectComparison.project1, archiveFile: archive1.file)
            archiveComparison.archive2 = new ComparedArchive(parent: projectComparison.project2, archiveFile: archive2.file)
            projectComparison.archiveComparisons << archiveComparison

            listener.archiveComparisonStarted(archiveComparison)
            new ArchiveEntriesComparator(archiveComparison, listener).compareEntries()
            listener.archiveComparisonFinished(archiveComparison)
        }
    }

    private void handleOrphanArchives(ComparedProject project, Map<String, Archive> archivesByName, Map<String, Archive> otherArchivesByName) {
        def orphanArchiveNames = Sets.difference(archivesByName.keySet(), otherArchivesByName.keySet())
        for (name in orphanArchiveNames) {
            def archive = archivesByName[name]
            def comparedArchive = new ComparedArchive(parent: project, archiveFile: archive.file)
            projectComparison.orphanArchives << comparedArchive
            listener.orphanArchiveFound(comparedArchive)
        }
    }
}
