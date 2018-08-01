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

import org.gradle.cache.CleanableStore
import org.gradle.cache.CleanupAction
import org.gradle.cache.CleanupProgressMonitor
import org.gradle.internal.resource.local.FileAccessTimeJournal
import spock.lang.Specification
import spock.lang.Subject

class CacheCleanupFileAccessTimeProvidingCleanupActionTest extends Specification {

    def fileAccessTimeJournal = Mock(FileAccessTimeJournal)
    def delegateAction = Mock(CleanupAction)
    def cleanableStore = Stub(CleanableStore)
    def progressMonitor = Stub(CleanupProgressMonitor)
    def fileAccessTimeProvider

    @Subject cleanupAction = CacheCleanupFileAccessTimeProvidingCleanupAction.create(fileAccessTimeJournal) { provider ->
        fileAccessTimeProvider = provider
        return delegateAction
    }

    def "delegates cleanup"() {
        when:
        cleanupAction.clean(cleanableStore, progressMonitor)

        then:
        1 * delegateAction.clean(cleanableStore, progressMonitor)
        0 * fileAccessTimeJournal._
    }

    def "creates and closes a single snapshot"() {
        given:
        def file1 = new File("a")
        def file2 = new File("b")
        def snapshot = Mock(FileAccessTimeJournal.Snapshot)

        when:
        cleanupAction.clean(cleanableStore, progressMonitor)

        then:
        1 * delegateAction.clean(cleanableStore, progressMonitor) >> {
            fileAccessTimeProvider.getLastAccessTime(file1)
            fileAccessTimeProvider.getLastAccessTime(file2)
        }
        1 * fileAccessTimeJournal.createSnapshot() >> snapshot
        1 * snapshot.getLastAccessTime(file1)
        1 * snapshot.getLastAccessTime(file2)

        then:
        1 * snapshot.close()
    }

    def "deletes last access time in the very end"() {
        given:
        def file1 = new File("a")
        def file2 = new File("b")
        def snapshot = Mock(FileAccessTimeJournal.Snapshot)

        when:
        cleanupAction.clean(cleanableStore, progressMonitor)

        then:
        1 * delegateAction.clean(cleanableStore, progressMonitor) >> {
            fileAccessTimeProvider.deleteLastAccessTime(file1)
            fileAccessTimeProvider.getLastAccessTime(file2)
        }
        1 * fileAccessTimeJournal.createSnapshot() >> snapshot
        1 * snapshot.close()

        then:
        1 * fileAccessTimeJournal.deleteLastAccessTime(file1)
    }
}
