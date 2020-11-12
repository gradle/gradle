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

package org.gradle.internal.snapshot.impl

import org.gradle.api.internal.cache.StringInterner
import org.gradle.internal.hash.TestFileHasher
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicBoolean

@UsesNativeServices
@CleanupTestDirectory(fieldName = "tmpDir")
class DirectorySnapshotterStatisticsTest extends Specification {
    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    def fileHasher = new TestFileHasher()
    def statisticsCollector = Mock(DirectorySnapshotterStatistics.Collector)
    def directorySnapshotter = new DirectorySnapshotter(fileHasher, new StringInterner(), [], statisticsCollector)

    def "can visit missing file"() {
        when:
        snapshot(tmpDir.file("missing"))

        then:
        1 * statisticsCollector.recordVisitHierarchy()

        then:
        1 * statisticsCollector.recordVisitFileFailed()
        0 * _
    }

    def "can visit regular file"() {
        when:
        snapshot(tmpDir.createFile("regular.txt"))

        then:
        1 * statisticsCollector.recordVisitHierarchy()

        then:
        1 * statisticsCollector.recordVisitFile()
        0 * _
    }

    def "can visit empty directory"() {
        when:
        snapshot(tmpDir.createDir("dir"))

        then:
        1 * statisticsCollector.recordVisitHierarchy()

        then:
        1 * statisticsCollector.recordVisitDirectory()
        0 * _
    }

    def "can visit directory hierarchy"() {
        given:
        def root = tmpDir.createDir("root")
        def subDir1 = root.createDir("sub-dir-1")
        subDir1.createFile("file.txt")
        def subDir2 = root.createDir("sub-dir-2")
        subDir2.createFile("file.txt")

        when:
        snapshot(root)

        then:
        1 * statisticsCollector.recordVisitHierarchy()

        then:
        3 * statisticsCollector.recordVisitDirectory()
        2 * statisticsCollector.recordVisitFile()
        0 * _
    }

    @Requires(TestPrecondition.SYMLINKS)
    def "can visit broken symlink"() {
        given:
        def rootDir = tmpDir.createDir("root")
        rootDir.file('brokenSymlink').createLink("linkTarget")
        assert rootDir.listFiles()*.exists() == [false]

        when:
        snapshot(rootDir)

        then:
        1 * statisticsCollector.recordVisitHierarchy()

        then:
        1 * statisticsCollector.recordVisitDirectory()
        1 * statisticsCollector.recordVisitFile()
        0 * _
    }

    @Requires(TestPrecondition.SYMLINKS)
    def "can visit symlinked hierarchy"() {
        given:
        def rootDir = tmpDir.createDir("root")
        def targetDir = rootDir.createDir("target-dir")
        targetDir.createFile("file.txt")
        rootDir.file('linked-dir').createLink("target-dir")

        when:
        snapshot(rootDir)

        then:
        1 * statisticsCollector.recordVisitHierarchy()

        then:
        1 * statisticsCollector.recordVisitHierarchy()
        3 * statisticsCollector.recordVisitDirectory()
        3 * statisticsCollector.recordVisitFile()
        0 * _
    }

    @Requires(TestPrecondition.FILE_PERMISSIONS)
    def "can visit unreadable files"() {
        given:
        def rootDir = tmpDir.createDir("root")
        rootDir.file('readableFile').createFile()
        rootDir.file('readableDirectory').createDir()
        rootDir.file('unreadableFile').createFile().makeUnreadable()
        rootDir.file('unreadableDirectory').createDir().makeUnreadable()

        when:
        snapshot(rootDir)

        then:
        1 * statisticsCollector.recordVisitHierarchy()
        2 * statisticsCollector.recordVisitDirectory()
        2 * statisticsCollector.recordVisitFile()
        1 * statisticsCollector.recordVisitFileFailed()
        0 * _

        cleanup:
        rootDir.listFiles()*.makeReadable()
    }

    private snapshot(File root) {
        directorySnapshotter.snapshot(root.absolutePath, null, new AtomicBoolean())
    }
}
