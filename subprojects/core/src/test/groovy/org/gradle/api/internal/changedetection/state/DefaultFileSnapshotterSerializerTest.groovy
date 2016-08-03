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

import com.google.common.base.Charsets
import com.google.common.hash.Hashing
import org.gradle.api.internal.cache.StringInterner
import org.gradle.internal.serialize.SerializerSpec

class DefaultFileSnapshotterSerializerTest extends SerializerSpec {
    def stringInterner = new StringInterner()
    def serializer = new DefaultFileSnapshotterSerializer(stringInterner)

    def "reads and writes the snapshot"() {
        when:
        def hash = Hashing.md5().hashString("foo", Charsets.UTF_8)
        FileCollectionSnapshotImpl out = serialize(new FileCollectionSnapshotImpl([
            "1": DirSnapshot.getInstance(),
            "2": MissingFileSnapshot.getInstance(),
            "3": new FileHashSnapshot(hash)], TaskFilePropertyCompareType.UNORDERED), serializer)

        then:
        out.snapshots.size() == 3
        out.snapshots['1'] instanceof DirSnapshot
        out.snapshots['2'] instanceof MissingFileSnapshot
        ((FileHashSnapshot) out.snapshots['3']).hash == hash
    }
}
