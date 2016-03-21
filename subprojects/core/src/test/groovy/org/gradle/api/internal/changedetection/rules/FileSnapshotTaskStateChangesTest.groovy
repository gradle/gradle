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
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshot
import org.gradle.util.ChangeListener
import spock.lang.Specification

public class FileSnapshotTaskStateChangesTest extends Specification {
    def previousInputSnapshot = Mock(FileCollectionSnapshot)
    def inputSnapshot = Mock(FileCollectionSnapshot)
    FileCollectionSnapshot.ChangeIterator<String> changeIterator = Mock()

    TaskStateChanges createStateChanges() {
        return new TestFileSnapshotTaskStateChanges()
    }

    def "emits change for no previous input snapshot"() {
        when:
        previousInputSnapshot = null
        def messages = createStateChanges().iterator().collect {it.message}

        then:
        messages == ["TYPE file history is not available."]
    }

    def "emits change for file changes since previous input snapshot"() {
        when:
        def messages = createStateChanges().iterator().collect {it.message}

        then:
        1 * inputSnapshot.iterateContentChangesSince(previousInputSnapshot, _) >> changeIterator
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
        messages == ["TYPE file one has been added.", "TYPE file two has been removed.", "TYPE file three has changed."]
    }

    private class TestFileSnapshotTaskStateChanges extends AbstractFileSnapshotTaskStateChanges {

        private TestFileSnapshotTaskStateChanges() {
            super("TASK")
        }

        @Override
        protected String getInputFileType() {
            return "TYPE"
        }

        @Override
        protected FileCollectionSnapshot getPrevious() {
            return previousInputSnapshot
        }

        @Override
        protected FileCollectionSnapshot getCurrent() {
            return inputSnapshot
        }

        @Override
        protected void saveCurrent() {

        }
    }
}
