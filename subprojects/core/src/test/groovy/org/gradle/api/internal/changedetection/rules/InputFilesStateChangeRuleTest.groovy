/*
 * Copyright 2013 the original author or authors.
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
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshot
import org.gradle.api.internal.changedetection.state.FileSnapshotter
import org.gradle.api.internal.changedetection.state.TaskExecution
import org.gradle.api.internal.file.collections.SimpleFileCollection
import org.gradle.api.tasks.TaskInputs
import org.gradle.util.ChangeListener
import spock.lang.Specification

public class InputFilesStateChangeRuleTest extends Specification {
    def inputSnapshot = Mock(FileCollectionSnapshot)
    def previousInputSnapshot = Mock(FileCollectionSnapshot)
    FileCollectionSnapshot.ChangeIterator<String> changeIterator = Mock()

    TaskStateChanges createStateChanges() {
        def taskInputs = Stub(TaskInputs) {
            getFiles() >> new SimpleFileCollection()
        }
        def task = Stub(TaskInternal) {
            getInputs() >> taskInputs
        }
        def snapshotter = Stub(FileSnapshotter) {
            snapshot(_) >> inputSnapshot
        }

        def previousExecution = Stub(TaskExecution) {
            getInputFilesSnapshot() >> previousInputSnapshot
        }
        return InputFilesStateChangeRule.create(task, previousExecution, Mock(TaskExecution), snapshotter)
    }

    def "emits change for no previous input snapshot"() {
        when:
        previousInputSnapshot = null
        def messages = createStateChanges().iterator().collect {it.message}

        then:
        messages == ["Input file history is not available."]
    }

    def "emits change for file changes since previous input snapshot"() {
        when:
        def messages = createStateChanges().iterator().collect {it.message}

        then:
        1 * inputSnapshot.iterateChangesSince(previousInputSnapshot) >> changeIterator
        4 * changeIterator.next(_ as ChangeListener) >> { ChangeListener listener ->
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
        messages == ["Input file one has been added.", "Input file two has been removed.", "Input file three has changed."]
    }
}
