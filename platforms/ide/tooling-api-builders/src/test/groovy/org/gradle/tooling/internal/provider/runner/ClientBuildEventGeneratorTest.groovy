/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.tooling.internal.provider.runner

import org.gradle.internal.build.event.BuildEventSubscriptions
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationListener
import org.gradle.internal.operations.OperationFinishEvent
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.internal.operations.OperationProgressEvent
import org.gradle.internal.operations.OperationStartEvent
import org.gradle.tooling.internal.protocol.events.InternalOperationDescriptor
import org.gradle.tooling.internal.protocol.events.InternalOperationFinishedProgressEvent
import org.gradle.tooling.internal.protocol.events.InternalOperationStartedProgressEvent
import org.gradle.tooling.internal.protocol.events.InternalProgressEvent
import spock.lang.Specification

import static org.gradle.internal.time.TestTime.timestampOf

class ClientBuildEventGeneratorTest extends Specification {
    def fallback = Mock(BuildOperationListener)
    def consumer = Mock(ProgressEventConsumer)
    def subscriptions = Stub(BuildEventSubscriptions)
    def details = "details"
    def operationId = Stub(OperationIdentifier)
    def parentId = Stub(OperationIdentifier)
    def operation = BuildOperationDescriptor.displayName("name").details(details).build(operationId, parentId)
    def startEvent = new OperationStartEvent(timestampOf(0))
    def progressEvent = new OperationProgressEvent(timestampOf(0), "progress")
    def finishEvent = new OperationFinishEvent(timestampOf(0), timestampOf(1), null, "result")
    def clientDescriptor = Stub(InternalOperationDescriptor)
    def clientStartEvent = Stub(InternalOperationStartedProgressEvent)
    def clientFinishEvent = Stub(InternalOperationFinishedProgressEvent)

    def "passes events when enabled mapper available for details type"() {
        def mapper1 = Mock(BuildOperationMapper)
        def mapper2 = Mock(BuildOperationMapper)
        def mappedProgressEvent = Mock(InternalProgressEvent)

        given:
        mapper1.isEnabled(subscriptions) >> true
        mapper1.trackers >> []
        mapper1.detailsType >> Long.class
        mapper2.isEnabled(subscriptions) >> true
        mapper2.trackers >> []
        mapper2.detailsType >> String.class

        def generator = new ClientBuildEventGenerator(consumer, subscriptions, [mapper1, mapper2], fallback)

        when:
        generator.started(operation, startEvent)

        then:
        1 * consumer.findStartedParentId(operation) >> parentId
        1 * mapper2.createDescriptor(details, operation, parentId) >> clientDescriptor
        1 * mapper2.createStartedEvent(clientDescriptor, details, startEvent) >> clientStartEvent
        1 * consumer.started(clientStartEvent)
        0 * _

        when:
        generator.progress(operationId, progressEvent)

        then:
        1 * mapper2.createProgressEvent(clientDescriptor, progressEvent) >> mappedProgressEvent
        1 * consumer.progress(mappedProgressEvent)
        0 * _

        when:
        generator.finished(operation, finishEvent)

        then:
        1 * mapper2.createFinishedEvent(clientDescriptor, details, finishEvent) >> clientFinishEvent
        1 * consumer.finished(clientFinishEvent)
        0 * _
    }

    def "discards events when disabled mapper available for details type"() {
        def mapper1 = Mock(BuildOperationMapper)
        def mapper2 = Mock(BuildOperationMapper)

        given:
        mapper1.isEnabled(subscriptions) >> false
        mapper2.isEnabled(subscriptions) >> false
        mapper1.detailsType >> Long.class
        mapper2.detailsType >> String.class

        def generator = new ClientBuildEventGenerator(consumer, subscriptions, [mapper1, mapper2], fallback)

        when:
        generator.started(operation, startEvent)

        then:
        0 * _

        when:
        generator.progress(operationId, progressEvent)

        then:
        0 * _

        when:
        generator.finished(operation, finishEvent)

        then:
        0 * _
    }

    def "passes events to fallback when no enabled mapper available for details type"() {
        def mapper1 = Mock(BuildOperationMapper)
        def mapper2 = Mock(BuildOperationMapper)

        given:
        mapper1.isEnabled(subscriptions) >> true
        mapper1.trackers >> []
        mapper1.detailsType >> Long.class
        mapper2.isEnabled(subscriptions) >> true
        mapper2.trackers >> []
        mapper2.detailsType >> Boolean.class

        def generator = new ClientBuildEventGenerator(consumer, subscriptions, [mapper1, mapper2], fallback)

        when:
        generator.started(operation, startEvent)

        then:
        1 * fallback.started(operation, startEvent)
        0 * _

        when:
        generator.progress(operationId, progressEvent)

        then:
        1 * fallback.progress(operationId, progressEvent)
        0 * _

        when:
        generator.finished(operation, finishEvent)

        then:
        1 * fallback.finished(operation, finishEvent)
        0 * _
    }

    def "passes events to fallback when no disabled mapper available for details type"() {
        def mapper1 = Mock(BuildOperationMapper)
        def mapper2 = Mock(BuildOperationMapper)

        given:
        mapper1.isEnabled(subscriptions) >> false
        mapper1.detailsType >> Long.class
        mapper2.isEnabled(subscriptions) >> false
        mapper2.detailsType >> Boolean.class

        def generator = new ClientBuildEventGenerator(consumer, subscriptions, [mapper1, mapper2], fallback)

        when:
        generator.started(operation, startEvent)

        then:
        1 * fallback.started(operation, startEvent)
        0 * _

        when:
        generator.progress(operationId, progressEvent)

        then:
        1 * fallback.progress(operationId, progressEvent)
        0 * _

        when:
        generator.finished(operation, finishEvent)

        then:
        1 * fallback.finished(operation, finishEvent)
        0 * _
    }

    def "passes events to trackers required by enabled mapper"() {
        def mapper = Mock(BuildOperationMapper)
        def tracker1 = Mock(BuildOperationTracker)
        def tracker2 = Mock(BuildOperationTracker)
        def tracker3 = Mock(BuildOperationTracker)

        given:
        mapper.isEnabled(subscriptions) >> true
        mapper.trackers >> [tracker1, tracker3]
        mapper.detailsType >> Long.class
        tracker1.trackers >> [tracker2]
        tracker2.trackers >> [tracker3]
        tracker3.trackers >> []

        def generator = new ClientBuildEventGenerator(consumer, subscriptions, [mapper], fallback)

        when:
        generator.started(operation, startEvent)

        then:
        1 * tracker1.started(operation, startEvent)
        1 * tracker2.started(operation, startEvent)
        1 * tracker3.started(operation, startEvent)
        0 * tracker1._
        0 * tracker2._
        0 * tracker3._

        when:
        generator.finished(operation, finishEvent)

        then:
        1 * tracker1.finished(operation, finishEvent)
        1 * tracker2.finished(operation, finishEvent)
        1 * tracker3.finished(operation, finishEvent)

        then:
        1 * tracker1.discardState(operation)
        1 * tracker2.discardState(operation)
        1 * tracker3.discardState(operation)
        0 * tracker1._
        0 * tracker2._
        0 * tracker3._
    }

    def "does not pass events to trackers for disabled mapper"() {
        def mapper = Mock(BuildOperationMapper)
        def tracker = Mock(BuildOperationTracker)

        given:
        mapper.isEnabled(subscriptions) >> false
        mapper.trackers >> [tracker]
        mapper.detailsType >> Long.class

        def generator = new ClientBuildEventGenerator(consumer, subscriptions, [mapper], fallback)

        when:
        generator.started(operation, startEvent)

        then:
        0 * tracker._

        when:
        generator.finished(operation, finishEvent)

        then:
        0 * tracker._
    }
}
