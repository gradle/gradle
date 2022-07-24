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
import org.gradle.internal.file.FileMetadata
import org.gradle.internal.file.FileMetadata.AccessType
import org.gradle.internal.file.impl.DefaultFileMetadata
import org.gradle.internal.hash.FileHasher
import org.gradle.internal.hash.TestHashCodes
import org.gradle.internal.snapshot.DirectorySnapshot
import org.gradle.internal.snapshot.FileSystemSnapshot
import org.gradle.internal.snapshot.RegularFileSnapshot
import org.gradle.internal.snapshot.SnapshotVisitorUtil
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.gradle.util.UsesNativeServices
import org.gradle.util.internal.TextUtil
import org.junit.Rule
import spock.lang.Specification

@UsesNativeServices
@CleanupTestDirectory(fieldName = "tmpDir")
class FileSystemSnapshotBuilderTest extends Specification {

    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    def stringInterner = Stub(StringInterner) {
            intern(_) >> { String string -> string }
    }
    def hasher = Stub(FileHasher) {
        hash(_, _, _) >> {
            TestHashCodes.hashCodeFrom(1234)
        }
    }

    String basePath = tmpDir.file("some/path").absolutePath

    def "can rebuild tree from relative paths"() {
        def builder = new FileSystemSnapshotBuilder(stringInterner, hasher)
        def expectedRelativePaths = ['one', 'one/two', 'one/two/some.txt', 'three', 'three/four.txt']

        when:
        builder.addFile(new File(basePath, "one/two/some.txt"), ["one", "two", "some.txt"] as String[], "some.txt", fileMetadata())
        builder.addDir(new File(basePath, "three"), ["three"] as String[])
        builder.addFile(new File(basePath, "three/four.txt"), ["three", "four.txt"] as String[], "four.txt", fileMetadata())
        def result = builder.build()
        def files = SnapshotVisitorUtil.getAbsolutePaths(result) as Set
        def relativePaths = SnapshotVisitorUtil.getRelativePaths(result) as Set

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

    @Requires(TestPrecondition.SYMLINKS)
    def "can add symlinked files"() {
        def builder = new FileSystemSnapshotBuilder(stringInterner, hasher)
        def symlink = fileMetadata(AccessType.VIA_SYMLINK)

        when:
        builder.addFile(new File(basePath), [] as String[], "path", symlink)
        def result = builder.build()

        then:
        result instanceof RegularFileSnapshot
        result.accessType == AccessType.VIA_SYMLINK
    }

    @Requires(TestPrecondition.SYMLINKS)
    def "detects symlinked directories"() {
        def builder = new FileSystemSnapshotBuilder(stringInterner, hasher)
        def actualRootDir = tmpDir.file("actualDir").createDir()
        def actualSubDir = tmpDir.file("actualSubDir").createDir()
        def rootDir = tmpDir.file("rootDir")
        def subDir = rootDir.file("subDir")
        rootDir.createLink(actualRootDir)
        subDir.createLink(actualSubDir)

        when:
        builder.addDir(subDir, ["subDir"] as String[])
        def result = builder.build()
        then:
        result instanceof DirectorySnapshot
        result.accessType == AccessType.VIA_SYMLINK
        result.absolutePath == rootDir.absolutePath
        result.children.size() == 1
        DirectorySnapshot subDirSnapshot = result.children[0] as DirectorySnapshot
        subDirSnapshot.accessType == AccessType.VIA_SYMLINK
        subDirSnapshot.absolutePath == subDir.absolutePath
    }

    private static FileMetadata fileMetadata(AccessType accessType = AccessType.DIRECT) {
        DefaultFileMetadata.file(0, 5, accessType)
    }
}
