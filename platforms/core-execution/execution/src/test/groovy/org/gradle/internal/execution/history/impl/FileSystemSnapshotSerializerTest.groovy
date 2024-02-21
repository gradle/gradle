/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.execution.history.impl

import org.gradle.api.internal.cache.StringInterner
import org.gradle.internal.serialize.AbstractEncoder
import org.gradle.internal.serialize.SerializerSpec
import org.gradle.internal.snapshot.CompositeFileSystemSnapshot
import org.gradle.internal.snapshot.DirectorySnapshot
import org.gradle.internal.snapshot.FileSystemSnapshot
import org.gradle.internal.snapshot.MissingFileSnapshot
import org.gradle.internal.snapshot.RegularFileSnapshot
import org.gradle.internal.snapshot.TestSnapshotFixture

import static org.gradle.internal.file.FileMetadata.AccessType.DIRECT
import static org.gradle.internal.file.FileMetadata.AccessType.VIA_SYMLINK
import static org.gradle.internal.file.impl.DefaultFileMetadata.file
import static org.gradle.internal.hash.TestHashCodes.hashCodeFrom
import static org.gradle.internal.snapshot.FileSystemSnapshot.EMPTY
import static org.gradle.internal.snapshot.SnapshotUtil.indexByAbsolutePath

class FileSystemSnapshotSerializerTest extends SerializerSpec implements TestSnapshotFixture {
    def stringInterner = new StringInterner()
    def serializer = new FileSystemSnapshotSerializer(stringInterner)

    @Override
    Class<? extends AbstractEncoder> getEncoder() {
        return super.getEncoder()
    }

    def "reads and writes empty snapshots"() {
        when:
        def out = serialize(EMPTY, serializer)

        then:
        out == EMPTY
    }

    def "reads and writes file snapshots"() {
        def snapshots = new RegularFileSnapshot("/home/lptr/dev/one.txt", "one.txt", hashCodeFrom(1234), file(1, 1, DIRECT))

        when:
        def out = serialize(snapshots, serializer)

        then:
        assertEqualSnapshots(out, snapshots)
    }

    def "reads and writes directory snapshots"() {
        def snapshots =  new DirectorySnapshot("/home/lptr/dev", "dev", DIRECT, hashCodeFrom(1111), [])

        when:
        def out = serialize(snapshots, serializer)

        then:
        assertEqualSnapshots(out, snapshots)
    }

    def "reads and writes missing snapshots"() {
        def snapshots = new MissingFileSnapshot("/home/lptr/dev/one.txt", "one.txt", DIRECT)

        when:
        def out = serialize(snapshots, serializer)

        then:
        assertEqualSnapshots(out, snapshots)
    }

    def "reads and writes directory snapshot hierarchies"() {
        def snapshots = directory("/home/lptr/dev", [
            regularFile("/home/lptr/dev/one.txt"),
            regularFile("/home/lptr/dev/two.txt"),
            directory("/home/lptr/dev/empty", []),
            directory("/home/lptr/dev/sub", [
                regularFile("/home/lptr/dev/sub/three.txt"),
                regularFile("/home/lptr/dev/sub/four.txt"),
            ]),
            regularFile("/home/lptr/dev/link", VIA_SYMLINK),
        ])

        when:
        def out = serialize(snapshots, serializer)

        then:
        assertEqualSnapshots(out, snapshots)
    }

    def "reads and writes composite snapshots"() {
        def snapshots = CompositeFileSystemSnapshot.of([
            directory("/home/lptr/dev", []),
            regularFile("/home/lptr/dev/one.txt"),
        ])

        when:
        def out = serialize(snapshots, serializer)

        then:
        assertEqualSnapshots(out, snapshots)
    }

    private static void assertEqualSnapshots(FileSystemSnapshot snapshot, FileSystemSnapshot expected) {
        assert snapshot == expected
        assert indexByAbsolutePath(snapshot) == indexByAbsolutePath(expected)
    }
}
