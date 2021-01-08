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

package org.gradle.internal.watch.vfs.impl

import org.gradle.internal.file.FileMetadata.AccessType
import org.gradle.internal.file.impl.DefaultFileMetadata
import org.gradle.internal.hash.Hashing
import org.gradle.internal.snapshot.DirectorySnapshot
import org.gradle.internal.snapshot.PathUtil
import org.gradle.internal.snapshot.RegularFileSnapshot
import org.gradle.internal.watch.registry.impl.SnapshotWatchedDirectoryFinder
import spock.lang.Specification
import spock.lang.Unroll

@Unroll
class SnapshotWatchedDirectoryFinderTest extends Specification {

    def "resolves directories to watch from snapshot"() {
        when:
        def directoriesToWatch = SnapshotWatchedDirectoryFinder.getDirectoriesToWatch(snapshot).collect { it.toString() } as Set
        then:
        normalizeLineSeparators(directoriesToWatch) == (expectedDirectoriesToWatch as Set)

        where:
        snapshot                                       | expectedDirectoriesToWatch
        fileSnapshot('/some/absolute/parent/file')     | ['/some/absolute/parent']
        directorySnapshot('/some/absolute/parent/dir') | ['/some/absolute/parent', '/some/absolute/parent/dir']
    }

    private static RegularFileSnapshot fileSnapshot(String absolutePath) {
        new RegularFileSnapshot(absolutePath, absolutePath.substring(absolutePath.lastIndexOf('/') + 1), Hashing.md5().hashString(absolutePath), DefaultFileMetadata.file(1, 1, AccessType.DIRECT))
    }

    private static DirectorySnapshot directorySnapshot(String absolutePath) {
        new DirectorySnapshot(absolutePath, PathUtil.getFileName(absolutePath), AccessType.DIRECT, Hashing.md5().hashString(absolutePath), [])
    }

    private static Set<String> normalizeLineSeparators(Set<String> paths) {
        return paths*.replace(File.separatorChar, '/' as char) as Set
    }
}
