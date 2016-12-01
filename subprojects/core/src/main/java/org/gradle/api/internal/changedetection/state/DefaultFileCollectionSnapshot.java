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

import com.google.common.collect.Lists;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.rules.TaskStateChange;
import org.gradle.caching.internal.BuildCacheKeyBuilder;
import org.gradle.internal.Factories;
import org.gradle.internal.Factory;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;

import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

class DefaultFileCollectionSnapshot implements FileCollectionSnapshot {
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

    public DefaultFileCollectionSnapshot(Map<String, NormalizedFileSnapshot> snapshots, TaskFilePropertyCompareStrategy compareStrategy, boolean pathIsAbsolute) {
        this.snapshots = snapshots;
        this.compareStrategy = compareStrategy;
        this.pathIsAbsolute = pathIsAbsolute;
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
        return compareStrategy.iterateContentChangesSince(snapshots, oldSnapshot.getSnapshots(), fileType, pathIsAbsolute);
    }

    @Override
    public void appendToCacheKey(BuildCacheKeyBuilder builder) {
        compareStrategy.appendToCacheKey(builder, snapshots);
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
            if (entry.getValue().getSnapshot() instanceof FileHashSnapshot) {
                files.add(new File(entry.getKey()));
            }
        }
        return files;
    }

    public static class SerializerImpl implements Serializer<DefaultFileCollectionSnapshot> {
        private final SnapshotMapSerializer snapshotMapSerializer;

        public SerializerImpl(StringInterner stringInterner) {
            this.snapshotMapSerializer = new SnapshotMapSerializer(stringInterner);
        }

        public DefaultFileCollectionSnapshot read(Decoder decoder) throws Exception {
            TaskFilePropertyCompareStrategy compareStrategy = TaskFilePropertyCompareStrategy.values()[decoder.readSmallInt()];
            Map<String, NormalizedFileSnapshot> snapshots = snapshotMapSerializer.read(decoder);
            boolean pathIsUnique = decoder.readBoolean();
            return new DefaultFileCollectionSnapshot(snapshots, compareStrategy, pathIsUnique);
        }

        public void write(Encoder encoder, DefaultFileCollectionSnapshot value) throws Exception {
            encoder.writeSmallInt(value.compareStrategy.ordinal());
            snapshotMapSerializer.write(encoder, value.snapshots);
            encoder.writeBoolean(value.pathIsAbsolute);
        }
    }
}
