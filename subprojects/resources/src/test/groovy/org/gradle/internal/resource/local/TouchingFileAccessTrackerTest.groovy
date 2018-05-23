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

package org.gradle.internal.resource.local

import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Unroll

import static java.util.concurrent.TimeUnit.MILLISECONDS
import static java.util.concurrent.TimeUnit.SECONDS

class TouchingFileAccessTrackerTest extends Specification {
    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    TestFile baseDir = tmpDir.createDir("base")

    def "ignores files in other directories"() {
        given:
        def fileInOtherDirectory = tmpDir.createFile("some-file.txt").makeOlder()
        def modificationTimeBeforeCall = fileInOtherDirectory.lastModified()

        when:
        new TouchingFileAccessTracker(baseDir, 1).markAccessed([fileInOtherDirectory])

        then:
        fileInOtherDirectory.lastModified() == modificationTimeBeforeCall
    }

    @Unroll
    def "touches all subdirectories for  depth #depth"() {
        given:
        def file1 = baseDir.createDir("a/aa/aaa").createFile("1")
        def file2 = baseDir.createDir("b/bb/bbb").createFile("2")
        def allFiles = [
            file1, file1.parentFile, file1.parentFile.parentFile, file1.parentFile.parentFile.parentFile,
            file2, file2.parentFile, file2.parentFile.parentFile, file2.parentFile.parentFile.parentFile
        ]
        allFiles*.lastModified = 0
        def timeBeforeCallFlooredToSeconds = SECONDS.toMillis(MILLISECONDS.toSeconds(System.currentTimeMillis()))
        def expectedTouchedFiles = touchedPaths.collect { baseDir.file(it) }

        when:
        new TouchingFileAccessTracker(baseDir, depth).markAccessed([file1, file2])

        then:
        expectedTouchedFiles.empty || expectedTouchedFiles.each {
            it.lastModified() >= timeBeforeCallFlooredToSeconds
        }
        allFiles.findAll { !expectedTouchedFiles.contains(it) }.each {
            it.lastModified() == 0
        }

        where:
        depth | touchedPaths
        1     | ["a", "b"]
        2     | ["a/aa", "b/bb"]
        3     | ["a/aa/aaa", "b/bb/bbb"]
        4     | ["a/aa/aaa/5", "b/bb/bbb/2"]
        5     | []
    }
}
