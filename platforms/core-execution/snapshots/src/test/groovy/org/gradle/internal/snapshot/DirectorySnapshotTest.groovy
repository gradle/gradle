/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.snapshot

import org.gradle.internal.file.FileMetadata
import org.gradle.internal.file.impl.DefaultFileMetadata
import org.gradle.internal.hash.TestHashCodes

class DirectorySnapshotTest extends AbstractFileSystemLocationSnapshotTest {
    def "can be relocated"() {
        def testDirectory = temporaryFolder.createDir("test")
        def sourceDir = testDirectory.createDir("source-directory")
        def childDir = sourceDir.createDir("child")
        def childFile = childDir.createFile("child.txt")
        def parentFile = sourceDir.createFile("parent.txt")
        def targetDir = testDirectory.file("target-directory")

        def directorySnapshot = new DirectorySnapshot(sourceDir.absolutePath, sourceDir.name, FileMetadata.AccessType.DIRECT, TestHashCodes.hashCodeFrom(1234), [
            new DirectorySnapshot(childDir.absolutePath, childDir.name, FileMetadata.AccessType.DIRECT, TestHashCodes.hashCodeFrom(2345), [
                new RegularFileSnapshot(childFile.absolutePath, childFile.name, TestHashCodes.hashCodeFrom(9876), DefaultFileMetadata.file(123, 456, FileMetadata.AccessType.DIRECT))
            ]),
            new RegularFileSnapshot(parentFile.absolutePath, parentFile.name, TestHashCodes.hashCodeFrom(8765), DefaultFileMetadata.file(123, 456, FileMetadata.AccessType.DIRECT))
        ])

        when:
        def relocated = directorySnapshot.relocate(targetDir.absolutePath, stringInterner)
        def index = SnapshotUtil.indexByAbsolutePath(relocated)
        then:
        index.keySet() == [
            targetDir.absolutePath,
            targetDir.file("child").absolutePath,
            targetDir.file("child/child.txt").absolutePath,
            targetDir.file("parent.txt").absolutePath,
        ] as Set

        index.values()*.name as Set == [
            targetDir.name,
            childDir.name,
            childFile.name,
            parentFile.name,
        ] as Set

        index.values().forEach(this::assertInterned)
    }
}
