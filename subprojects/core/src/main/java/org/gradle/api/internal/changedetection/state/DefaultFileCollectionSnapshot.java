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

import com.google.common.base.Objects;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.hash.HashCode;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.rules.TaskStateChange;
import org.gradle.caching.internal.BuildCacheHasher;
import org.gradle.internal.Factories;
import org.gradle.internal.Factory;
import org.gradle.internal.nativeintegration.filesystem.FileType;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.HashCodeSerializer;

import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class DefaultFileCollectionSnapshot implements FileCollectionSnapshot {
    private final Map<String, NormalizedFileSnapshot> snapshots;
    private final TaskFilePropertyCompareStrategy compareStrategy;

    private final HashCode hash;

    private final boolean pathIsAbsolute;
    private final Factory<List<File>> cachedElementsFactory = Factories.softReferenceCache(new Factory<List<File>>() {
        @Override
        public List<File> create() {
            return doGetElements();
        }
    });
    private final Factory<List<File>> cachedFilesFactory = Factories.softReferenceCache(new Factory<List<File>>() {
        @Override
        public List<File> create() {
            return doGetFiles();
        }
    });
    public DefaultFileCollectionSnapshot(Map<String, NormalizedFileSnapshot> snapshots, TaskFilePropertyCompareStrategy compareStrategy, boolean pathIsAbsolute, HashCode hash) {
        this.snapshots = snapshots;
        this.compareStrategy = compareStrategy;
        this.pathIsAbsolute = pathIsAbsolute;
        this.hash = hash;
    }

    @Override
    public Map<String, NormalizedFileSnapshot> getSnapshots() {
        return snapshots;
    }

    @Override
    public boolean isEmpty() {
        return snapshots.isEmpty();
    }

    @Override
    public Iterator<TaskStateChange> iterateContentChangesSince(FileCollectionSnapshot oldSnapshot, String fileType) {
        if (hash != null && oldSnapshot.getHash() != null && hash.equals(oldSnapshot.getHash())) {
            return Iterators.emptyIterator();
        }
        return compareStrategy.iterateContentChangesSince(snapshots, oldSnapshot.getSnapshots(), fileType, pathIsAbsolute);
    }

    @Override
    public void appendToHasher(BuildCacheHasher hasher) {
        hasher.putBytes(hash.asBytes());
    }

    @Override
    public HashCode getHash() {
        return hash;
    }

    @Override
    public List<File> getElements() {
        return cachedElementsFactory.create();
    }

    private List<File> doGetElements() {
        List<File> files = Lists.newArrayListWithCapacity(snapshots.size());
        for (String name : snapshots.keySet()) {
            files.add(new File(name));
        }
        return files;
    }

    @Override
    public List<File> getFiles() {
        return cachedFilesFactory.create();
    }

    private List<File> doGetFiles() {
        List<File> files = Lists.newArrayList();
        for (Map.Entry<String, NormalizedFileSnapshot> entry : snapshots.entrySet()) {
            if (entry.getValue().getSnapshot().getType() == FileType.RegularFile) {
                files.add(new File(entry.getKey()));
            }
        }
        return files;
    }

    public static class SerializerImpl extends AbstractSerializer<DefaultFileCollectionSnapshot> {
        private final SnapshotMapSerializer snapshotMapSerializer;
        private final HashCodeSerializer hashCodeSerializer;

        public SerializerImpl(StringInterner stringInterner) {
            this.snapshotMapSerializer = new SnapshotMapSerializer(stringInterner);
            this.hashCodeSerializer = new HashCodeSerializer();
        }

        public DefaultFileCollectionSnapshot read(Decoder decoder) throws Exception {
            TaskFilePropertyCompareStrategy compareStrategy = TaskFilePropertyCompareStrategy.values()[decoder.readSmallInt()];
            Map<String, NormalizedFileSnapshot> snapshots = snapshotMapSerializer.read(decoder);

            boolean pathIsUnique = decoder.readBoolean();
            boolean hashIsPresent = decoder.readBoolean();
            HashCode hash = hashIsPresent ? hashCodeSerializer.read(decoder) : null;
            return new DefaultFileCollectionSnapshot(snapshots, compareStrategy, pathIsUnique, hash);
        }

        public void write(Encoder encoder, DefaultFileCollectionSnapshot value) throws Exception {
            encoder.writeSmallInt(value.compareStrategy.ordinal());
            snapshotMapSerializer.write(encoder, value.snapshots);
            encoder.writeBoolean(value.pathIsAbsolute);
            boolean hashPresent = value.hash != null;
            encoder.writeBoolean(hashPresent);
            if (hashPresent) {
                hashCodeSerializer.write(encoder, value.hash);
            }
        }

        @Override
        public boolean equals(Object obj) {
            if (!super.equals(obj)) {
                return false;
            }

            SerializerImpl rhs = (SerializerImpl) obj;
            return Objects.equal(snapshotMapSerializer, rhs.snapshotMapSerializer) && Objects.equal(hashCodeSerializer, rhs.hashCodeSerializer);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(super.hashCode(), snapshotMapSerializer, hashCodeSerializer);
        }
    }
}
