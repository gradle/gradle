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

import com.google.common.base.Function;
import com.google.common.collect.Iterators;
import org.gradle.api.internal.changedetection.rules.FileChange;
import org.gradle.api.internal.changedetection.rules.TaskStateChange;
import org.gradle.api.internal.changedetection.rules.TaskStateChangeVisitor;
import org.gradle.caching.internal.BuildCacheHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hashing;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

public class EmptyFileCollectionSnapshot implements FileCollectionSnapshot {
    public static final EmptyFileCollectionSnapshot INSTANCE = new EmptyFileCollectionSnapshot();

    private static final HashCode SIGNATURE = Hashing.md5().hashString(EmptyFileCollectionSnapshot.class.getName());

    private EmptyFileCollectionSnapshot() {
    }

    @Override
    public Iterator<TaskStateChange> iterateContentChangesSince(FileCollectionSnapshot oldSnapshot, final String title, boolean includeAdded) {
        return Iterators.transform(oldSnapshot.getContentSnapshots().entrySet().iterator(), new Function<Map.Entry<String, FileContentSnapshot>, TaskStateChange>() {
            @Override
            public TaskStateChange apply(Map.Entry<String, FileContentSnapshot> entry) {
                return FileChange.removed(entry.getKey(), title, entry.getValue().getType());
            }
        });
    }

    @Override
    public boolean accept(FileCollectionSnapshot oldSnapshot, final String title, boolean includeAdded, TaskStateChangeVisitor visitor) {
        for (Map.Entry<String, FileContentSnapshot> entry : oldSnapshot.getContentSnapshots().entrySet()) {
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
    public Collection<File> getElements() {
        return Collections.emptySet();
    }

    @Override
    public Map<String, NormalizedFileSnapshot> getSnapshots() {
        return Collections.emptyMap();
    }

    @Override
    public Map<String, FileContentSnapshot> getContentSnapshots() {
        return Collections.emptyMap();
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
