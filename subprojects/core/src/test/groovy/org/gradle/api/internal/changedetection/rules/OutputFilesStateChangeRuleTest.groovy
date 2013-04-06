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
import org.gradle.api.internal.TaskOutputsInternal
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshot
import org.gradle.api.internal.changedetection.state.FileSnapshotter
import org.gradle.api.internal.changedetection.state.TaskExecution
import org.gradle.api.internal.file.collections.SimpleFileCollection
import spock.lang.Specification;

public class OutputFilesStateChangeRuleTest extends Specification {
    def outputSnapshot = Mock(FileCollectionSnapshot)
    def previousOutputSnapshot = Mock(FileCollectionSnapshot)
    def listener = Mock(UpToDateChangeListener)

    TaskStateChanges createStateChanges() {
        def taskOutputs = Stub(TaskOutputsInternal) {
            getFiles() >> new SimpleFileCollection()
        }
        def task = Stub(TaskInternal) {
            toString() >> "TASK"
            getOutputs() >> taskOutputs
        }
        def snapshotter = Stub(FileSnapshotter) {
            snapshot(_) >> outputSnapshot
        }

        def previousExecution = Stub(TaskExecution) {
            getOutputFilesSnapshot() >> previousOutputSnapshot
        }
        return OutputFilesStateChangeRule.create(task, previousExecution, Mock(TaskExecution), snapshotter)
    }

    def "emits change for no previous output snapshot"() {
        when:
        previousOutputSnapshot = null
        createStateChanges().findChanges(listener)

        then:
        1 * listener.isAccepting() >> true
        1 * listener.accept({it.getMessage() == "Output file history is not available for TASK."})
    }

    def "emits change for file changes since previous output snapshot"() {
        when:
        createStateChanges().findChanges(listener)

        then:
        1 * outputSnapshot.changesSince(previousOutputSnapshot, _ as FileCollectionSnapshot.SnapshotChangeListener) >> { snapshot, snapshotChangeListener ->
            assert snapshotChangeListener.resumeAfter == null
            snapshotChangeListener.added("one")
            snapshotChangeListener.removed("two")
            snapshotChangeListener.changed("three")
        }
        _ * listener.isAccepting() >> true
        1 * listener.accept({it.getMessage() == "Output file one has been added."})
        1 * listener.accept({it.getMessage() == "Output file two has been removed."})
        1 * listener.accept({it.getMessage() == "Output file three has changed."})
    }
}
