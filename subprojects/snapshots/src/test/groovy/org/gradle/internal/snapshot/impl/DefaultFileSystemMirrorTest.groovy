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

package org.gradle.internal.snapshot.impl

import org.gradle.BuildResult
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.changedetection.state.DefaultWellKnownFileLocations
import org.gradle.internal.classpath.CachedJarFileStore
import org.gradle.internal.file.FileMetadataSnapshot
import org.gradle.internal.hash.HashCode
import org.gradle.internal.snapshot.FileSystemLocationSnapshot
import org.gradle.internal.snapshot.RegularFileSnapshot
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
        mirror = new DefaultFileSystemMirror(new DefaultWellKnownFileLocations([fileStore]))
    }

    def "keeps state about a file until task outputs are generated"() {
        def file = tmpDir.file("a")
        def fileSnapshot = Stub(RegularFileSnapshot)
        def fileTreeSnapshot = Stub(FileSystemLocationSnapshot)
        def metadata = Stub(FileMetadataSnapshot)

        given:

        _ * fileSnapshot.absolutePath >> file.path
        _ * fileSnapshot.hash >> HashCode.fromInt(25)
        _ * fileTreeSnapshot.absolutePath >> file.path

        expect:
        mirror.getMetadata(file.path) == null
        mirror.getSnapshot(file.path) == null

        mirror.putMetadata(file.path, metadata)
        mirror.putSnapshot(fileSnapshot)

        mirror.getMetadata(file.path) == metadata
        mirror.getSnapshot(file.path) == fileSnapshot

        mirror.beforeOutputChange()

        mirror.getMetadata(file.path) == null
        mirror.getSnapshot(file.path) == null
    }

    def "keeps state about a file until end of build"() {
        def file = tmpDir.file("a")
        def fileSnapshot = Stub(RegularFileSnapshot)
        def metadata = Stub(FileMetadataSnapshot)
        def buildResult = Stub(BuildResult)
        def gradle = Stub(GradleInternal)

        given:
        _ * fileSnapshot.absolutePath >> file.path
        _ * fileSnapshot.hash >> HashCode.fromInt(37)
        _ * buildResult.gradle >> gradle
        _ * gradle.parent >> null

        expect:
        mirror.getMetadata(file.path) == null
        mirror.getSnapshot(file.path) == null

        mirror.putMetadata(file.path, metadata)
        mirror.putSnapshot(fileSnapshot)

        mirror.getMetadata(file.path) == metadata
        mirror.getSnapshot(file.path) == fileSnapshot

        mirror.beforeBuildFinished()

        mirror.getMetadata(file.path) == null
        mirror.getSnapshot(file.path) == null
    }

    def "does not discard state about a file that lives in the caches when task outputs are generated"() {
        def file = cacheDir.file("some/dir/a")
        def fileSnapshot = Stub(RegularFileSnapshot)
        def metadata = Stub(FileMetadataSnapshot)
        def buildResult = Stub(BuildResult)
        def gradle = Stub(GradleInternal)

        given:
        _ * fileSnapshot.absolutePath >> file.path
        _ * buildResult.gradle >> gradle
        _ * gradle.parent >> null

        expect:
        mirror.getMetadata(file.path) == null
        mirror.getSnapshot(file.path) == null

        mirror.putMetadata(file.path, metadata)
        mirror.putSnapshot(fileSnapshot)

        mirror.getMetadata(file.path) == metadata
        mirror.getSnapshot(file.path) == fileSnapshot

        mirror.beforeOutputChange()

        mirror.getMetadata(file.path) == metadata
        mirror.getSnapshot(file.path) == fileSnapshot

        mirror.beforeBuildFinished()

        mirror.getMetadata(file.path) == null
        mirror.getSnapshot(file.path) == null
    }
}
