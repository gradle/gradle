/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.logging.internal

import org.gradle.internal.TimeProvider
import org.gradle.internal.progress.OperationIdentifier
import org.gradle.test.fixtures.concurrent.ConcurrentSpec

class DefaultProgressLoggerFactoryTest extends ConcurrentSpec {
    def progressListener = Mock(ProgressListener)
    def timeProvider = Mock(TimeProvider)
    def factory = new DefaultProgressLoggerFactory(progressListener, timeProvider)

    def progressLoggerBroadcastsEvents() {
        when:
        def logger = factory.newOperation('logger')
        logger.description = 'description'
        logger.started('started')

        then:
        logger != null
        1 * timeProvider.getCurrentTime() >> 100L
        1 * progressListener.started(!null) >> { ProgressStartEvent event ->
            assert event.timestamp == 100L
            assert event.category == 'logger'
            assert event.description == 'description'
            assert event.shortDescription == null
            assert event.loggingHeader == null
            assert event.status == 'started'
        }

        when:
        logger.progress('progress')

        then:
        1 * timeProvider.getCurrentTime() >> 200L
        1 * progressListener.progress(!null) >> { ProgressEvent event ->
            assert event.timestamp == 200L
            assert event.category == 'logger'
            assert event.status == 'progress'
        }

        when:
        logger.completed('completed')

        then:
        1 * timeProvider.getCurrentTime() >> 300L
        1 * progressListener.completed(!null) >> { ProgressCompleteEvent event ->
            assert event.timestamp == 300L
            assert event.category == 'logger'
            assert event.status == 'completed'
        }
    }

    def "attaches current running operation as parent when operation is started"() {
        given:
        def notStarted = factory.newOperation("category").setDescription("ignore-me")
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
            assert event.parentId == null
            assert event.operationId != null
            parentId = event.operationId
        }
        1 * progressListener.started(!null) >> { ProgressStartEvent event ->
            assert event.description == "child"
            assert event.operationId != parentId
            assert event.parentId == parentId
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
            parentId = event.operationId
        }
        1 * progressListener.started(!null) >> { ProgressStartEvent event ->
            assert event.description == "sibling"
            assert event.parentId == parentId
        }
        1 * progressListener.started(!null) >> { ProgressStartEvent event ->
            assert event.description == "child"
            assert event.parentId == parentId
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
            parentId = event.operationId
            assert event.parentId == null
        }
        1 * progressListener.started({it.description == "child"}) >> { ProgressStartEvent event ->
            childId = event.operationId
            assert event.parentId == parentId
        }
        1 * progressListener.started({it.description == "op-2"}) >> { ProgressStartEvent event ->
            id2 = event.operationId
            assert event.parentId == null
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
            parentId = event.operationId
        }
        1 * progressListener.started({it.description == "child"}) >> { ProgressStartEvent event ->
            assert event.parentId == parentId
            assert event.operationId != parentId
        }
    }

    def canSpecifyShortDescription() {
        when:
        def logger = factory.newOperation('logger')
        logger.description = 'description'
        logger.shortDescription = 'short'
        logger.started()

        then:
        1 * progressListener.started(!null) >> { ProgressStartEvent event ->
            assert event.shortDescription == 'short'
        }
    }

    def canSpecifyLoggingHeader() {
        when:
        def logger = factory.newOperation('logger')
        logger.description = 'description'
        logger.loggingHeader = 'header'
        logger.started()

        then:
        1 * progressListener.started(!null) >> { ProgressStartEvent event ->
            assert event.loggingHeader == 'header'
        }
    }

    def canSpecifyNullStatus() {
        def logger = factory.newOperation('logger')
        logger.description = 'not empty'

        when:
        logger.started(null)
        logger.progress(null)
        logger.completed(null)

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

    def cannotChangeShortDescriptionAfterStart() {
        def logger = factory.newOperation('logger')
        logger.description = 'old'
        logger.started()

        when:
        logger.shortDescription = 'new'

        then:
        IllegalStateException e = thrown()
        e.message == 'Cannot configure this operation (logger - old) once it has started.'
    }

    def cannotChangeLoggingHeaderAfterStart() {
        def logger = factory.newOperation('logger')
        logger.description = 'old'
        logger.started()

        when:
        logger.loggingHeader = 'new'

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
        logger.completed('finished')

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
        logger.shortDescription == "f"
        1 * progressListener.started(!null)
    }
}

