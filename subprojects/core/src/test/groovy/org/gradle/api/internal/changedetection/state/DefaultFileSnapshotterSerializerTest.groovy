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

import spock.lang.Specification

/**
 * By Szczepan Faber on 5/21/13
 */
class DefaultFileSnapshotterSerializerTest extends Specification {

    def serializer = new DefaultFileSnapshotterSerializer()

    def "reads and writes the snapshot"() {
        def bytes = new ByteArrayOutputStream()
        serializer.write(bytes, new DefaultFileSnapshotter.FileCollectionSnapshotImpl([
                "1": new DefaultFileSnapshotter.DirSnapshot(),
                "2": new DefaultFileSnapshotter.MissingFileSnapshot(),
                "3": new DefaultFileSnapshotter.FileHashSnapshot("foo".bytes)]))

        when:
        DefaultFileSnapshotter.FileCollectionSnapshotImpl out = serializer.read(new ByteArrayInputStream(bytes.toByteArray()))

        then:
        out.snapshots.size() == 3
        out.snapshots['1'] instanceof DefaultFileSnapshotter.DirSnapshot
        out.snapshots['2'] instanceof DefaultFileSnapshotter.MissingFileSnapshot
        ((DefaultFileSnapshotter.FileHashSnapshot) out.snapshots['3']).hash == "foo".bytes
    }
}
