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

package org.gradle.cache.internal

import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class SingleDepthFilesFinderTest extends Specification {
    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    def "finds files for depth #depth"() {
        given:
        tmpDir.file("a/aa/aaa").createFile("1")
        tmpDir.file("a/aa/aaa").createFile("2")
        tmpDir.file("b/bb/bbb").createFile("3")
        tmpDir.file("b/bb/bbb").createFile("4")

        when:
        def result = new SingleDepthFilesFinder(depth).find(tmpDir.getTestDirectory(), { true })

        then:
        result as Set == expectedPaths.collect { tmpDir.file(it) } as Set

        where:
        depth | expectedPaths
        1     | ["a", "b"]
        2     | ["a/aa", "b/bb"]
        3     | ["a/aa/aaa", "b/bb/bbb"]
        4     | ["a/aa/aaa/1", "a/aa/aaa/2", "b/bb/bbb/3", "b/bb/bbb/4"]
        5     | []
    }

    def "applies filter"() {
        given:
        def excludedFile = tmpDir.getTestDirectory().createFile("excluded")
        def includedFile = tmpDir.getTestDirectory().createFile("included")
        FileFilter filter = { it != excludedFile }

        when:
        def result = new SingleDepthFilesFinder(1).find(tmpDir.getTestDirectory(), filter)

        then:
        result as List == [includedFile]
    }

    def "handles empty dir"() {
        when:
        def result = new SingleDepthFilesFinder(1).find(tmpDir.getTestDirectory(), { true })

        then:
        result as List == []
    }
}
