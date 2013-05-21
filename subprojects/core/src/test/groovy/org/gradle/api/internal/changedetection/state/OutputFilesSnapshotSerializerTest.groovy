package org.gradle.api.internal.changedetection.state

import spock.lang.Specification

/**
 * By Szczepan Faber on 5/21/13
 */
class OutputFilesSnapshotSerializerTest extends Specification {

    def serializer = new OutputFilesSnapshotSerializer()

    def "reads and writes the snapshot"() {
        def bytes = new ByteArrayOutputStream()
        def snapshot = new DefaultFileSnapshotter.FileCollectionSnapshotImpl(["1": new DefaultFileSnapshotter.DirSnapshot()])
        def outputSnapshot = new OutputFilesSnapshotter.OutputFilesSnapshot(["x": 14L], snapshot)
        serializer.write(bytes, outputSnapshot)

        when:
        OutputFilesSnapshotter.OutputFilesSnapshot out = serializer.read(new ByteArrayInputStream(bytes.toByteArray()))

        then:
        ((DefaultFileSnapshotter.FileCollectionSnapshotImpl)out.filesSnapshot).snapshots.size() == 1
        out.rootFileIds == ['x': 14L]
    }
}
