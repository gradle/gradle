/*
 * Copyright 2024 the original author or authors.
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

import org.gradle.internal.file.FileAccessTimeJournal;

import java.io.File;

/**
 * Deletes all cache entries regardless of their age and removes them from the journal.
 */
public class UnconditionalCacheCleanup extends AbstractTimeJournalAwareCacheCleanup {

    public UnconditionalCacheCleanup(FilesFinder eligibleFilesFinder, FileAccessTimeJournal journal) {
        super(eligibleFilesFinder, journal);
    }

    @Override
    protected boolean shouldDelete(File file) {
        return true;
    }
}
