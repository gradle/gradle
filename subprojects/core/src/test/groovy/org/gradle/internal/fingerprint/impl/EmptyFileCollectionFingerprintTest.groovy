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

import org.gradle.api.internal.changedetection.rules.CollectingTaskStateChangeVisitor
import org.gradle.api.internal.changedetection.rules.FileChange
import org.gradle.api.internal.changedetection.rules.TaskStateChange
import org.gradle.api.internal.changedetection.state.DefaultNormalizedFileSnapshot
import org.gradle.internal.file.FileType
import org.gradle.internal.fingerprint.FileCollectionFingerprint
import org.gradle.internal.hash.HashCode
import spock.lang.Specification

class EmptyFileCollectionFingerprintTest extends Specification {
    def "comparing empty snapshot to regular snapshot shows entries added"() {
        def fingerprint = new DefaultHistoricalFileCollectionFingerprint([
            "file1.txt": new DefaultNormalizedFileSnapshot("file1.txt", FileType.RegularFile, HashCode.fromInt(123)),
            "file2.txt": new DefaultNormalizedFileSnapshot("file2.txt", FileType.RegularFile, HashCode.fromInt(234)),
        ], FingerprintCompareStrategy.ABSOLUTE)
        expect:
        getChanges(fingerprint, EmptyFileCollectionFingerprint.INSTANCE, false).empty
        getChanges(fingerprint, EmptyFileCollectionFingerprint.INSTANCE, true) == [
            FileChange.added("file1.txt", "test", FileType.RegularFile),
            FileChange.added("file2.txt", "test", FileType.RegularFile)
        ]
    }

    def "comparing regular snapshot to empty snapshot shows entries removed"() {
        def fingerprint = new DefaultHistoricalFileCollectionFingerprint([
            "file1.txt": new DefaultNormalizedFileSnapshot("file1.txt", FileType.RegularFile, HashCode.fromInt(123)),
            "file2.txt": new DefaultNormalizedFileSnapshot("file2.txt", FileType.RegularFile, HashCode.fromInt(234)),
        ], FingerprintCompareStrategy.ABSOLUTE)
        expect:
        getChanges(EmptyFileCollectionFingerprint.INSTANCE, fingerprint, false).toList() == [
            FileChange.removed("file1.txt", "test", FileType.RegularFile),
            FileChange.removed("file2.txt", "test", FileType.RegularFile)
        ]
        getChanges(EmptyFileCollectionFingerprint.INSTANCE, fingerprint, true).toList() == [
            FileChange.removed("file1.txt", "test", FileType.RegularFile),
            FileChange.removed("file2.txt", "test", FileType.RegularFile)
        ]
    }

    def "comparing to itself works"() {
        expect:
        getChanges(EmptyFileCollectionFingerprint.INSTANCE, EmptyFileCollectionFingerprint.INSTANCE, false).toList() == []
        getChanges(EmptyFileCollectionFingerprint.INSTANCE, EmptyFileCollectionFingerprint.INSTANCE, true).toList() == []
    }

    private static Collection<TaskStateChange> getChanges(FileCollectionFingerprint current, FileCollectionFingerprint previous, boolean includeAdded) {
        def visitor = new CollectingTaskStateChangeVisitor()
        current.visitChangesSince(previous, "test", includeAdded, visitor)
        visitor.changes
    }
}
