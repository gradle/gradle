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

import org.gradle.internal.serialize.Serializer
import org.gradle.internal.serialize.SerializerSpec

class OutputFilesSnapshotSerializerTest extends SerializerSpec {
    def targetSerializer = Mock(Serializer)
    def serializer = new OutputFilesSnapshotSerializer(targetSerializer)

    def "reads and writes the snapshot"() {
        def snapshot = Stub(FileCollectionSnapshot)
        def outputSnapshot = new OutputFilesCollectionSnapshotter.OutputFilesSnapshot(["x": 14L], snapshot)

        given:
        1 * targetSerializer.write(_, snapshot)
        1 * targetSerializer.read(_) >> snapshot

        when:
        OutputFilesCollectionSnapshotter.OutputFilesSnapshot out = serialize(outputSnapshot, serializer)

        then:
        out.rootFileIds == ['x': 14L]
        out.filesSnapshot == snapshot
    }
}
