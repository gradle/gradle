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

class FileSnapshotSerializerTest extends SerializerSpec {

    def snapshot = new DefaultFileSnapshotter.FileCollectionSnapshotImpl(["hey": new DefaultFileSnapshotter.DirSnapshot()])
    def outputSnapshot = new OutputFilesSnapshotter.OutputFilesSnapshot(["foo": 1L, "bar": 2L], snapshot)

    def "handles default snapshots"() {
        when:
        DefaultFileSnapshotter.FileCollectionSnapshotImpl out = serialize(snapshot, new FileSnapshotSerializer())

        then:
        out.snapshots.size() == 1
        out.snapshots['hey'] instanceof DefaultFileSnapshotter.DirSnapshot
    }

    def "handles output snapshots"() {
        when:
        OutputFilesSnapshotter.OutputFilesSnapshot out = serialize(outputSnapshot, new FileSnapshotSerializer())

        then:
        out.rootFileIds == ["foo": 1L, "bar": 2L]
        DefaultFileSnapshotter.FileCollectionSnapshotImpl filesSnapshot = out.filesSnapshot
        filesSnapshot.snapshots.size() == 1
        filesSnapshot.snapshots['hey'] instanceof DefaultFileSnapshotter.DirSnapshot
    }
}
