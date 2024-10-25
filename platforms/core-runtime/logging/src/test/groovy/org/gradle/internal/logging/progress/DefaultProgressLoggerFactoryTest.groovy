/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.internal.logging.progress

import org.gradle.internal.logging.events.ProgressCompleteEvent
import org.gradle.internal.logging.events.ProgressEvent
import org.gradle.internal.logging.events.ProgressStartEvent
import org.gradle.internal.operations.DefaultBuildOperationIdFactory
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.internal.time.MockClock
import org.gradle.test.fixtures.concurrent.ConcurrentSpec

class DefaultProgressLoggerFactoryTest extends ConcurrentSpec {
    def progressListener = Mock(ProgressListener)
    def timeProvider = MockClock.create()
    def buildOperationIdFactory = new DefaultBuildOperationIdFactory()
    def factory = new DefaultProgressLoggerFactory(progressListener, timeProvider, buildOperationIdFactory)

    def progressLoggerBroadcastsEvents() {
        given:
        timeProvider.withStartTime(100L)

        when:
        def logger = factory.newOperation('logger')
        logger.description = 'description'
        logger.started('started')

        then:
        logger != null
        1 * progressListener.started(!null) >> { ProgressStartEvent event ->
            assert event.timestamp == 100L
            assert event.category == 'logger'
            assert event.description == 'description'
            assert event.loggingHeader == null
            assert event.status == 'started'
        }

        when:
        logger.progress('progress')

        then:
        1 * progressListener.progress(!null) >> { ProgressEvent event ->
            assert event.status == 'progress'
        }

        when:
        timeProvider.increment(200L)

        logger.completed('completed', false)

        then:
        1 * progressListener.completed(!null) >> { ProgressCompleteEvent event ->
            assert event.timestamp == 300L
            assert event.status == 'completed'
        }
    }

    def "attaches current running operation as parent when operation is started"() {
        given:
        def completed = factory.newOperation("category").setDescription("ignore-me")
        def child = factory.newOperation("category").setDescription("child")
        def parent = factory.newOperation("category").setDescription("parent")
        def parentId

        completed.started()
        completed.completed()

        when:
        parent.started()
        child.started()

        then:
        1 * progressListener.started(!null) >> { ProgressStartEvent event ->
            assert event.description == "parent"
            assert event.parentProgressOperationId == null
            assert event.progressOperationId != null
            parentId = event.progressOperationId
        }
        1 * progressListener.started(!null) >> { ProgressStartEvent event ->
            assert event.description == "child"
            assert event.progressOperationId != parentId
            assert event.parentProgressOperationId == parentId
        }
    }

    def "can specify the parent of an operation"() {
        given:
        def sibling = factory.newOperation("category").setDescription("sibling")
        def parent = factory.newOperation("category").setDescription("parent")
        def child = factory.newOperation(String, parent).setDescription("child")
        def parentId

        when:
        parent.started()
        sibling.started()
        child.started()

        then:
        1 * progressListener.started(!null) >> { ProgressStartEvent event ->
            assert event.description == "parent"
            parentId = event.progressOperationId
        }
        1 * progressListener.started(!null) >> { ProgressStartEvent event ->
            assert event.description == "sibling"
            assert event.parentProgressOperationId == parentId
        }
        1 * progressListener.started(!null) >> { ProgressStartEvent event ->
            assert event.description == "child"
            assert event.parentProgressOperationId == parentId
        }
    }

    def "multiple threads can log independent operations"() {
        OperationIdentifier parentId
        OperationIdentifier childId
        OperationIdentifier id2

        when:
        async {
            start {
                def parent = factory.newOperation("category").setDescription("op-1")
                parent.started()
                def child = factory.newOperation("category").setDescription("child")
                child.started()
                instant.op1Running
                thread.blockUntil.op2Running
                child.completed()
                parent.completed()
            }
            start {
                def logger = factory.newOperation("category").setDescription("op-2")
                logger.started()
                instant.op2Running
                thread.blockUntil.op1Running
                logger.completed()
            }
        }

        then:
        1 * progressListener.started({it.description == "op-1"}) >> { ProgressStartEvent event ->
            parentId = event.progressOperationId
            assert event.parentProgressOperationId == null
        }
        1 * progressListener.started({it.description == "child"}) >> { ProgressStartEvent event ->
            childId = event.progressOperationId
            assert event.parentProgressOperationId == parentId
        }
        1 * progressListener.started({it.description == "op-2"}) >> { ProgressStartEvent event ->
            id2 = event.progressOperationId
            assert event.parentProgressOperationId == null
        }

        and:
        [parentId, childId, id2].toSet().size() == 3
    }

    def "operation can have parent running in a different thread"() {
        OperationIdentifier parentId

        when:
        async {
            def parent = factory.newOperation("category").setDescription("op-1")
            start {
                parent.started()
                instant.parentRunning
                thread.blockUntil.childFinished
                parent.completed()
            }
            start {
                thread.blockUntil.parentRunning
                def child = factory.newOperation(String, parent).setDescription("child")
                child.started()
                child.completed()
                instant.childFinished
            }
        }

        then:
        1 * progressListener.started({it.description == "op-1"}) >> { ProgressStartEvent event ->
            parentId = event.progressOperationId
        }
        1 * progressListener.started({it.description == "child"}) >> { ProgressStartEvent event ->
            assert event.parentProgressOperationId == parentId
            assert event.progressOperationId != parentId
        }
    }

    def canSpecifyNullStatus() {
        def logger = factory.newOperation('logger')
        logger.description = 'not empty'

        when:
        logger.started(null)
        logger.progress(null)
        logger.completed(null, false)

        then:
        1 * progressListener.started({it.status == ''})
        1 * progressListener.progress({it.status == ''})
        1 * progressListener.completed({it.status == ''})
    }

    def mustSpecifyDescriptionBeforeStart() {
        def logger = factory.newOperation('logger')

        when:
        logger.started()

        then:
        IllegalStateException e = thrown()
        e.message == 'A description must be specified before this operation is started.'
    }

    def cannotChangeDescriptionAfterStart() {
        def logger = factory.newOperation('logger')
        logger.description = 'old'
        logger.started()

        when:
        logger.description = 'new'

        then:
        IllegalStateException e = thrown()
        e.message == 'Cannot configure this operation (logger - old) once it has started.'
    }

    def cannotMakeProgressBeforeStart() {
        def logger = factory.newOperation('logger')
        logger.description = 'op'

        when:
        logger.progress('new')

        then:
        IllegalStateException e = thrown()
        e.message == 'This operation (logger - op) has not been started.'
    }

    def cannotMakeProgressAfterCompletion() {
        def logger = factory.newOperation('logger')
        logger.description = 'op'
        logger.started()
        logger.completed()

        when:
        logger.progress('new')

        then:
        IllegalStateException e = thrown()
        e.message == 'This operation (logger - op) has already been completed.'
    }

    def cannotCompleteBeforeStart() {
        def logger = factory.newOperation('logger')
        logger.description = 'op'

        when:
        logger.completed('finished', false)

        then:
        IllegalStateException e = thrown()
        e.message == 'This operation (logger - op) has not been started.'
    }

    def cannotStartMultipleTimes() {
        def logger = factory.newOperation('logger')
        logger.description = 'op'
        logger.started()

        when:
        logger.started()

        then:
        IllegalStateException e = thrown()
        e.message == 'This operation (logger - op) has already been started.'
    }

    def cannotStartAfterComplete() {
        def logger = factory.newOperation('logger')
        logger.description = 'op'
        logger.started()
        logger.completed()

        when:
        logger.started()

        then:
        IllegalStateException e = thrown()
        e.message == 'This operation (logger - op) has already completed.'
    }

    def cannotCompleteMultipleTimes() {
        def logger = factory.newOperation('logger')
        logger.description = 'op'
        logger.started()
        logger.completed()

        when:
        logger.completed()

        then:
        IllegalStateException e = thrown()
        e.message == 'This operation (logger - op) has already been completed.'
    }

    def "can log start conveniently"() {
        when:
        def logger = factory.newOperation('logger').start("foo", "f")

        then:
        logger.description == "foo"
        1 * progressListener.started(!null) >> { ProgressStartEvent event ->
            assert event.description == 'foo'
            assert event.status == 'f'
        }
    }
}

