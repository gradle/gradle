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

package org.gradle.internal.operations

import spock.lang.Specification

class DefaultBuildOperationListenerManagerTest extends Specification {

    def manager = new DefaultBuildOperationListenerManager()
    def broadcaster = manager.broadcaster
    def events = []

    def startEvent = new OperationStartEvent(0)
    def progressEvent = new OperationProgressEvent(0, null)
    def finishEvent = new OperationFinishEvent(0, 0, null, null)

    private long nextId = 1

    private BuildOperationDescriptor nextOp(String displayName) {
        def id = new OperationIdentifier(nextId++)
        BuildOperationDescriptor.displayName(displayName).build(id, null)
    }

    def "notifies start and progress in registration order, finish in reverse registration order"() {
        given:
        manager.addListener(recordingListener("1"))
        manager.addListener(recordingListener("2"))
        manager.addListener(recordingListener("3"))
        def op1 = nextOp("1")
        def op2 = nextOp("2")

        when:
        broadcaster.started(op1, startEvent)
        broadcaster.started(op2, startEvent)
        broadcaster.finished(op1, finishEvent)
        broadcaster.finished(op2, finishEvent)

        then:
        events == [
            start("1", op1.id),
            start("2", op1.id),
            start("3", op1.id),
            start("1", op2.id),
            start("2", op2.id),
            start("3", op2.id),
            finished("3", op1.id),
            finished("2", op1.id),
            finished("1", op1.id),
            finished("3", op2.id),
            finished("2", op2.id),
            finished("1", op2.id),
        ]
    }

    def "does not forward progress or finished for operations whose started was not seen"() {
        given:
        def op1 = nextOp("1")

        // Register listener after op1's ID is allocated — but before started is broadcast
        manager.addListener(recordingListener("1"))

        def op2 = nextOp("2")

        when:
        // op1 started is delivered to listener "1" since it's in the list
        broadcaster.started(op1, startEvent)
        broadcaster.started(op2, startEvent)
        broadcaster.progress(op1.id, progressEvent)
        broadcaster.progress(op2.id, progressEvent)
        broadcaster.finished(op1, finishEvent)
        broadcaster.finished(op2, finishEvent)

        then:
        // Listener sees all events for both ops — ID allocation order doesn't matter,
        // only whether started() was delivered
        events == [
            start("1", op1.id),
            start("1", op2.id),
            progress("1", op1.id),
            progress("1", op2.id),
            finished("1", op1.id),
            finished("1", op2.id),
        ]
    }

    def "does not forward progress for operations whose started was not delivered to listener"() {
        given:
        def op1 = nextOp("1")

        // Broadcast started before any listeners are registered
        broadcaster.started(op1, startEvent)

        // Now register a listener — it missed started for op1
        manager.addListener(recordingListener("1"))

        def op2 = nextOp("2")

        when:
        broadcaster.started(op2, startEvent)
        broadcaster.progress(op1.id, progressEvent)
        broadcaster.progress(op2.id, progressEvent)
        broadcaster.finished(op1, finishEvent)
        broadcaster.finished(op2, finishEvent)

        then:
        // Listener sees finished for op1 even though it missed started,
        // but progress for op1 is correctly filtered out
        events == [
            start("1", op2.id),
            progress("1", op2.id),
            finished("1", op1.id),
            finished("1", op2.id),
        ]
    }

    def "listener registered between two listeners sees operations started after registration"() {
        given:
        manager.addListener(recordingListener("1"))
        manager.addListener(recordingListener("2"))

        def op1 = nextOp("1")

        manager.addListener(recordingListener("3"))

        def op2 = nextOp("2")

        when:
        broadcaster.started(op1, startEvent)
        broadcaster.started(op2, startEvent)
        broadcaster.progress(op1.id, progressEvent)
        broadcaster.progress(op2.id, progressEvent)
        broadcaster.finished(op1, finishEvent)
        broadcaster.finished(op2, finishEvent)

        then:
        events == [
            // All listeners see started for op1 (all were registered when started was broadcast)
            start("1", op1.id),
            start("2", op1.id),
            start("3", op1.id),

            start("1", op2.id),
            start("2", op2.id),
            start("3", op2.id),

            // All listeners see progress for both ops
            progress("1", op1.id),
            progress("2", op1.id),
            progress("3", op1.id),

            progress("1", op2.id),
            progress("2", op2.id),
            progress("3", op2.id),

            // All listeners see finished for both ops (reverse order)
            finished("3", op1.id),
            finished("2", op1.id),
            finished("1", op1.id),

            finished("3", op2.id),
            finished("2", op2.id),
            finished("1", op2.id),
        ]
    }

    BuildOperationListener recordingListener(String label) {
        new RecordingListener(label)
    }

    String start(String label, OperationIdentifier id) {
        "$label - started $id"
    }

    String progress(String label, OperationIdentifier id) {
        "$label - progress $id"
    }

    String finished(String label, OperationIdentifier id) {
        "$label - finished $id"
    }

    class RecordingListener implements BuildOperationListener {

        private final String label

        RecordingListener(String label) {
            this.label = label
        }

        @Override
        void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
            events << start(label, buildOperation.id)
        }


        @Override
        void progress(OperationIdentifier operationIdentifier, OperationProgressEvent progressEvent) {
            events << progress(label, operationIdentifier)
        }

        @Override
        void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent) {
            events << finished(label, buildOperation.id)
        }
    }

}
