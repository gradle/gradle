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

package org.gradle.api.internal.changedetection.state;

import org.gradle.api.internal.changedetection.rules.FileChange;
import org.gradle.api.internal.changedetection.rules.TaskStateChangeVisitor;
import org.gradle.api.internal.changedetection.state.mirror.PhysicalSnapshotVisitor;
import org.gradle.api.internal.changedetection.state.mirror.logical.CurrentFileCollectionFingerprint;
import org.gradle.api.internal.changedetection.state.mirror.logical.HistoricalFileCollectionFingerprint;
import org.gradle.caching.internal.BuildCacheHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hashing;

import java.util.Collections;
import java.util.Map;

public class EmptyFileCollectionSnapshot implements CurrentFileCollectionFingerprint, HistoricalFileCollectionFingerprint {
    public static final EmptyFileCollectionSnapshot INSTANCE = new EmptyFileCollectionSnapshot();

    private static final HashCode SIGNATURE = Hashing.md5().hashString(EmptyFileCollectionSnapshot.class.getName());

    private EmptyFileCollectionSnapshot() {
    }

    @Override
    public boolean visitChangesSince(FileCollectionSnapshot oldSnapshot, final String title, boolean includeAdded, TaskStateChangeVisitor visitor) {
        for (Map.Entry<String, NormalizedFileSnapshot> entry : oldSnapshot.getSnapshots().entrySet()) {
            if (!visitor.visitChange(FileChange.removed(entry.getKey(), title, entry.getValue().getType()))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public HashCode getHash() {
        return SIGNATURE;
    }

    @Override
    public Map<String, NormalizedFileSnapshot> getSnapshots() {
        return Collections.emptyMap();
    }

    @Override
    public void visitRoots(PhysicalSnapshotVisitor visitor) {
    }

    @Override
    public HistoricalFileCollectionFingerprint archive() {
        return this;
    }

    @Override
    public void appendToHasher(BuildCacheHasher hasher) {
        hasher.putHash(SIGNATURE);
    }

    @Override
    public String toString() {
        return "EMPTY";
    }
}
