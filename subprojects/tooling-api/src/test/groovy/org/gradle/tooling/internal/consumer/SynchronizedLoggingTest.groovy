/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.tooling.internal.consumer

import org.gradle.internal.operations.BuildOperationIdFactory
import org.gradle.internal.time.Clock
import org.gradle.test.fixtures.concurrent.ConcurrentSpec

import java.util.concurrent.CopyOnWriteArraySet

public class SynchronizedLoggingTest extends ConcurrentSpec {
    def logging = new SynchronizedLogging(Stub(Clock), Stub(BuildOperationIdFactory))

    def "initialises on first usage"() {
        expect:
        logging.listenerManager != null
        logging.listenerManager == logging.listenerManager

        logging.progressLoggerFactory != null
        logging.progressLoggerFactory == logging.progressLoggerFactory
    }

    def "keeps state per thread"() {
        given:

        Set loggingTools = new CopyOnWriteArraySet()

        when:
        async {
            2.times {
                start {
                    loggingTools << logging.listenerManager
                    loggingTools << logging.progressLoggerFactory
                }
            }
        }

        then:
        loggingTools.size() == 4
    }
}
