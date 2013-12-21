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

package org.gradle.tooling.internal.consumer;


import org.gradle.test.fixtures.ConcurrentTestUtil
import spock.lang.Specification

import java.util.concurrent.CopyOnWriteArraySet

public class SynchronizedLoggingTest extends Specification {

    def logging = new SynchronizedLogging()
    def concurrent = new ConcurrentTestUtil()

    def "must be initialized"() {
        when:
        logging.listenerManager
        then:
        thrown(IllegalStateException)

        when:
        logging.progressLoggerFactory
        then:
        thrown(IllegalStateException)

        when:
        logging.init()
        then:
        logging.listenerManager
        logging.progressLoggerFactory
    }

    def "keeps state per thread"() {
        given:

        Set loggingTools = new CopyOnWriteArraySet()

        when:
        2.times {
            concurrent.start {
                logging.init()
                loggingTools << logging.listenerManager
                loggingTools << logging.progressLoggerFactory
            }
        }
        concurrent.finished()

        then: "each thread has separate instances of logging tools"
        loggingTools.size() == 4
    }
}
