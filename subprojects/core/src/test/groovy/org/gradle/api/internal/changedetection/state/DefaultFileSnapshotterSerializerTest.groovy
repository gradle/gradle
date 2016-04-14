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
import org.gradle.cache.internal.MapBackedInMemoryStore
import org.gradle.internal.hash.HashUtil
import org.gradle.internal.serialize.SerializerSpec

class DefaultFileSnapshotterSerializerTest extends SerializerSpec {
    def stringInterner = new StringInterner()
    def treeSnapshotRepository = new TreeSnapshotRepository(new InMemoryCache(), stringInterner)
    def serializer = new DefaultFileSnapshotterSerializer(stringInterner, treeSnapshotRepository)

    def "reads and writes the snapshot"() {
        when:
        def hash = HashUtil.createHash("foo", "md5")
        FileCollectionSnapshotImpl out = serialize(new FileCollectionSnapshotImpl([
            "1": DirSnapshot.getInstance(),
            "2": MissingFileSnapshot.getInstance(),
            "3": new FileHashSnapshot(hash)]), serializer)

        then:
        out.snapshots.size() == 3
        out.snapshots['1'] instanceof DirSnapshot
        out.snapshots['2'] instanceof MissingFileSnapshot
        ((FileHashSnapshot) out.snapshots['3']).hash == hash
    }

    private static class InMemoryCache extends MapBackedInMemoryStore implements TaskArtifactStateCacheAccess {

    }
}
