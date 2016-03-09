/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.api.internal.changedetection.state

import org.gradle.api.internal.cache.StringInterner
import org.gradle.internal.serialize.SerializerSpec

class DefaultFileSnapshotterSerializerTest extends SerializerSpec {

    def serializer = new DefaultFileSnapshotterSerializer(new StringInterner())

    def "reads and writes the snapshot"() {
        when:
        AbstractFileCollectionSnapshotter.FileCollectionSnapshotImpl out = serialize(new AbstractFileCollectionSnapshotter.FileCollectionSnapshotImpl([
            "1": AbstractFileCollectionSnapshotter.DirSnapshot.getInstance(),
            "2": AbstractFileCollectionSnapshotter.MissingFileSnapshot.getInstance(),
            "3": new AbstractFileCollectionSnapshotter.FileHashSnapshot("foo".bytes)]), serializer)

        then:
        out.snapshots.size() == 3
        out.snapshots['1'] instanceof AbstractFileCollectionSnapshotter.DirSnapshot
        out.snapshots['2'] instanceof AbstractFileCollectionSnapshotter.MissingFileSnapshot
        ((AbstractFileCollectionSnapshotter.FileHashSnapshot) out.snapshots['3']).hash == "foo".bytes
    }
}
