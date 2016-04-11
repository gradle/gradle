/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.api.file.FileCollection
import org.gradle.api.internal.cache.StringInterner
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.hash.DefaultHasher
import org.gradle.cache.internal.MapBackedInMemoryStore
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

@UsesNativeServices
class OutputFilesCollectionSnapshotterTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider testDir = new TestNameTestDirectoryProvider()
    @Subject
    OutputFilesCollectionSnapshotter snapshotter

    def "output snapshotting should ignore files created between executions"() {
        given:
        def rootDir = testDir.createDir("root")
        rootDir.createFile("a/1.txt")
        rootDir.createFile("b/2.txt")
        rootDir.createFile("b/3.txt")
        def previous = createSnapshot(rootDir)
        rootDir.createFile("otheroutput/1.txt")
        def beforeTask = createSnapshot(rootDir)
        rootDir.createFile("taskcreated/1.txt")
        def afterTask = createSnapshot(rootDir)
        when:
        def outputSnapshot = snapshotter.createOutputSnapshot(previous, beforeTask, afterTask, createFileCollection(rootDir))
        then:
        outputSnapshot.files.findAll { it.isFile() }.size() == 4
    }

    private FileCollectionSnapshot createSnapshot(File dir) {
        snapshotter.snapshot(snapshotter.preCheck(createFileCollection(dir), false))
    }

    private FileCollection createFileCollection(File dir) {
        TestFiles.fileCollectionFactory().fixed("root", dir)
    }

    def setup() {
        def cachingTreeVisitor = new CachingTreeVisitor()
        def stringInterner = new StringInterner()
        def cache = new InMemoryCache()
        def hasher = new DefaultHasher()
        def fileSnapshotter = new CachingFileSnapshotter(hasher, cache, stringInterner)
        def treeSnapshotCache = new TreeSnapshotRepository(cache, stringInterner)
        def defaultSnapshotter = new DefaultFileCollectionSnapshotter(fileSnapshotter, cache, stringInterner, TestFiles.resolver(), cachingTreeVisitor, treeSnapshotCache)
        snapshotter = new OutputFilesCollectionSnapshotter(defaultSnapshotter, stringInterner)
    }

    private static class InMemoryCache extends MapBackedInMemoryStore implements TaskArtifactStateCacheAccess {

    }
}
