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
