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

import org.gradle.api.internal.cache.StringInterner
import org.gradle.internal.file.FileType
import org.gradle.internal.file.impl.DefaultFileMetadata
import org.gradle.internal.hash.FileHasher
import org.gradle.internal.hash.HashCode
import org.gradle.internal.snapshot.CompleteDirectorySnapshot
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot
import org.gradle.internal.snapshot.FileSystemSnapshot
import org.gradle.internal.snapshot.FileSystemSnapshotVisitor
import org.gradle.internal.snapshot.RegularFileSnapshot
import org.gradle.internal.snapshot.RelativePathSegmentsTracker
import org.gradle.util.TextUtil
import spock.lang.Specification

class FileSystemSnapshotBuilderTest extends Specification {

    def stringInterner = Stub(StringInterner) {
            intern(_) >> { String string -> string }
    }
    def hasher = Stub(FileHasher) {
        hash(_, _, _) >> {
            HashCode.fromInt(1234)
        }
    }

    String basePath = new File("some/path").absolutePath

    def "can rebuild tree from relative paths"() {
        def builder = new FileSystemSnapshotBuilder(stringInterner, hasher)
        def expectedRelativePaths = ['one', 'one/two', 'one/two/some.txt', 'three', 'three/four.txt']

        when:
        builder.addFile(new File(basePath, "one/two/some.txt"), ["one", "two", "some.txt"] as String[], "some.txt", fileMetadata())
        builder.addDir(new File(basePath, "three"), ["three"] as String[])
        builder.addFile(new File(basePath, "three/four.txt"), ["three", "four.txt"] as String[], "four.txt", fileMetadata())
        Set<String> files = [] as Set
        Set<String> relativePaths = [] as Set
        def result = builder.build()
        result.accept(new FileSystemSnapshotVisitor() {
            private final relativePathTracker = new RelativePathSegmentsTracker()

            @Override
            boolean preVisitDirectory(CompleteDirectorySnapshot directorySnapshot) {
                def isRoot = relativePathTracker.root
                relativePathTracker.enter(directorySnapshot)
                if (!isRoot) {
                    files.add(directorySnapshot.absolutePath)
                    relativePaths.add(relativePathTracker.relativePath.join("/"))
                }
                return true
            }

            @Override
            void visitFile(CompleteFileSystemLocationSnapshot fileSnapshot) {
                files.add(fileSnapshot.absolutePath)
                relativePathTracker.enter(fileSnapshot)
                relativePaths.add(relativePathTracker.relativePath.join("/"))
                relativePathTracker.leave()
            }

            @Override
            void postVisitDirectory(CompleteDirectorySnapshot directorySnapshot) {
                relativePathTracker.leave()
            }
        })

        then:
        normalizeFileSeparators(files) == normalizeFileSeparators(expectedRelativePaths.collect { "${basePath}/$it".toString() } as Set)
        relativePaths == expectedRelativePaths as Set
    }

    private static Set<String> normalizeFileSeparators(Set<String> paths) {
        paths.collect { TextUtil.normaliseFileSeparators(it) } as Set
    }

    def "cannot replace a file with a directory"() {
        def builder = new FileSystemSnapshotBuilder(stringInterner, hasher)
        def relativePath = ["some", "file.txt"] as String[]
        builder.addFile(new File(basePath, "some/file.txt"), relativePath, "file.txt", fileMetadata())

        when:
        builder.addDir(new File(basePath, "some/file.txt"), relativePath)

        then:
        thrown IllegalStateException
    }

    def "cannot replace a directory with a file"() {
        def builder = new FileSystemSnapshotBuilder(stringInterner, hasher)
        def relativePath = ["some", "file.txt"] as String[]
        builder.addDir(new File(basePath, "some/file.txt"), relativePath)

        when:
        builder.addFile(new File(basePath, "some/file.txt"), relativePath, "file.txt", fileMetadata())

        then:
        thrown IllegalStateException
    }

    def "can add root file"() {
        def builder = new FileSystemSnapshotBuilder(stringInterner, hasher)
        def snapshot = fileMetadata()

        when:
        builder.addFile(new File(basePath), [] as String[], "path", snapshot)
        def result = builder.build()

        then:
        result instanceof RegularFileSnapshot
        result.hash == hasher.hash(new File(basePath), snapshot.length, snapshot.lastModified)
    }

    def "can add nothing"() {
        def builder = new FileSystemSnapshotBuilder(stringInterner, hasher)

        expect:
        builder.build() == FileSystemSnapshot.EMPTY
    }

    private static DefaultFileMetadata fileMetadata() {
        new DefaultFileMetadata(FileType.RegularFile, 0, 5)
    }
}
