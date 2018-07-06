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

package org.gradle.internal.file.fingerprint.internal;

import org.gradle.api.internal.changedetection.rules.FileChange;
import org.gradle.api.internal.changedetection.rules.TaskStateChangeVisitor;
import org.gradle.caching.internal.BuildCacheHasher;
import org.gradle.internal.file.content.NormalizedFileSnapshot;
import org.gradle.internal.file.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.file.physical.PhysicalSnapshotVisitor;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hashing;

import java.util.Collections;
import java.util.Map;

public class EmptyFileCollectionFingerprint implements FileCollectionFingerprint {
    public static final EmptyFileCollectionFingerprint INSTANCE = new EmptyFileCollectionFingerprint();

    private static final HashCode SIGNATURE = Hashing.md5().hashString(EmptyFileCollectionFingerprint.class.getName());

    private EmptyFileCollectionFingerprint() {
    }

    @Override
    public boolean visitChangesSince(FileCollectionFingerprint oldFingerprint, final String title, boolean includeAdded, TaskStateChangeVisitor visitor) {
        for (Map.Entry<String, NormalizedFileSnapshot> entry : oldFingerprint.getSnapshots().entrySet()) {
            if (!visitor.visitChange(FileChange.removed(entry.getKey(), title, entry.getValue().getSnapshot().getType()))) {
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
    public void appendToHasher(BuildCacheHasher hasher) {
        hasher.putHash(SIGNATURE);
    }

    @Override
    public String toString() {
        return "EMPTY";
    }
}
