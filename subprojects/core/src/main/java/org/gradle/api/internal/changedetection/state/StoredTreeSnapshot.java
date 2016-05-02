/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.changedetection.state;

import com.google.common.collect.ImmutableList;
import org.gradle.api.Action;

import java.util.Collection;

class StoredTreeSnapshot implements TreeSnapshot {
    private final ImmutableList<FileSnapshotWithKey> fileSnapshotWithKeyList;
    private final long assignedId;

    public StoredTreeSnapshot(ImmutableList<FileSnapshotWithKey> fileSnapshotWithKeyList, long assignedId) {
        this.fileSnapshotWithKeyList = fileSnapshotWithKeyList;
        this.assignedId = assignedId;
    }

    @Override
    public boolean isShareable() {
        return assignedId != -1;
    }

    @Override
    public Collection<FileSnapshotWithKey> getFileSnapshots() {
        return fileSnapshotWithKeyList;
    }

    @Override
    public Long getAssignedId() {
        return assignedId;
    }

    @Override
    public Long maybeStoreEntry(Action<Long> storeEntryAction) {
        return assignedId;
    }
}
