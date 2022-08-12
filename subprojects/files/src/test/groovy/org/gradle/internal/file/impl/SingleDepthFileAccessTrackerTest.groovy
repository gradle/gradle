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

package org.gradle.internal.file.impl

import org.gradle.internal.file.FileAccessTimeJournal
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class SingleDepthFileAccessTrackerTest extends Specification {
    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    TestFile baseDir = tmpDir.file("base")
    FileAccessTimeJournal journal = Mock(FileAccessTimeJournal)

    def "ignores files in other directories"() {
        given:
        def fileInOtherDirectory = tmpDir.file("some-file.txt")

        when:
        new SingleDepthFileAccessTracker(journal, baseDir, 1).markAccessed(fileInOtherDirectory)

        then:
        0 * journal.setLastAccessTime(_, _)
    }

    def "touches all subdirectories for depth #depth"() {
        given:
        def file1 = baseDir.file("a/aa/aaa/1")
        def file2 = baseDir.file("b/bb/bbb/2")
        def expectedTouchedFiles = touchedPaths.collect { baseDir.file(it) }

        def tracker = new SingleDepthFileAccessTracker(journal, baseDir, depth)

        when:
        tracker.markAccessed(file1)
        tracker.markAccessed(file2)

        then:
        expectedTouchedFiles.empty || expectedTouchedFiles.each {
            1 * journal.setLastAccessTime(it, _)
        }
        0 * _

        where:
        depth | touchedPaths
        1     | ["a", "b"]
        2     | ["a/aa", "b/bb"]
        3     | ["a/aa/aaa", "b/bb/bbb"]
        4     | ["a/aa/aaa/1", "b/bb/bbb/2"]
        5     | []
    }
}
