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

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.rules.TaskStateChange;
import org.gradle.caching.internal.BuildCacheHasher;
import org.gradle.caching.internal.DefaultBuildCacheHasher;
import org.gradle.internal.Factories;
import org.gradle.internal.Factory;
import org.gradle.internal.file.FileType;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.HashCodeSerializer;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class DefaultFileCollectionSnapshot implements FileCollectionSnapshot {
    private final Map<String, NormalizedFileSnapshot> snapshots;
    private final TaskFilePropertyCompareStrategy compareStrategy;
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
    private HashCode hashCode;

    public DefaultFileCollectionSnapshot(Map<String, NormalizedFileSnapshot> snapshots, TaskFilePropertyCompareStrategy compareStrategy, boolean pathIsAbsolute) {
        this(snapshots, null, compareStrategy, pathIsAbsolute);
    }

    DefaultFileCollectionSnapshot(Map<String, NormalizedFileSnapshot> snapshots, @Nullable HashCode hashCode, TaskFilePropertyCompareStrategy compareStrategy, boolean pathIsAbsolute) {
        this.snapshots = snapshots;
        this.hashCode = hashCode;
        this.compareStrategy = compareStrategy;
        this.pathIsAbsolute = pathIsAbsolute;
    }

    @Override
    public Map<String, NormalizedFileSnapshot> getSnapshots() {
        return snapshots;
    }

    public Map<String, FileContentSnapshot> getContentSnapshots() {
        return Maps.transformValues(snapshots, new Function<NormalizedFileSnapshot, FileContentSnapshot>() {
            @Override
            public FileContentSnapshot apply(NormalizedFileSnapshot normalizedSnapshot) {
                return normalizedSnapshot.getSnapshot();
            }
        });
    }

    @Override
    public boolean isEmpty() {
        return snapshots.isEmpty();
    }

    @Override
    public Iterator<TaskStateChange> iterateContentChangesSince(FileCollectionSnapshot oldSnapshot, String fileType, boolean includeAdded) {
        if (includeAdded && hashCode != null && getHash().equals(oldSnapshot.getHash())) {
            return Iterators.emptyIterator();
        }
        return compareStrategy.iterateContentChangesSince(snapshots, oldSnapshot.getSnapshots(), fileType, pathIsAbsolute, includeAdded);
    }

    @Override
    public HashCode getHash() {
        if (hashCode == null) {
            DefaultBuildCacheHasher hasher = new DefaultBuildCacheHasher();
            compareStrategy.appendToHasher(hasher, snapshots.values());
            hashCode = hasher.hash();
        }
        return hashCode;
    }

    @Override
    public void appendToHasher(BuildCacheHasher hasher) {
        hasher.putHash(getHash());
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

    @Override
    public String toString() {
        return compareStrategy + (pathIsAbsolute ? " with absolute paths" : "") + ": " + snapshots;
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
            boolean hasHash = decoder.readBoolean();
            HashCode hash = hasHash ? hashCodeSerializer.read(decoder) : null;
            Map<String, NormalizedFileSnapshot> snapshots = snapshotMapSerializer.read(decoder);
            boolean pathIsUnique = decoder.readBoolean();
            return new DefaultFileCollectionSnapshot(snapshots, hash, compareStrategy, pathIsUnique);
        }

        public void write(Encoder encoder, DefaultFileCollectionSnapshot value) throws Exception {
            encoder.writeSmallInt(value.compareStrategy.ordinal());
            boolean hasHash = value.hashCode != null;
            encoder.writeBoolean(hasHash);
            if (hasHash) {
                hashCodeSerializer.write(encoder, value.getHash());
            }
            snapshotMapSerializer.write(encoder, value.snapshots);
            encoder.writeBoolean(value.pathIsAbsolute);
        }

        @Override
        public boolean equals(Object obj) {
            if (!super.equals(obj)) {
                return false;
            }

            SerializerImpl rhs = (SerializerImpl) obj;
            return Objects.equal(snapshotMapSerializer, rhs.snapshotMapSerializer);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(super.hashCode(), snapshotMapSerializer);
        }
    }
}
