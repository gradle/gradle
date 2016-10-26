/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.api.file.FileCollection;
import org.gradle.internal.serialize.SerializerRegistry;

public interface FileCollectionSnapshotter {
    /**
     * The type used to refer to this snapshotter in the {@link FileCollectionSnapshotterRegistry}.
     * Must be a super-type of the actual implementation.
     */
    Class<? extends FileCollectionSnapshotter> getRegisteredType();

    /**
     * Registers the serializer(s) that can be used to serialize the {@link FileCollectionSnapshot} implementations produced by this snapshotter.
     */
    void registerSerializers(SerializerRegistry registry);

    /**
     * Creates a snapshot of the contents of the given collection.
     *
     * @param files The files to snapshot.
     * @param compareStrategy How to compare this collection snapshot to others.
     * @param snapshotNormalizationStrategy How to normalize file snapshots.
     * @return The snapshot.
     */
    FileCollectionSnapshot snapshot(FileCollection files, TaskFilePropertyCompareStrategy compareStrategy, SnapshotNormalizationStrategy snapshotNormalizationStrategy);
}
