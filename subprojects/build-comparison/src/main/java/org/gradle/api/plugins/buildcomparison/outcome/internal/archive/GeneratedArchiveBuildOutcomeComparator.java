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

import org.gradle.api.Transformer;
import org.gradle.api.plugins.buildcomparison.compare.internal.BuildOutcomeComparator;
import org.gradle.api.plugins.buildcomparison.outcome.internal.BuildOutcomeAssociation;
import org.gradle.api.plugins.buildcomparison.outcome.internal.archive.entry.ArchiveEntry;
import org.gradle.api.plugins.buildcomparison.outcome.internal.archive.entry.ArchiveEntryComparison;
import org.gradle.api.plugins.buildcomparison.outcome.internal.archive.entry.FileToArchiveEntrySetTransformer;
import org.gradle.internal.Pair;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.util.Collections;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

public class GeneratedArchiveBuildOutcomeComparator implements BuildOutcomeComparator<GeneratedArchiveBuildOutcome, GeneratedArchiveBuildOutcomeComparisonResult> {

    private final Transformer<Set<ArchiveEntry>, File> archiveToEntriesTransformer;

    public GeneratedArchiveBuildOutcomeComparator() {
        this(new FileToArchiveEntrySetTransformer());
    }

    GeneratedArchiveBuildOutcomeComparator(Transformer<Set<ArchiveEntry>, File> archiveToEntriesTransformer) {
        this.archiveToEntriesTransformer = archiveToEntriesTransformer;
    }

    public Class<GeneratedArchiveBuildOutcome> getComparedType() {
        return GeneratedArchiveBuildOutcome.class;
    }

    public GeneratedArchiveBuildOutcomeComparisonResult compare(BuildOutcomeAssociation<GeneratedArchiveBuildOutcome> association) {
        GeneratedArchiveBuildOutcome source = association.getSource();
        GeneratedArchiveBuildOutcome target = association.getTarget();

        Set<ArchiveEntry> sourceEntries;
        if (source.getArchiveFile() != null && source.getArchiveFile().exists()) {
            sourceEntries = archiveToEntriesTransformer.transform(source.getArchiveFile());
        } else {
            sourceEntries = Collections.emptySet();
        }

        Set<ArchiveEntry> targetEntries;
        if (target.getArchiveFile() != null && target.getArchiveFile().exists()) {
            targetEntries = archiveToEntriesTransformer.transform(target.getArchiveFile());
        } else {
            targetEntries = Collections.emptySet();
        }

        CollectionUtils.SetDiff<ArchiveEntry> diff = CollectionUtils.diffSetsBy(sourceEntries, targetEntries, new Transformer<ArchiveEntry.Path, ArchiveEntry>() {
            public ArchiveEntry.Path transform(ArchiveEntry entry) {
                return entry.getPath();
            }
        });

        SortedSet<ArchiveEntryComparison> entryComparisons = new TreeSet<ArchiveEntryComparison>();

        for (ArchiveEntry sourceOnly : diff.leftOnly) {
            entryComparisons.add(new ArchiveEntryComparison(sourceOnly, null));
        }

        for (Pair<ArchiveEntry, ArchiveEntry> pair : diff.common) {
            entryComparisons.add(new ArchiveEntryComparison(pair.left, pair.right));
        }

        for (ArchiveEntry targetOnly : diff.rightOnly) {
            entryComparisons.add(new ArchiveEntryComparison(null, targetOnly));
        }

        return new GeneratedArchiveBuildOutcomeComparisonResult(association, entryComparisons);
    }
}
