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

package org.gradle.caching.internal.controller

import com.google.common.collect.ImmutableList
import org.gradle.api.internal.cache.StringInterner
import org.gradle.api.internal.file.TestFiles
import org.gradle.internal.RelativePathSupplier
import org.gradle.internal.file.FileType
import org.gradle.internal.file.ThreadLocalBufferProvider
import org.gradle.internal.file.TreeType
import org.gradle.internal.hash.TestHashCodes
import org.gradle.internal.operations.TestBuildOperationExecutor
import org.gradle.internal.snapshot.DirectorySnapshot
import org.gradle.internal.snapshot.FileSystemLocationSnapshot
import org.gradle.internal.snapshot.FileSystemSnapshotHierarchyVisitor
import org.gradle.internal.snapshot.RelativePathTracker
import org.gradle.internal.snapshot.RelativePathTrackingFileSystemSnapshotHierarchyVisitor
import org.gradle.internal.snapshot.SnapshotUtil
import org.gradle.internal.snapshot.SnapshotVisitResult
import org.gradle.internal.vfs.FileSystemAccess
import org.gradle.test.fixtures.Flaky
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import org.slf4j.Logger
import spock.lang.Specification

@Flaky(because = "https://github.com/gradle/gradle-private/issues/3916")
class NextGenBuildCacheControllerTest extends Specification {

    @Rule
    final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    NextGenBuildCacheController controller
    FileSystemAccess fileSystemAccess

    def setup() {
        fileSystemAccess = TestFiles.fileSystemAccess()
        controller = new NextGenBuildCacheController(
            "id",
            Stub(Logger),
            TestFiles.deleter(),
            fileSystemAccess,
            new ThreadLocalBufferProvider(64 * 1024),
            new StringInterner(),
            new TestBuildOperationExecutor(),
            Mock(NextGenBuildCacheAccess)
        )
    }

    def "should use snapshots from cache data for #description output"() {
        given:
        def root = tmpDir.file("root")
        createOuput(root)
        def manifestEntriesBuilder = ImmutableList.<CacheManifest.ManifestEntry> builder()
        def rootSnapshot = fileSystemAccess.read(root.absolutePath)
        rootSnapshot.accept(new RelativePathTracker(), new RelativePathTrackingFileSystemSnapshotHierarchyVisitor() {
            @Override
            SnapshotVisitResult visitEntry(FileSystemLocationSnapshot snapshot, RelativePathSupplier relativePath) {
                manifestEntriesBuilder.add(new CacheManifest.ManifestEntry(
                    snapshot.getType(),
                    relativePath.toRelativePath(),
                    snapshot.getHash(),
                    SnapshotUtil.getLength(snapshot)))
                return SnapshotVisitResult.CONTINUE
            }
        })
        def manifestEntries = manifestEntriesBuilder.build()

        when:
        def actualSnapshot = controller.createSnapshot(type, root, manifestEntries)
            .get()

        then:
        actualSnapshot == rootSnapshot

        where:
        description | type               | createOuput
        "file"      | TreeType.FILE      | { TestFile location -> createFileOutput(location) }
        "directory" | TreeType.DIRECTORY | { TestFile location -> createDirectoryOutput(location) }
    }

    def "can handle missing file output"() {
        given:
        def root = tmpDir.file("root")
        def manifestEntries = [new CacheManifest.ManifestEntry(FileType.Missing, "", TestHashCodes.hashCodeFrom(12345678L), 0)]

        when:
        def actualSnapshot = controller.createSnapshot(TreeType.FILE, root, manifestEntries)

        then:
        !actualSnapshot.present
    }

    def "can handle missing directory output"() {
        given:
        def root = tmpDir.file("root")
        def manifestEntries = [new CacheManifest.ManifestEntry(FileType.Missing, "", TestHashCodes.hashCodeFrom(12345678L), 0)]

        when:
        def actualSnapshot = controller.createSnapshot(TreeType.DIRECTORY, root, manifestEntries)
            .get()

        then:
        actualSnapshot instanceof DirectorySnapshot
        actualSnapshot.absolutePath == root.absolutePath
        actualSnapshot.accept(new FileSystemSnapshotHierarchyVisitor() {
            @Override
            SnapshotVisitResult visitEntry(FileSystemLocationSnapshot entry) {
                // Make sure we don't have entries in the directory snapshot apart from itself
                assert entry == actualSnapshot
                return SnapshotVisitResult.CONTINUE
            }
        })
    }

    void createFileOutput(TestFile location) {
        location.createFile() << "Hello world"
    }

    void createDirectoryOutput(TestFile location) {
        location.createDir()
        location.createFile("a.txt") << "Hello world: 'a'"
        location.createDir("b")
        location.createFile("b/b.txt") << "Hello world: 'b'"
        location.createDir("c")
        location.createFile("c/c.txt") << "Hello world: 'c'"
        location.createDir("c/d")
        location.createFile("c/d/d.txt") << "Hello world: 'd'"
    }
}
