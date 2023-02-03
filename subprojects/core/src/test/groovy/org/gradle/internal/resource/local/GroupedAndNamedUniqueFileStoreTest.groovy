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

import org.gradle.api.internal.file.temp.DefaultTemporaryFileProvider
import org.gradle.api.internal.file.temp.TemporaryFileProvider
import org.gradle.internal.file.FileAccessTimeJournal
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

class GroupedAndNamedUniqueFileStoreTest extends Specification {

    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    TestFile baseDir = tmpDir.createDir("base")
    TemporaryFileProvider temporaryFileProvider = new DefaultTemporaryFileProvider({ tmpDir.createDir("tmp") })
    FileAccessTimeJournal fileAccessTimeJournal = Mock(FileAccessTimeJournal)
    GroupedAndNamedUniqueFileStore.Grouper<String> grouper = new GroupedAndNamedUniqueFileStore.Grouper<String>() {
        @Override
        String determineGroup(String key) {
            return 'group'
        }
        @Override
        int getNumberOfGroupingDirs() {
            return 0
        }
    }

    @Subject GroupedAndNamedUniqueFileStore<String> fileStore = new GroupedAndNamedUniqueFileStore<String>(baseDir, temporaryFileProvider, fileAccessTimeJournal, grouper, { key -> key }, TestUtil.checksumService)

    def "marks files accessed when they are added to store"() {
        when:
        fileStore.add('1', { it.text = 'Hello, World!' })

        then:
        1 * fileAccessTimeJournal.setLastAccessTime(baseDir.file('group'), _)
    }

    def "marks files accessed when they are moved into the store"() {
        given:
        def file = tmpDir.createFile("1.txt")

        when:
        fileStore.move('1', file)

        then:
        1 * fileAccessTimeJournal.setLastAccessTime(baseDir.file('group'), _)
    }

    def "allows to mark files accessed externally"() {
        when:
        fileStore.getFileAccessTracker().markAccessed(baseDir.file('group/1.txt'))

        then:
        1 * fileAccessTimeJournal.setLastAccessTime(baseDir.file('group'), _)
    }
}
