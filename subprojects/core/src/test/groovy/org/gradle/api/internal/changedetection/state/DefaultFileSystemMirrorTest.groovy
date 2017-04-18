/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.changedetection.state

import org.gradle.BuildResult
import org.gradle.api.internal.GradleInternal
import org.gradle.internal.classpath.CachedJarFileStore
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class DefaultFileSystemMirrorTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    DefaultFileSystemMirror mirror
    TestFile cacheDir

    def setup() {
        cacheDir = tmpDir.createDir("cache")
        def fileStore = Stub(CachedJarFileStore)
        fileStore.fileStoreRoots >> [cacheDir]
        mirror = new DefaultFileSystemMirror([fileStore])
    }

    def "keeps state about a file until task outputs are generated"() {
        def file = tmpDir.file("a")
        def fileSystemResource = Stub(SnapshottableFileSystemResource)
        def fileTreeSnapshot = Stub(SnapshottableDirectoryTree)
        def snapshot = Stub(Snapshot)

        given:
        _ * fileSystemResource.path >> file.path
        _ * fileTreeSnapshot.path >> file.path

        expect:
        mirror.getFile(file.path) == null
        mirror.getDirectoryTree(file.path) == null
        mirror.getContent(file.path) == null

        mirror.putFile(fileSystemResource)
        mirror.putDirectory(fileTreeSnapshot)
        mirror.putContent(file.path, snapshot)

        mirror.getFile(file.path) == fileSystemResource
        mirror.getDirectoryTree(file.path) == fileTreeSnapshot
        mirror.getContent(file.path) == snapshot

        mirror.beforeTaskOutputsGenerated()

        mirror.getFile(file.path) == null
        mirror.getDirectoryTree(file.path) == null
        mirror.getContent(file.path) == null
    }

    def "keeps state about a file until end of build"() {
        def file = tmpDir.file("a")
        def fileSystemResource = Stub(SnapshottableFileSystemResource)
        def directoryTree = Stub(SnapshottableDirectoryTree)
        def snapshot = Stub(Snapshot)
        def buildResult = Stub(BuildResult)
        def gradle = Stub(GradleInternal)

        given:
        _ * fileSystemResource.path >> file.path
        _ * directoryTree.path >> file.path
        _ * buildResult.gradle >> gradle
        _ * gradle.parent >> null

        expect:
        mirror.getFile(file.path) == null
        mirror.getDirectoryTree(file.path) == null
        mirror.getContent(file.path) == null

        mirror.putFile(fileSystemResource)
        mirror.putDirectory(directoryTree)
        mirror.putContent(file.path, snapshot)

        mirror.getFile(file.path) == fileSystemResource
        mirror.getDirectoryTree(file.path) == directoryTree
        mirror.getContent(file.path) == snapshot

        mirror.beforeComplete()

        mirror.getFile(file.path) == null
        mirror.getDirectoryTree(file.path) == null
        mirror.getContent(file.path) == null
    }

    def "does not discard state about a file that lives in the caches when task outputs are generated"() {
        def file = cacheDir.file("some/dir/a")
        def fileSystemResource = Stub(SnapshottableFileSystemResource)
        def directoryTree = Stub(SnapshottableDirectoryTree)
        def snapshot = Stub(Snapshot)
        def buildResult = Stub(BuildResult)
        def gradle = Stub(GradleInternal)

        given:
        _ * fileSystemResource.path >> file.path
        _ * directoryTree.path >> file.path
        _ * buildResult.gradle >> gradle
        _ * gradle.parent >> null

        expect:
        mirror.getFile(file.path) == null
        mirror.getDirectoryTree(file.path) == null
        mirror.getContent(file.path) == null

        mirror.putFile(fileSystemResource)
        mirror.putDirectory(directoryTree)
        mirror.putContent(file.path, snapshot)

        mirror.getFile(file.path) == fileSystemResource
        mirror.getDirectoryTree(file.path) == directoryTree
        mirror.getContent(file.path) == snapshot

        mirror.beforeTaskOutputsGenerated()

        mirror.getFile(file.path) == fileSystemResource
        mirror.getDirectoryTree(file.path) == directoryTree
        mirror.getContent(file.path) == snapshot

        mirror.beforeComplete()

        mirror.getFile(file.path) == null
        mirror.getDirectoryTree(file.path) == null
        mirror.getContent(file.path) == null
    }
}
