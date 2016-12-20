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

import org.gradle.api.logging.Logger
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.GFileUtils
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule
import spock.lang.Specification

class BuildOutputDeleterTest extends Specification {

    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()

    def logger = Mock(Logger)
    BuildOutputDeleter buildOutputDeleter = new BuildOutputDeleter()

    def setup() {
        buildOutputDeleter.logger = logger
    }

    def "skips deletion operation if no directory or file is provided"() {
        when:
        buildOutputDeleter.delete([])

        then:
        0 * logger._
        noExceptionThrown()
    }

    def "skips deletion operation for non-existing directory or file"() {
        when:
        buildOutputDeleter.delete([new File(temporaryFolder.testDirectory, 'test'), new File(temporaryFolder.testDirectory, 'test.txt')])

        then:
        0 * logger._
        noExceptionThrown()
    }

    def "can delete existing directory"() {
        given:
        def dir = temporaryFolder.createDir('test')

        when:
        buildOutputDeleter.delete([dir])

        then:
        1 * logger.quiet("Cleaned up directory '$dir'")
        !dir.exists()
    }

    def "can delete existing file"() {
        given:
        def file = temporaryFolder.createFile('test.txt')

        when:
        buildOutputDeleter.delete([file])

        then:
        1 * logger.quiet("Cleaned up file '$file'")
        !file.exists()
    }

    def "deletes directories and files recursively"() {
        given:
        def dir1 = temporaryFolder.createDir('test', 'sub1')
        def dir2 = temporaryFolder.createDir('test', 'sub2')
        def file1 = new File(dir1, 'test1.txt')
        def file2 = new File(dir2, 'test2.txt')
        GFileUtils.touch(file1)
        GFileUtils.touch(file2)

        when:
        buildOutputDeleter.delete([temporaryFolder.testDirectory])

        then:
        1 * logger.quiet("Cleaned up directory '$temporaryFolder.testDirectory'")
        !dir1.exists()
        !dir2.exists()
        !file1.exists()
        !file2.exists()
    }

    def "deletes dedicates directories and files"() {
        given:
        def dir1 = temporaryFolder.createDir('test', 'sub1')
        def dir2 = temporaryFolder.createDir('test', 'sub2')
        def file1 = new File(dir1, 'test1.txt')
        def file2 = new File(dir2, 'test2.txt')
        GFileUtils.touch(file1)
        GFileUtils.touch(file2)

        when:
        buildOutputDeleter.delete([file1, dir2])

        then:
        1 * logger.quiet("Cleaned up file '$file1'")
        1 * logger.quiet("Cleaned up directory '$dir2'")
        dir1.exists()
        !dir2.exists()
        !file1.exists()
        !file2.exists()
    }

    @Requires(TestPrecondition.WINDOWS)
    def "logs warning if file cannot be deleted"() {
        given:
        def file = temporaryFolder.createFile('test.txt')
        file.text << 'Hello World'

        when:
        def channel = new RandomAccessFile(file, 'rw').channel
        def lock = channel.lock()
        buildOutputDeleter.delete([file])

        then:
        1 * logger.warn("Unable to clean up '%'", file)

        cleanup:
        lock?.release()
        channel?.close()
    }
}
