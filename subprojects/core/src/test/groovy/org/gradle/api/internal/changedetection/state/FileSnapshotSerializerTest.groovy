package org.gradle.api.internal.changedetection.state

import spock.lang.Specification

/**
 * By Szczepan Faber on 5/21/13
 */
class FileSnapshotSerializerTest extends Specification {

    def snapshot = new DefaultFileSnapshotter.FileCollectionSnapshotImpl(["hey": new DefaultFileSnapshotter.DirSnapshot()])
    def outputSnapshot = new OutputFilesSnapshotter.OutputFilesSnapshot(["foo": 1L, "bar": 2L], snapshot)

    def "handles default snapshots"() {
        def bytes = new ByteArrayOutputStream()
        new FileSnapshotSerializer().write(bytes, snapshot)

        when:
        DefaultFileSnapshotter.FileCollectionSnapshotImpl out = new FileSnapshotSerializer().read(new ByteArrayInputStream(bytes.toByteArray()))

        then:
        out.snapshots.size() == 1
        out.snapshots['hey'] instanceof DefaultFileSnapshotter.DirSnapshot
    }

    def "handles output snapshots"() {
        def bytes = new ByteArrayOutputStream()
        new FileSnapshotSerializer().write(bytes, outputSnapshot)

        when:
        OutputFilesSnapshotter.OutputFilesSnapshot out = new FileSnapshotSerializer().read(new ByteArrayInputStream(bytes.toByteArray()))

        then:
        out.rootFileIds == ["foo": 1L, "bar": 2L]
        DefaultFileSnapshotter.FileCollectionSnapshotImpl filesSnapshot = out.filesSnapshot
        filesSnapshot.snapshots.size() == 1
        filesSnapshot.snapshots['hey'] instanceof DefaultFileSnapshotter.DirSnapshot
    }
}
