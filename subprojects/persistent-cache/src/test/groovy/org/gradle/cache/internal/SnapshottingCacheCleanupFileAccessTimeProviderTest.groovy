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


import org.gradle.internal.resource.local.FileAccessTimeJournal
import spock.lang.Specification
import spock.lang.Subject

class SnapshottingCacheCleanupFileAccessTimeProviderTest extends Specification {

    def fileAccessTimeJournal = Mock(FileAccessTimeJournal)

    @Subject provider = new SnapshottingCacheCleanupFileAccessTimeProvider(fileAccessTimeJournal)

    def "creates and closes a single snapshot"() {
        given:
        def file1 = new File("a")
        def file2 = new File("b")
        def snapshot = Mock(FileAccessTimeJournal.Snapshot)

        when:
        provider.getLastAccessTime(file1)
        provider.getLastAccessTime(file2)

        then:
        1 * fileAccessTimeJournal.createSnapshot() >> snapshot
        1 * snapshot.getLastAccessTime(file1)
        1 * snapshot.getLastAccessTime(file2)

        when:
        provider.close()

        then:
        1 * snapshot.close()
    }

    def "deletes last access time when being closed"() {
        given:
        def file1 = new File("a")
        def file2 = new File("b")
        def snapshot = Mock(FileAccessTimeJournal.Snapshot)

        when:
        provider.deleteLastAccessTime(file1)
        provider.getLastAccessTime(file2)

        then:
        1 * fileAccessTimeJournal.createSnapshot() >> snapshot

        when:
        provider.close()

        then:
        1 * snapshot.close()

        then:
        1 * fileAccessTimeJournal.deleteLastAccessTime(file1)
    }
}
