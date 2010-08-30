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
        def logger = factory.start('logger', 'description')

        then:
        logger != null
        1 * timeProvider.getCurrentTime() >> 100L
        1 * progressListener.started({it.timestamp == 100L && it.category == 'logger' && it.description == 'description'})

        when:
        logger.progress('progress')

        then:
        1 * timeProvider.getCurrentTime() >> 200L
        1 * progressListener.progress({it.timestamp == 200L && it.category == 'logger' && it.status == 'progress'})

        when:
        logger.completed('completed')

        then:
        1 * timeProvider.getCurrentTime() >> 300L
        1 * progressListener.completed({it.timestamp == 300L && it.category == 'logger' && it.status == 'completed'})
    }

    def hasEmptyStatusOnStart() {
        when:
        def logger = factory.start('logger', 'description')

        then:
        logger.description == 'description'
        logger.status == ''
    }

    def hasMostRecentStatusOnProgress() {
        when:
        def logger = factory.start('logger', 'description')
        logger.progress('status')

        then:
        logger.status == 'status'
    }
    
    def hasMostRecentStatusOnComplete() {
        when:
        def logger = factory.start('logger', 'description')
        logger.completed('done')

        then:
        logger.status == 'done'
    }
}

