/*
 * Copyright 2026 the original author or authors.
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
package org.gradle.api

import spock.lang.Specification

class GradleExceptionTest extends Specification {
    def "constructor carries the given resolutions"() {
        when:
        def exception = new GradleException('failure', ['resolution1', 'resolution2'])

        then:
        exception.message == 'failure'
        exception.cause == null
        exception.resolutions == ['resolution1', 'resolution2']
    }

    def "clearResolutions removes the resolutions given to the constructor"() {
        given:
        def exception = new GradleException('failure', ['resolution'])

        when:
        exception.clearResolutions()

        then:
        exception.resolutions.empty
    }

    def "resolutions are immutable once the constructor has returned"() {
        given:
        def resolutions = ['resolution']
        def exception = new GradleException('failure', resolutions)

        when:
        resolutions.add('added later')

        then:
        exception.resolutions == ['resolution']
    }

    def "a null second argument reports the overload ambiguity"() {
        // Groovy resolves overloads from the runtime types, so a null cause selects the (String, Iterable)
        // constructor rather than failing to compile as it would in Java and Kotlin. Build scripts are Groovy,
        // so this is the path a user hits by writing `throw new GradleException(message, e.cause)` where the
        // nested cause happens to be absent.
        when:
        new GradleException('failure', null)

        then:
        def e = thrown(NullPointerException)
        e.message.contains('resolutions must not be null')
        e.message.contains('(Throwable) null')
    }

    def "casting the null cause disambiguates"() {
        when:
        def exception = new GradleException('failure', (Throwable) null)

        then:
        exception.message == 'failure'
        exception.cause == null
        exception.resolutions.empty
    }

    def "a cause can still be attached with initCause"() {
        given:
        def cause = new RuntimeException('cause')
        def exception = constructed

        when:
        exception.initCause(cause)

        then:
        exception.cause == cause
        exception.resolutions == expectedResolutions

        where:
        constructed                                   | expectedResolutions
        new GradleException()                         | []
        new GradleException('failure')                | []
        new GradleException('failure', ['resolution']) | ['resolution']
    }
}
