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
import spock.lang.Specification

public class InputFilesStateChangeRuleTest extends Specification {
    def inputSnapshot = Mock(FileCollectionSnapshot)
    def previousInputSnapshot = Mock(FileCollectionSnapshot)
    def listener = Mock(UpToDateChangeListener)

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
        return InputFilesStateChangeRule.create(task, previousExecution, Mock(TaskExecution), snapshotter, 3)
    }

    def "emits change for no previous input snapshot"() {
        when:
        previousInputSnapshot = null
        createStateChanges().findChanges(listener)

        then:
        1 * listener.isAccepting() >> true
        1 * listener.accept({it.getMessage() == "Input file history is not available."})
    }

    def "emits change for file changes since previous input snapshot"() {
        when:
        createStateChanges().findChanges(listener)

        then:
        1 * inputSnapshot.changesSince(previousInputSnapshot, _ as FileCollectionSnapshot.SnapshotChangeListener) >> { snapshot, snapshotChangeListener ->
            assert snapshotChangeListener.resumeAfter == null
            snapshotChangeListener.added("one")
            snapshotChangeListener.removed("two")
            snapshotChangeListener.changed("three")
        }
        _ * listener.isAccepting() >> true
        1 * listener.accept({it.getMessage() == "Input file one has been added."})
        1 * listener.accept({it.getMessage() == "Input file two has been removed."})
        1 * listener.accept({it.getMessage() == "Input file three has changed."})
    }

    def "caches changes for subsequent calls"() {
        final stateChanges = createStateChanges()
        when:
        stateChanges.findChanges(listener)

        then:
        1 * inputSnapshot.changesSince(previousInputSnapshot, _ as FileCollectionSnapshot.SnapshotChangeListener) >> { snapshot, snapshotChangeListener ->
            snapshotChangeListener.added("one")
            snapshotChangeListener.removed("two")
            snapshotChangeListener.changed("three")
        }
        _ * listener.isAccepting() >> true
        _ * listener.accept(_)

        when:
        stateChanges.findChanges(listener)

        then:
        2 * listener.isAccepting() >>> [true, false]
        1 * listener.accept({it.getMessage() == "Input file one has been added."})
        0 * _

        when:
        stateChanges.findChanges(listener)

        then:
        _ * listener.isAccepting() >>> [true, false]
        1 * listener.accept({it.getMessage() == "Input file one has been added."})
        0 * _
    }

    def "resumes changes after cached changes exhausted"() {
        final stateChanges = createStateChanges()
        when: "first call restricts changes to 1"
        stateChanges.findChanges(listener)

        then:
        3 * listener.isAccepting() >>> [true, false, false]
        1 * inputSnapshot.changesSince(previousInputSnapshot, _ as FileCollectionSnapshot.SnapshotChangeListener) >> { snapshot, snapshotChangeListener ->
            assert !snapshotChangeListener.stopped
            snapshotChangeListener.added("one")
            assert snapshotChangeListener.stopped
        }
        1 * listener.accept({it.getMessage() == "Input file one has been added."})
        0 * _

        when: "subsequent call does not restrict changes"
        stateChanges.findChanges(listener)

        then: "first cached changes are emitted"
        1 * listener.isAccepting() >> true
        1 * listener.accept({it.getMessage() == "Input file one has been added."})

        then: "other changes are detected in snapshot"
        _ * listener.isAccepting() >> true
        1 * inputSnapshot.changesSince(previousInputSnapshot, _ as FileCollectionSnapshot.SnapshotChangeListener) >> { snapshot, snapshotChangeListener ->
            assert snapshotChangeListener.resumeAfter == "one"
            snapshotChangeListener.removed("two")
            snapshotChangeListener.changed("three")
            assert !snapshotChangeListener.stopped
        }
        1 * listener.accept({it.getMessage() == "Input file two has been removed."})
        1 * listener.accept({it.getMessage() == "Input file three has changed."})
        0 * _
    }

    def     "resumes changes after cached changes exhausted when cache is full"() {
        final stateChanges = createStateChanges()
        when: "first call has more changes than can fit in cache"
        stateChanges.findChanges(listener)

        then:
        1 * inputSnapshot.changesSince(previousInputSnapshot, _ as FileCollectionSnapshot.SnapshotChangeListener) >> { snapshot, snapshotChangeListener ->
            snapshotChangeListener.added("one")
            snapshotChangeListener.removed("two")
            snapshotChangeListener.changed("three")
            snapshotChangeListener.added("four") // Only 3 changes will be cached
        }
        _ * listener.isAccepting() >> true
        4 * listener.accept(_)
        0 * _

        when: "subsequent call"
        stateChanges.findChanges(listener)

        then: "first cached changes are emitted"
        1 * listener.accept({it.getMessage() == "Input file one has been added."})
        1 * listener.accept({it.getMessage() == "Input file two has been removed."})
        1 * listener.accept({it.getMessage() == "Input file three has changed."})

        and:
        3 * listener.isAccepting() >> true

        then: "other changes are detected in snapshot"
        1 * inputSnapshot.changesSince(previousInputSnapshot, _ as FileCollectionSnapshot.SnapshotChangeListener) >> { snapshot, snapshotChangeListener ->
            assert snapshotChangeListener.resumeAfter == "three"
            snapshotChangeListener.added("four")
        }
        1 * listener.accept({it.getMessage() == "Input file four has been added."})
        1 * listener.isAccepting() >> true
        0 * _
    }
}
