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

import org.gradle.messaging.serialize.SerializerSpec

class OutputFilesSnapshotSerializerTest extends SerializerSpec {

    def serializer = new OutputFilesSnapshotSerializer()

    def "reads and writes the snapshot"() {
        def snapshot = new DefaultFileSnapshotter.FileCollectionSnapshotImpl(["1": new DefaultFileSnapshotter.DirSnapshot()])
        def outputSnapshot = new OutputFilesSnapshotter.OutputFilesSnapshot(["x": 14L], snapshot)

        when:
        OutputFilesSnapshotter.OutputFilesSnapshot out = serialize(outputSnapshot, serializer)

        then:
        ((DefaultFileSnapshotter.FileCollectionSnapshotImpl)out.filesSnapshot).snapshots.size() == 1
        out.rootFileIds == ['x': 14L]
    }
}
