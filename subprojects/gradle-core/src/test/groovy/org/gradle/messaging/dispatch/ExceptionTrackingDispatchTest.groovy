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

package org.gradle.messaging.dispatch

import org.slf4j.Logger
import spock.lang.Specification

class ExceptionTrackingDispatchTest extends Specification {
    private final Dispatch<String> target = Mock()
    private final Logger logger = Mock()
    private final ExceptionTrackingDispatch<String> dispatch = new ExceptionTrackingDispatch<String>(target, logger)

    def stopRethrowsDispatchFailure() {
        RuntimeException failure = new RuntimeException()

        when:
        dispatch.dispatch('message')
        dispatch.stop()

        then:
        1 * target.dispatch('message') >> { throw failure }
        def e = thrown(DispatchException)
        e.cause == failure
        0 * logger._
    }

    def logsAnySubsequentFailures() {
        RuntimeException failure1 = new RuntimeException()
        RuntimeException failure2 = new RuntimeException()

        when:
        dispatch.dispatch('message1')
        dispatch.dispatch('message2')
        dispatch.stop()

        then:
        1 * target.dispatch('message1') >> { throw failure1 }
        1 * target.dispatch('message2') >> { throw failure2 }
        1 * logger.error('Failed to dispatch message message2.', failure2)
        def e = thrown(DispatchException)
        e.cause == failure1
        0 * logger._
    }

    def stopDoesNothingWhenThereWereNoDispatchFailures() {
        when:
        dispatch.dispatch('message')
        dispatch.stop()

        then:
        1 * target.dispatch('message')
        0 * logger._
    }
}
