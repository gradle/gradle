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

import org.gradle.cache.CleanupProgressMonitor;
import org.gradle.internal.operations.BuildOperationContext;

public class DefaultCleanupProgressMonitor implements CleanupProgressMonitor {

    private final BuildOperationContext buildOperationContext;
    private long deleted;
    private long skipped;

    public DefaultCleanupProgressMonitor(BuildOperationContext buildOperationContext) {
        this.buildOperationContext = buildOperationContext;
    }

    @Override
    public void incrementDeleted() {
        deleted++;
        updateProgress();
    }

    @Override
    public void incrementSkipped() {
        incrementSkipped(1);
    }

    @Override
    public void incrementSkipped(long amount) {
        skipped += amount;
        updateProgress();
    }

    public long getDeleted() {
        return deleted;
    }

    private void updateProgress() {
        buildOperationContext.progress(mandatoryNumber(deleted, " entry", " entries") + " deleted"
            + optionalNumber(", ", skipped, " skipped"));
    }

    private static String mandatoryNumber(long value, String singular, String plural) {
        return value == 1 ? value + singular : value + plural;
    }

    private static String optionalNumber(String separator, long value, String description) {
        return value == 0 ? "" : separator + value + description;
    }
}
