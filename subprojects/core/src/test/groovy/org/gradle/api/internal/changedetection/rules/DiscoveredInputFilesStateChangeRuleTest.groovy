/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.internal.changedetection.rules
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshot
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshotter
import org.gradle.api.internal.changedetection.state.TaskExecution
import org.gradle.api.internal.file.collections.SimpleFileCollection
import org.gradle.util.ChangeListener
import spock.lang.Specification

class DiscoveredInputFilesStateChangeRuleTest extends Specification {
    def currentExecution = Mock(TaskExecution)
    def previousInputSnapshot = Mock(FileCollectionSnapshot)
    def discoveredFileSnapshot = Mock(FileCollectionSnapshot)
    FileCollectionSnapshot.ChangeIterator<String> changeIterator = Mock()

    TaskStateChanges createStateChanges() {
        def previousExecution = Stub(TaskExecution) {
            getDiscoveredInputFilesSnapshot() >> previousInputSnapshot
        }
        def inputSnapshotter = Stub(FileCollectionSnapshotter) {
            snapshot(_) >> discoveredFileSnapshot
        }
        return DiscoveredInputFilesStateChangeRule.create(previousExecution, currentExecution, inputSnapshotter)
    }

    def "emits change for no previous input snapshot"() {
        when:
        previousInputSnapshot = null
        def messages = createStateChanges().iterator().collect {it.message}

        then:
        messages == ["Discovered input file history is not available."]
    }

    def "emits change for file changes since previous input snapshot"() {
        when:
        def messages = createStateChanges().iterator().collect {it.message}

        then:
        1 * previousInputSnapshot.getFiles() >> new SimpleFileCollection()
        1 * discoveredFileSnapshot.iterateChangesSince(previousInputSnapshot) >> changeIterator
        4 * changeIterator.next(_ as ChangeListener) >> { ChangeListener listener ->
            // added probably doesn't make sense for discovered inputs...
            listener.added("one")
            true
        } >> { ChangeListener listener ->
            listener.removed("two")
            true
        } >> { ChangeListener listener ->
            listener.changed("three")
            true
        } >> false

        and:
        messages == ["Discovered input file one has been added.", "Discovered input file two has been removed.", "Discovered input file three has changed."]
    }
}
