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

package org.gradle.testkit.runner.fixtures.file

import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class TestFileTest extends Specification {

    @Rule
    TestNameTestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider()

    def "can create new instance"() {
        expect:
        new TestFile('build.gradle')
        new TestFile(testDirectoryProvider.testDirectory.absolutePath, 'build.gradle')
        new TestFile(testDirectoryProvider.testDirectory, 'build.gradle')
        new TestFile(testDirectoryProvider.testDirectory.toURI())
        new TestFile(testDirectoryProvider.testDirectory, 'someDir', 'build.gradle')
        new TestFile(testDirectoryProvider.testDirectory, new File('someDir'), 'build.gradle')
    }

    def "can create write to text file and read from it"() {
        given:
        def content = 'Hello world'
        def testFile = new TestFile(testDirectoryProvider.testDirectory)

        when:
        testFile = testFile.file('build.gradle')

        then:
        !testFile.exists()

        when:
        testFile.text = content

        then:
        testFile.isFile()
        testFile.text == content
    }

    def "can create file"() {
        when:
        def testFile = new TestFile(testDirectoryProvider.testDirectory, path).createFile()

        then:
        testFile.isFile()
        def lastModified = testFile.lastModified()

        when:
        testFile.createFile()

        then:
        testFile.isFile()
        lastModified == testFile.lastModified()

        where:
        path << [['build.gradle'] as Object[], ['some', 'dir', 'build.gradle'] as Object[], [new File('some'), 'dir', 'build.gradle'] as Object[]]
    }

    def "can create directory"() {
        when:
        def testFile = new TestFile(testDirectoryProvider.testDirectory, path).createDirectory()

        then:
        testFile.isDirectory()
        def lastModified = testFile.lastModified()

        when:
        testFile.createDirectory()

        then:
        testFile.isDirectory()
        lastModified == testFile.lastModified()

        where:
        path << [['a'] as Object[], ['b', 'c', 'd'] as Object[], [new File('b'), 'c', 'd'] as Object[]]
    }

    def "can list files in a directory"() {
        given:
        def testDir = new TestFile(testDirectoryProvider.testDirectory)
        testDir.file('build.gradle').createFile()
        testDir.file('settings.gradle').createFile()
        testDir.file('src').createDirectory()
        testDir.file('build').createDirectory()

        when:
        def files = testDir.listFiles()

        then:
        files.length == 4
        files.each { assert it instanceof TestFile }
    }
}
