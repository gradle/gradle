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
import org.gradle.internal.snapshot.CompleteDirectorySnapshot
import org.gradle.internal.snapshot.CompositeFileSystemSnapshot
import org.gradle.internal.snapshot.RegularFileSnapshot

import static org.gradle.internal.file.FileMetadata.AccessType.DIRECT
import static org.gradle.internal.file.FileMetadata.AccessType.VIA_SYMLINK
import static org.gradle.internal.file.impl.DefaultFileMetadata.file
import static org.gradle.internal.hash.HashCode.fromInt
import static org.gradle.internal.snapshot.FileSystemSnapshot.EMPTY

class FileSystemSnapshotSerializerTest extends SerializerSpec {
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
        def snapshots = new RegularFileSnapshot("/home/lptr/dev/one.txt", "one.txt", fromInt(1234), file(1, 1, DIRECT))

        when:
        def out = serialize(snapshots, serializer)

        then:
        out == snapshots
    }

    def "reads and writes directory snapshots"() {
        def snapshots =  new CompleteDirectorySnapshot("/home/lptr/dev", "dev", DIRECT, fromInt(1111), [])

        when:
        def out = serialize(snapshots, serializer)

        then:
        out == snapshots
    }

    def "reads and writes directory snapshot hierarchies"() {
        def snapshots = new CompleteDirectorySnapshot("/home/lptr/dev", "dev", DIRECT, fromInt(1111), [
            new RegularFileSnapshot("/home/lptr/dev/one.txt", "one.txt", fromInt(1234), file(1, 1, DIRECT)),
            new RegularFileSnapshot("/home/lptr/dev/two.txt", "two.txt", fromInt(4321), file(5, 28, DIRECT)),
            new CompleteDirectorySnapshot("/home/lptr/dev/empty", "empty", DIRECT, fromInt(2222), []),
            new CompleteDirectorySnapshot("/home/lptr/dev/sub", "sub", DIRECT, fromInt(3333), [
                new RegularFileSnapshot("/home/lptr/dev/sub/three.txt", "three.txt", fromInt(5678), file(2, 2, DIRECT)),
                new RegularFileSnapshot("/home/lptr/dev/sub/four.txt", "four.txt", fromInt(8765), file(4, 0, DIRECT)),
            ]),
            new RegularFileSnapshot("/home/lptr/dev/link", "link", fromInt(8765), file(4, 0, VIA_SYMLINK)),
        ])

        when:
        def out = serialize(snapshots, serializer)

        then:
        out == snapshots
    }

    def "reads and writes composite snapshots"() {
        def snapshots = CompositeFileSystemSnapshot.of([
            new CompleteDirectorySnapshot("/home/lptr/dev", "dev", DIRECT, fromInt(1111), []),
            new RegularFileSnapshot("/home/lptr/dev/one.txt", "one.txt", fromInt(1234), file(1, 1, DIRECT)),
        ])

        when:
        def out = serialize(snapshots, serializer)

        then:
        out == snapshots
    }
}
