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

package org.gradle.internal.cleanup

import org.gradle.api.internal.file.delete.Deleter
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class DefaultBuildOutputDeleterTest extends Specification {

    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()

    def deleter = Mock(Deleter)
    def buildOutputDeleter = new DefaultBuildOutputDeleter(deleter)

    def "skips deletion operation if no directory or file is provided"() {
        when:
        buildOutputDeleter.delete([])

        then:
        0 * deleter._
        noExceptionThrown()
    }

    def "skips deletion operation for non-existing directory or file"() {
        when:
        buildOutputDeleter.delete([nonExistentDir(), nonExistentFile()])

        then:
        0 * deleter._
        noExceptionThrown()
    }

    private File nonExistentFile() {
        new File(temporaryFolder.testDirectory, 'test.txt')
    }

    private File nonExistentDir() {
        new File(temporaryFolder.testDirectory, 'test')
    }

    def "can delete existing directory"() {
        given:
        def dir = temporaryFolder.createDir('test')

        when:
        buildOutputDeleter.delete([dir])

        then:
        1 * deleter.delete(dir)
        0 * _
    }

    def "can delete existing file"() {
        given:
        def file = temporaryFolder.createFile('test.txt')

        when:
        buildOutputDeleter.delete([file])

        then:
        1 * deleter.delete(file)
        0 * _
    }

    def "only deletes root outputs"() {
        def dir1 = temporaryFolder.createDir('test', 'sub1')
        def file1 = dir1.file('test1.txt').touch()

        when:
        buildOutputDeleter.delete([dir1, file1])

        then:
        1 * deleter.delete(dir1)
        0 * deleter.delete(file1)
        0 * _
    }
}
