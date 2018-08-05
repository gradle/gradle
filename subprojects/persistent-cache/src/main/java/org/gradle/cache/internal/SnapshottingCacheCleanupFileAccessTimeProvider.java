/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.cache.internal;

import org.gradle.internal.resource.local.FileAccessTimeJournal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SnapshottingCacheCleanupFileAccessTimeProvider implements CacheCleanupFileAccessTimeProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(SnapshottingCacheCleanupFileAccessTimeProvider.class);

    private final List<File> deletedFiles = new ArrayList<File>();
    private final FileAccessTimeJournal journal;
    private FileAccessTimeJournal.Snapshot snapshot;

    public SnapshottingCacheCleanupFileAccessTimeProvider(FileAccessTimeJournal journal) {
        this.journal = journal;
    }

    @Override
    public long getLastAccessTime(File file) {
        return getSnapshot().getLastAccessTime(file);
    }

    @Override
    public void deleteLastAccessTime(File file) {
        deletedFiles.add(file);
    }

    @Override
    public void close() {
        closeSnapshot();
        bulkDeleteAccessTimes();
    }

    private FileAccessTimeJournal.Snapshot getSnapshot() {
        if (snapshot == null) {
            snapshot = journal.createSnapshot();
        }
        return snapshot;
    }

    private void closeSnapshot() {
        if (snapshot == null) {
            return;
        }
        try {
            snapshot.close();
        } catch (Exception e) {
            LOGGER.debug("Failed to close snapshot of file access time journal", e);
        } finally {
            snapshot = null;
        }
    }

    private void bulkDeleteAccessTimes() {
        for (File file : deletedFiles) {
            journal.deleteLastAccessTime(file);
        }
        deletedFiles.clear();
    }
}
