/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.language.base.internal.tasks

import com.google.common.collect.Sets
import org.gradle.api.internal.file.TestFiles
import org.gradle.internal.file.Deleter
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class SimpleStaleClassCleanerTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    Deleter deleter = TestFiles.deleter()

    def "deletes all previous output files"() {
        def file1 = tmpDir.file('file1').createFile()
        def file2 = tmpDir.file('file2').createFile()
        def cleaner = new SimpleStaleClassCleaner(deleter, files(file1, file2))
        cleaner.addDirToClean(tmpDir.testDirectory)

        when:
        cleaner.execute()

        then:
        !file1.exists()
        !file2.exists()

        and:
        cleaner.didWork
    }

    def "deletes empty parent directories"() {
        def file1 = tmpDir.file('foo/bar/file1').createFile()
        tmpDir.file('foo/baz/file2').createFile()
        def cleaner = new SimpleStaleClassCleaner(deleter, files(file1))
        cleaner.addDirToClean(tmpDir.testDirectory)

        when:
        cleaner.execute()

        then:
        !tmpDir.file('foo/bar').exists()
        tmpDir.file('foo/baz').exists()


        and:
        cleaner.didWork
    }

    def "deletes parent directories regardless of order"() {
        def file1 = tmpDir.file('foo/file1').createFile()
        def file2 = tmpDir.file('foo/bar/file2').createFile()
        def cleaner = new SimpleStaleClassCleaner(deleter, files(file1, file2))
        cleaner.addDirToClean(tmpDir.testDirectory)

        when:
        cleaner.execute()

        then:
        !tmpDir.file('foo').exists()


        and:
        cleaner.didWork
    }

    def "does not delete the root directory"() {
        def file1 = tmpDir.file('foo/bar/file1').createFile()
        def cleaner = new SimpleStaleClassCleaner(deleter, files(file1))
        cleaner.addDirToClean(tmpDir.testDirectory)

        when:
        cleaner.execute()

        then:
        !tmpDir.file('foo').exists()
        tmpDir.testDirectory.exists()

        and:
        cleaner.didWork
    }

    def "does not delete files that are not under one of the given roots"() {
        def destDir = tmpDir.file('dir')
        def file1 = destDir.file('file1').createFile()
        def file2 = tmpDir.file('file2').createFile()
        def cleaner = new SimpleStaleClassCleaner(deleter, files(file1, file2))
        cleaner.addDirToClean(destDir)

        when:
        cleaner.execute()

        then:
        !file1.exists()
        file2.exists()

        and:
        cleaner.didWork
    }

    def "reports when no work was done"() {
        def cleaner = new SimpleStaleClassCleaner(deleter, files())
        cleaner.addDirToClean(tmpDir.file('dir'))

        when:
        cleaner.execute()

        then:
        !cleaner.didWork
    }

    Set<File> files(File... args) {
        Sets.newLinkedHashSet(args as List)
    }
}
