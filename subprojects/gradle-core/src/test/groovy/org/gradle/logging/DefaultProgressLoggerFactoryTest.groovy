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



package org.gradle.logging

import spock.lang.Specification
import org.gradle.listener.ListenerManager

class DefaultProgressLoggerFactoryTest extends Specification {
    private final ListenerManager listenerManager = Mock()
    private final ProgressListener progressListener = Mock()
    private final ProgressLoggerFactory factory = new DefaultProgressLoggerFactory(listenerManager)

    def progressLoggerBroadcastsEvents() {
        when:
        def logger = factory.start('description')

        then:
        logger != null
        1 * listenerManager.getBroadcaster(ProgressListener.class) >> progressListener
        1 * progressListener.started(!null)

        when:
        logger.progress('progress')

        then:
        1 * progressListener.progress(!null)

        when:
        logger.completed()

        then:
        1 * progressListener.completed(!null)
    }

    def hasEmptyStatusOnStart() {
        when:
        def logger = factory.start('description')

        then:
        1 * listenerManager.getBroadcaster(ProgressListener.class) >> progressListener
        logger.description == 'description'
        logger.status == ''
    }

    def hasMostRecentStatusOnProgress() {
        when:
        def logger = factory.start('description')
        logger.progress('status')

        then:
        1 * listenerManager.getBroadcaster(ProgressListener.class) >> progressListener
        logger.status == 'status'
    }
    
    def hasMostRecentStatusOnComplete() {
        when:
        def logger = factory.start('description')
        logger.completed('done')

        then:
        1 * listenerManager.getBroadcaster(ProgressListener.class) >> progressListener
        logger.status == 'done'
    }
}

