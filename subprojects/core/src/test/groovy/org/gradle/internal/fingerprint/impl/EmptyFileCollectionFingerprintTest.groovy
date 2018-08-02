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
import org.gradle.api.internal.changedetection.rules.CollectingTaskStateChangeVisitor
import org.gradle.api.internal.changedetection.rules.FileChange
import org.gradle.api.internal.changedetection.rules.TaskStateChange
import org.gradle.api.internal.changedetection.state.DefaultNormalizedFileSnapshot
import org.gradle.internal.file.FileType
import org.gradle.internal.fingerprint.FileCollectionFingerprint
import org.gradle.internal.fingerprint.FingerprintingStrategy
import org.gradle.internal.hash.HashCode
import spock.lang.Specification
import spock.lang.Unroll

@Unroll
class EmptyFileCollectionFingerprintTest extends Specification {

    private static final List<FileCollectionFingerprint> EMPTY_FINGERPRINTS = [
        EmptyHistoricalFileCollectionFingerprint.INSTANCE,
        *FingerprintingStrategy.Identifier.values()*.emptyFingerprint
    ]

    def "comparing empty snapshot to regular snapshot shows entries added"() {
        def fingerprint = new DefaultHistoricalFileCollectionFingerprint([
            "file1.txt": new DefaultNormalizedFileSnapshot("file1.txt", FileType.RegularFile, HashCode.fromInt(123)),
            "file2.txt": new DefaultNormalizedFileSnapshot("file2.txt", FileType.RegularFile, HashCode.fromInt(234)),
        ], FingerprintCompareStrategy.ABSOLUTE, ImmutableMultimap.of('/dir', HashCode.fromInt(456)))
        expect:
        getChanges(fingerprint, empty, false).empty
        getChanges(fingerprint, empty, true) == [
            FileChange.added("file1.txt", "test", FileType.RegularFile),
            FileChange.added("file2.txt", "test", FileType.RegularFile)
        ]

        where:
        empty << EMPTY_FINGERPRINTS
    }

    def "comparing regular snapshot to empty snapshot shows entries removed"() {
        def fingerprint = new DefaultHistoricalFileCollectionFingerprint([
            "file1.txt": new DefaultNormalizedFileSnapshot("file1.txt", FileType.RegularFile, HashCode.fromInt(123)),
            "file2.txt": new DefaultNormalizedFileSnapshot("file2.txt", FileType.RegularFile, HashCode.fromInt(234)),
        ], FingerprintCompareStrategy.ABSOLUTE, ImmutableMultimap.of('/dir', HashCode.fromInt(456)))
        expect:
        getChanges(empty, fingerprint, false).toList() == [
            FileChange.removed("file1.txt", "test", FileType.RegularFile),
            FileChange.removed("file2.txt", "test", FileType.RegularFile)
        ]
        getChanges(empty, fingerprint, true).toList() == [
            FileChange.removed("file1.txt", "test", FileType.RegularFile),
            FileChange.removed("file2.txt", "test", FileType.RegularFile)
        ]

        where:
        empty << EMPTY_FINGERPRINTS
    }

    def "comparing empty fingerprints always produces empty - #firstEmpty / #secondEmpty"() {
        expect:
        getChanges(firstEmpty, secondEmpty, false).toList() == []
        getChanges(firstEmpty, secondEmpty, true).toList() == []

        where:
        combination << [EMPTY_FINGERPRINTS, EMPTY_FINGERPRINTS].combinations()
        firstEmpty = combination[0]
        secondEmpty = combination[1]
    }

    private static Collection<TaskStateChange> getChanges(FileCollectionFingerprint current, FileCollectionFingerprint previous, boolean includeAdded) {
        def visitor = new CollectingTaskStateChangeVisitor()
        current.visitChangesSince(previous, "test", includeAdded, visitor)
        visitor.changes
    }
}
