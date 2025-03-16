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

package org.gradle.internal.file.nio


import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

class ModificationTimeFileAccessTimeJournalTest extends Specification {

    static final long FIXED_TIMESTAMP = 42_000

    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    @Subject ModificationTimeFileAccessTimeJournal journal = new ModificationTimeFileAccessTimeJournal()

    def "updates modification time"() {
        given:
        def file = tmpDir.createFile("file")

        when:
        journal.setLastAccessTime(file, FIXED_TIMESTAMP)

        then:
        file.lastModified() == FIXED_TIMESTAMP
    }

    def "reads modification time"() {
        given:
        def file = tmpDir.createFile("file")
        file.lastModified = FIXED_TIMESTAMP

        when:
        def lastAccessTime = journal.getLastAccessTime(file)

        then:
        lastAccessTime == FIXED_TIMESTAMP
    }
}
