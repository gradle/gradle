/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.tooling.exceptions

import org.gradle.tooling.Failure
import spock.lang.Specification

class FailureSpec extends Specification {

    def "should create a failure from an exception"() {
        given:
            def exception = Mock(Exception)
            exception.getMessage() >> 'some message'
            exception.printStackTrace(_) >> { PrintWriter prn ->
                prn << 'some description'
            }
        when:
            def failure = Failure.fromThrowable(exception)
        then:
            failure != null
            failure.message == 'some message'
            failure.description == 'some description'
            failure.cause == null

    }

    def "should create a failure from an exception with a cause "() {
        given:
            def exception = Mock(Exception)
            def cause = Mock(Exception)
            exception.getMessage() >> 'some message'
            exception.printStackTrace(_) >> { PrintWriter prn ->
                prn << 'some description'
            }
            cause.getMessage() >> 'some cause'
            cause.printStackTrace(_) >> { PrintWriter prn ->
                prn << 'some description cause'
            }
            exception.getCause() >> cause
        when:
            def failure = Failure.fromThrowable(exception)
        then:
            failure != null
            failure.message == 'some message'
            failure.description == 'some description'
            failure.cause != null
            failure.cause.message == 'some cause'
            failure.cause.description == 'some description cause'
    }

    def "failure description should include the cause stack trace"() {
        given:
            def npe = new NullPointerException("oops")
            def ex = new RuntimeException("dummy exception", npe)
        when:
            def failure = Failure.fromThrowable(ex)
        then:
            failure != null
            failure.message == 'dummy exception'
            failure.cause != null
            failure.cause.message == 'oops'
            failure.cause.description.contains('NullPointerException')
            failure.description.contains('NullPointerException')
    }
}
