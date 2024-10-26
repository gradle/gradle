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

import static org.gradle.internal.time.TestTime.timestampOf

class DefaultBuildOperationListenerManagerTest extends Specification {

    def manager = new DefaultBuildOperationListenerManager()
    def broadcaster = manager.broadcaster
    def events = []

    def id1 = new OperationIdentifier(1)
    def op1 = BuildOperationDescriptor.displayName("1").build(id1, null)
    def id2 = new OperationIdentifier(2)
    def op2 = BuildOperationDescriptor.displayName("2").build(id2, null)

    def startEvent = new OperationStartEvent(timestampOf(0))
    def progressEvent = new OperationProgressEvent(timestampOf(0), null)
    def finishEvent = new OperationFinishEvent(timestampOf(0), timestampOf(0), null, null)

    def "notifies start and progress in registration order, finish in reverse registration order"() {
        given:
        manager.addListener(recordingListener("1"))
        manager.addListener(recordingListener("2"))
        manager.addListener(recordingListener("3"))

        when:
        broadcaster.started(op1, startEvent)
        broadcaster.started(op2, startEvent)
        broadcaster.finished(op1, finishEvent)
        broadcaster.finished(op2, finishEvent)

        then:
        events == [
            start("1", id1),
            start("2", id1),
            start("3", id1),
            start("1", id2),
            start("2", id2),
            start("3", id2),
            finished("3", id1),
            finished("2", id1),
            finished("1", id1),
            finished("3", id2),
            finished("2", id2),
            finished("1", id2),
        ]
    }

    def "does not forward progress notifications outside of start and finish"() {
        given:
        manager.addListener(recordingListener("1"))
        manager.addListener(new RecordingListener("2") {
            @Override
            void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
                super.started(buildOperation, startEvent)
                broadcaster.progress(buildOperation.id, progressEvent)
            }

            @Override
            void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent) {
                super.finished(buildOperation, finishEvent)
                broadcaster.progress(buildOperation.id, progressEvent)
            }
        })
        manager.addListener(recordingListener("3"))

        when:
        broadcaster.started(op1, startEvent)
        broadcaster.started(op2, startEvent)
        broadcaster.progress(id1, progressEvent)
        broadcaster.progress(id2, progressEvent)
        broadcaster.finished(op1, finishEvent)
        broadcaster.finished(op2, finishEvent)

        then:
        events == [
            start("1", id1),
            start("2", id1),
            progress("1", id1),
            progress("2", id1),
            start("3", id1),

            start("1", id2),
            start("2", id2),
            progress("1", id2),
            progress("2", id2),
            start("3", id2),

            progress("1", id1),
            progress("2", id1),
            progress("3", id1),

            progress("1", id2),
            progress("2", id2),
            progress("3", id2),

            finished("3", id1),
            finished("2", id1),
            progress("1", id1),
            finished("1", id1),

            finished("3", id2),
            finished("2", id2),
            progress("1", id2),
            finished("1", id2)
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
