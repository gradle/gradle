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
     * Registers the serializer(s) that can be used to serialize the {@link FileCollectionSnapshot} implementations produced by this snapshotter.
     */
    void registerSerializers(SerializerRegistry registry);

    /**
     * Creates an empty snapshot, which changes can be later merged into.
     *
     * @return The snapshot.
     */
    FileCollectionSnapshot emptySnapshot();

    /**
     * Creates an actual snapshot based on the precheck information
     *
     * @param preCheck The precheck information returned from preCheck method
     * @return The snapshot.
     */
    FileCollectionSnapshot snapshot(FileCollectionSnapshot.PreCheck preCheck);

    /**
     * Creates the 1. phase of a snapshot of the contents of the given collection
     *
     * @param files The files to snapshot
     * @param allowReuse When true, can use cached snapshot trees
     * @return The snapshot precheck.
     */
    FileCollectionSnapshot.PreCheck preCheck(FileCollection files, boolean allowReuse);
}
