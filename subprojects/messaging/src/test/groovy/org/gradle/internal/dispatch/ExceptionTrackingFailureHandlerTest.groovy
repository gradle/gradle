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
package org.gradle.internal.dispatch

import org.slf4j.Logger
import spock.lang.Specification

class ExceptionTrackingFailureHandlerTest extends Specification {
    private final Logger logger = Mock()
    private final ExceptionTrackingFailureHandler dispatch = new ExceptionTrackingFailureHandler(logger)

    def stopRethrowsFailure() {
        RuntimeException failure = new RuntimeException()

        when:
        dispatch.dispatchFailed("message", failure)
        dispatch.stop()

        then:
        def e = thrown(DispatchException)
        e.cause == failure
        0 * logger._
    }

    def logsAnySubsequentFailures() {
        RuntimeException failure1 = new RuntimeException()
        RuntimeException failure2 = new RuntimeException('broken2')

        when:
        dispatch.dispatchFailed("message1", failure1)
        dispatch.dispatchFailed("message2", failure2)
        dispatch.stop()

        then:
        def e = thrown(DispatchException)
        e.cause == failure1
        1 * logger.error('broken2', failure2)
        0 * logger._
    }

    def stopDoesNothingWhenThereWereNoFailures() {
        when:
        dispatch.stop()

        then:
        0 * logger._
    }
}
