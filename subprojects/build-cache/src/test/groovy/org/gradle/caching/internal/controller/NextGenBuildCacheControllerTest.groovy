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
import org.gradle.internal.file.ThreadLocalBufferProvider
import org.gradle.internal.snapshot.FileSystemLocationSnapshot
import org.gradle.internal.snapshot.RelativePathTracker
import org.gradle.internal.snapshot.RelativePathTrackingFileSystemSnapshotHierarchyVisitor
import org.gradle.internal.snapshot.SnapshotUtil
import org.gradle.internal.snapshot.SnapshotVisitResult
import org.gradle.internal.vfs.FileSystemAccess
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class NextGenBuildCacheControllerTest extends Specification {

    @Rule
    final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    NextGenBuildCacheController controller
    FileSystemAccess fileSystemAccess

    def setup() {
        fileSystemAccess = TestFiles.fileSystemAccess()
        controller = new NextGenBuildCacheController(
            "id",
            TestFiles.deleter(),
            fileSystemAccess,
            new ThreadLocalBufferProvider(64 * 1024),
            new StringInterner(),
            Mock(NextGenBuildCacheAccess)
        )
    }

    def "should use snapshots from cache data for #input output"() {
        given:
        TestFile root = tmpDir.createDir("root")
        createOuputFunction(root)
        ImmutableList.Builder<CacheManifest.ManifestEntry> manifestEntriesBuilder = ImmutableList.builder();
        def rootSnapshot = fileSystemAccess.read(root.absolutePath)
        rootSnapshot.accept(new RelativePathTracker(), new RelativePathTrackingFileSystemSnapshotHierarchyVisitor() {
            @Override
            SnapshotVisitResult visitEntry(FileSystemLocationSnapshot snapshot, RelativePathSupplier relativePath) {
                manifestEntriesBuilder.add(new CacheManifest.ManifestEntry(
                    snapshot.getType(),
                    relativePath.toRelativePath(),
                    snapshot.getHash(),
                    SnapshotUtil.getLength(snapshot)));
                return SnapshotVisitResult.CONTINUE;
            }
        })
        List<CacheManifest.ManifestEntry> manifestEntries = manifestEntriesBuilder.build()

        when:
        def actualSnapshot = controller.createSnapshot(root, manifestEntries)

        then:
        actualSnapshot == rootSnapshot

        where:
        input       | createOuputFunction
        "file"      | { TestFile location -> createFileOutput(location) }
        "directory" | { TestFile location -> createDirectoryOutput(location) }
    }

    def createFileOutput(TestFile root) {
        root.createFile("a.txt") << "Hello world"
    }

    def createDirectoryOutput(TestFile root) {
        root.createFile("a.txt") << "Hello world: 'a'"
        root.createDir("b")
        root.createFile("b/b.txt") << "Hello world: 'b'"
        root.createDir("c")
        root.createFile("c/c.txt") << "Hello world: 'c'"
        root.createDir("c/d")
        root.createFile("c/d/d.txt") << "Hello world: 'd'"
    }
}
