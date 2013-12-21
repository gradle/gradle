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

package org.gradle.api.plugins.buildcomparison.outcome.internal.archive.entry;

import org.gradle.api.plugins.buildcomparison.compare.internal.ComparisonResultType;

public class ArchiveEntryComparison {

    private final String path;
    private final ArchiveEntry source;
    private final ArchiveEntry target;

    public ArchiveEntryComparison(String path, ArchiveEntry source, ArchiveEntry target) {
        if (source == null && target == null) {
            throw new IllegalArgumentException("Both 'from' and 'to' cannot be null");
        }

        this.path = path;
        this.source = source;
        this.target = target;
    }

    public ComparisonResultType getComparisonResultType() {
        if (source != null && target == null) {
            return ComparisonResultType.SOURCE_ONLY;
        } else if (source == null && target != null) {
            return ComparisonResultType.TARGET_ONLY;
        } else {
            //noinspection ConstantConditions
            return source.equals(target) ? ComparisonResultType.EQUAL : ComparisonResultType.UNEQUAL;
        }
    }

    public String getPath() {
        return path;
    }

    public ArchiveEntry getSource() {
        return source;
    }

    public ArchiveEntry getTarget() {
        return target;
    }
}
