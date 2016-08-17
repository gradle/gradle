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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Iterators;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.rules.ChangeType;
import org.gradle.api.internal.changedetection.rules.FileChange;
import org.gradle.api.internal.changedetection.rules.TaskStateChange;
import org.gradle.api.internal.tasks.cache.TaskCacheKeyBuilder;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class OutputFilesCollectionSnapshot implements FileCollectionSnapshot {
    private final Map<String, Boolean> roots;
    private final FileCollectionSnapshot filesSnapshot;

    OutputFilesCollectionSnapshot(Map<String, Boolean> roots, FileCollectionSnapshot filesSnapshot) {
        this.roots = roots;
        this.filesSnapshot = filesSnapshot;
    }

    public Collection<File> getFiles() {
        return filesSnapshot.getFiles();
    }

    @Override
    public Map<String, NormalizedFileSnapshot> getSnapshots() {
        return filesSnapshot.getSnapshots();
    }

    @Override
    public boolean isEmpty() {
        return filesSnapshot.isEmpty();
    }

    @Override
    public Iterator<TaskStateChange> iterateContentChangesSince(FileCollectionSnapshot oldSnapshot, String fileType) {
        final OutputFilesCollectionSnapshot other = (OutputFilesCollectionSnapshot) oldSnapshot;
        final Iterator<TaskStateChange> rootFileIdIterator = iterateRootFileIdChanges(other, fileType);
        final Iterator<TaskStateChange> fileIterator = filesSnapshot.iterateContentChangesSince(other.filesSnapshot, fileType);
        return Iterators.concat(rootFileIdIterator, fileIterator);
    }

    @VisibleForTesting
    Map<String, Boolean> getRoots() {
        return roots;
    }

    FileCollectionSnapshot getFilesSnapshot() {
        return filesSnapshot;
    }

    private Iterator<TaskStateChange> iterateRootFileIdChanges(final OutputFilesCollectionSnapshot other, final String fileType) {
        Map<String, Boolean> added = new HashMap<String, Boolean>(roots);
        added.keySet().removeAll(other.roots.keySet());
        final Iterator<String> addedIterator = added.keySet().iterator();

        Map<String, Boolean> removed = new HashMap<String, Boolean>(other.roots);
        removed.keySet().removeAll(roots.keySet());
        final Iterator<String> removedIterator = removed.keySet().iterator();

        Set<String> changed = new HashSet<String>();
        for (Map.Entry<String, Boolean> current : roots.entrySet()) {
            Boolean otherValue = other.roots.get(current.getKey());
            // Only care about roots that used to exist and have been removed
            if (otherValue != null && otherValue && !otherValue.equals(current.getValue())) {
                changed.add(current.getKey());
            }
        }
        final Iterator<String> changedIterator = changed.iterator();

        return new AbstractIterator<TaskStateChange>() {
            @Override
            protected TaskStateChange computeNext() {
                if (addedIterator.hasNext()) {
                    return new FileChange(addedIterator.next(), ChangeType.ADDED, fileType);
                }
                if (removedIterator.hasNext()) {
                    return new FileChange(removedIterator.next(), ChangeType.REMOVED, fileType);
                }
                if (changedIterator.hasNext()) {
                    return new FileChange(changedIterator.next(), ChangeType.MODIFIED, fileType);
                }

                return endOfData();
            }
        };
    }

    @Override
    public void appendToCacheKey(TaskCacheKeyBuilder builder) {
        filesSnapshot.appendToCacheKey(builder);
    }

    public static class SerializerImpl implements Serializer<OutputFilesCollectionSnapshot> {
        private final Serializer<FileCollectionSnapshot> serializer;
        private final StringInterner stringInterner;

        public SerializerImpl(Serializer<FileCollectionSnapshot> serializer, StringInterner stringInterner) {
            this.serializer = serializer;
            this.stringInterner = stringInterner;
        }

        public OutputFilesCollectionSnapshot read(Decoder decoder) throws Exception {
            Map<String, Boolean> roots = new HashMap<String, Boolean>();
            int rootFileIdsCount = decoder.readSmallInt();
            for (int i = 0; i < rootFileIdsCount; i++) {
                String path = stringInterner.intern(decoder.readString());
                boolean exists = decoder.readBoolean();
                roots.put(path, exists);
            }
            FileCollectionSnapshot snapshot = serializer.read(decoder);
            return new OutputFilesCollectionSnapshot(roots, snapshot);
        }

        public void write(Encoder encoder, OutputFilesCollectionSnapshot value) throws Exception {
            int roots = value.roots.size();
            encoder.writeSmallInt(roots);
            for (Map.Entry<String, Boolean> entry : value.roots.entrySet()) {
                String path = entry.getKey();
                Boolean exists = entry.getValue();
                encoder.writeString(path);
                encoder.writeBoolean(exists);
            }
            serializer.write(encoder, value.filesSnapshot);
        }
    }
}
