/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.fingerprint.impl

import com.google.common.collect.ImmutableMultimap
import org.gradle.internal.change.Change
import org.gradle.internal.change.CollectingChangeVisitor
import org.gradle.internal.change.DefaultFileChange
import org.gradle.internal.file.FileType
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint
import org.gradle.internal.fingerprint.FileCollectionFingerprint
import org.gradle.internal.hash.HashCode
import spock.lang.Specification
import spock.lang.Unroll

@Unroll
class EmptyCurrentFileCollectionFingerprintTest extends Specification {

    def empty = new EmptyCurrentFileCollectionFingerprint("test")

    def "comparing regular snapshot to empty snapshot shows entries removed (include added: #includeAdded)"() {
        def fingerprint = Mock(FileCollectionFingerprint) {
            getFingerprints() >> [
                "file1.txt": new DefaultFileSystemLocationFingerprint("file1.txt", FileType.RegularFile, HashCode.fromInt(123)),
                "file2.txt": new DefaultFileSystemLocationFingerprint("file2.txt", FileType.RegularFile, HashCode.fromInt(234)),
            ]
            getRootHashes() >> ImmutableMultimap.of('/dir', HashCode.fromInt(456))
        }
        expect:
        getChanges(fingerprint, empty, includeAdded).toList() == [
            DefaultFileChange.removed("file1.txt", "test", FileType.RegularFile, "file1.txt"),
            DefaultFileChange.removed("file2.txt", "test", FileType.RegularFile, "file2.txt")
        ]

        where:
        includeAdded << [true, false]
    }

    def "comparing empty fingerprints produces empty (include added: #includeAdded)"() {
        expect:
        getChanges(empty, empty, includeAdded).empty

        where:
        includeAdded << [true, false]
    }

    private static Collection<Change> getChanges(FileCollectionFingerprint previous, CurrentFileCollectionFingerprint current, boolean includeAdded) {
        def visitor = new CollectingChangeVisitor()
        current.visitChangesSince(previous, "test", includeAdded, visitor)
        visitor.changes
    }
}
