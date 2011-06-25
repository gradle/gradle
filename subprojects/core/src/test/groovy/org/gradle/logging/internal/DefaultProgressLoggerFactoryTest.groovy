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

import spock.lang.Specification
import org.gradle.logging.ProgressLoggerFactory
import org.gradle.util.TimeProvider

class DefaultProgressLoggerFactoryTest extends Specification {
    private final ProgressListener progressListener = Mock()
    private final TimeProvider timeProvider = Mock()
    private final ProgressLoggerFactory factory = new DefaultProgressLoggerFactory(progressListener, timeProvider)

    def progressLoggerBroadcastsEvents() {
        when:
        def logger = factory.newOperation('logger')
        logger.description = 'description'
        logger.started('started')

        then:
        logger != null
        1 * timeProvider.getCurrentTime() >> 100L
        1 * progressListener.started(!null) >> { args ->
            def event = args[0]
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
        1 * progressListener.progress(!null) >> { args ->
            def event = args[0]
            assert event.timestamp == 200L
            assert event.category == 'logger'
            assert event.status == 'progress'
        }

        when:
        logger.completed('completed')

        then:
        1 * timeProvider.getCurrentTime() >> 300L
        1 * progressListener.completed(!null) >> { args ->
            def event = args[0]
            assert event.timestamp == 300L
            assert event.category == 'logger'
            assert event.status == 'completed'
        }
    }

    def canSpecifyShortDescription() {
        when:
        def logger = factory.newOperation('logger')
        logger.description = 'description'
        logger.shortDescription = 'short'
        logger.started()

        then:
        1 * progressListener.started(!null) >> { args ->
            def event = args[0]
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
        1 * progressListener.started(!null) >> { args ->
            def event = args[0]
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
        e.message == 'Cannot configure this operation once it has started.'
    }

    def cannotChangeShortDescriptionAfterStart() {
        def logger = factory.newOperation('logger')
        logger.description = 'old'
        logger.started()

        when:
        logger.shortDescription = 'new'

        then:
        IllegalStateException e = thrown()
        e.message == 'Cannot configure this operation once it has started.'
    }

    def cannotChangeLoggingHeaderAfterStart() {
        def logger = factory.newOperation('logger')
        logger.description = 'old'
        logger.started()

        when:
        logger.loggingHeader = 'new'

        then:
        IllegalStateException e = thrown()
        e.message == 'Cannot configure this operation once it has started.'
    }

    def cannotMakeProgressBeforeStart() {
        def logger = factory.newOperation('logger')

        when:
        logger.progress('new')

        then:
        IllegalStateException e = thrown()
        e.message == 'This operation has not been started.'
    }

    def cannotMakeProgressAfterCompletion() {
        def logger = factory.newOperation('logger')
        logger.description = 'not empty'
        logger.started()
        logger.completed()

        when:
        logger.progress('new')

        then:
        IllegalStateException e = thrown()
        e.message == 'This operation has completed.'
    }

    def cannotCompleteBeforeStart() {
        def logger = factory.newOperation('logger')

        when:
        logger.completed('finished')

        then:
        IllegalStateException e = thrown()
        e.message == 'This operation has not been started.'
    }

    def cannotStartMultipleTimes() {
        def logger = factory.newOperation('logger')
        logger.description = 'not empty'
        logger.started()

        when:
        logger.started()

        then:
        IllegalStateException e = thrown()
        e.message == 'This operation has already been started.'
    }

    def cannotStartAfterComplete() {
        def logger = factory.newOperation('logger')
        logger.description = 'not empty'
        logger.started()
        logger.completed()

        when:
        logger.started()

        then:
        IllegalStateException e = thrown()
        e.message == 'This operation has completed.'
    }

    def cannotCompleteMultipleTimes() {
        def logger = factory.newOperation('logger')
        logger.description = 'not empty'
        logger.started()
        logger.completed()

        when:
        logger.completed()

        then:
        IllegalStateException e = thrown()
        e.message == 'This operation has completed.'
    }
}

