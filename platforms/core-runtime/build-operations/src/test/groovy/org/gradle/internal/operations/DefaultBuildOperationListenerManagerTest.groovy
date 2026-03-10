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

    def idFactory = new DefaultBuildOperationIdFactory()
    def manager = new DefaultBuildOperationListenerManager(idFactory)
    def broadcaster = manager.broadcaster
    def events = []

    def startEvent = new OperationStartEvent(0)
    def progressEvent = new OperationProgressEvent(0, null)
    def finishEvent = new OperationFinishEvent(0, 0, null, null)

    private BuildOperationDescriptor nextOp(String displayName) {
        def id = new OperationIdentifier(idFactory.nextId())
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

    def "does not forward notifications for operations started before listener was registered"() {
        given:
        // Listeners "1" and "2" registered before any operations
        manager.addListener(recordingListener("1"))
        manager.addListener(recordingListener("2"))

        // op1's ID is allocated after listeners "1" and "2" but before "3"
        def op1 = nextOp("1")

        // Listener "3" is registered after op1's ID was allocated
        manager.addListener(recordingListener("3"))

        // op2 is allocated after all listeners are registered
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
            // op1: listeners "1" and "2" see it, "3" does not (registered after op1's ID)
            start("1", op1.id),
            start("2", op1.id),

            // op2: all listeners see it
            start("1", op2.id),
            start("2", op2.id),
            start("3", op2.id),

            // progress for op1 — "3" does not see it
            progress("1", op1.id),
            progress("2", op1.id),

            // progress for op2 — all listeners see it
            progress("1", op2.id),
            progress("2", op2.id),
            progress("3", op2.id),

            // finished for op1 — "3" does not see it (reverse order)
            finished("2", op1.id),
            finished("1", op1.id),

            // finished for op2 — all listeners see it (reverse order)
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
