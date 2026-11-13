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

/**
 * Unit tests for {@link GradleException}.
 */
class GradleExceptionTest extends Specification {
    def "constructor carries the given resolutions"() {
        given:
        def cause = new RuntimeException('cause')

        when:
        def exception = new GradleException('failure', cause, ['resolution1', 'resolution2'])

        then:
        exception.message == 'failure'
        exception.cause == cause
        exception.resolutions == ['resolution1', 'resolution2']
    }

    def "clearResolutions removes the resolutions given to the constructor"() {
        given:
        def exception = new GradleException('failure', new RuntimeException('cause'), ['resolution'])

        when:
        exception.clearResolutions()

        then:
        exception.resolutions.empty
    }

    def "resolutions are immutable once the constructor has returned"() {
        given:
        def resolutions = ['resolution']
        def exception = new GradleException('failure', new RuntimeException('cause'), resolutions)

        when:
        resolutions.add('added later')

        then:
        exception.resolutions == ['resolution']
    }

    def "a null second argument is unambiguously the cause"() {
        // There is no (String, Iterable) overload, so a null second argument cannot be ambiguous. Groovy resolves
        // overloads from the runtime types, which is why this is worth pinning: it is the path a build script takes
        // when it writes `throw new GradleException(message, e.cause)` and the nested cause happens to be absent.
        when:
        def exception = new GradleException('failure', null)

        then:
        exception.message == 'failure'
        exception.cause == null
        exception.resolutions.empty
    }

    def "resolutions can be attached without a cause"() {
        // The replacement for the removed (String, Iterable) constructor, which leaves the cause attachable.
        given:
        def exception = new GradleException('failure')

        when:
        exception.addResolution('resolution')

        then:
        exception.resolutions == ['resolution']
        exception.cause == null
    }

    def "a cause can still be attached with initCause"() {
        given:
        def cause = new RuntimeException('cause')
        def exception = constructed

        when:
        exception.initCause(cause)

        then:
        exception.cause == cause

        where:
        constructed << [
            new GradleException(),
            new GradleException('failure'),
        ]
    }

    def "passing a null cause alongside resolutions blocks initCause"() {
        // Documented consequence of the surviving three-argument constructor: an explicitly null cause still
        // counts as initialized. Callers that need an attachable cause use addResolution instead.
        given:
        def exception = new GradleException('failure', null, ['resolution'])

        when:
        exception.initCause(new RuntimeException('cause'))

        then:
        thrown(IllegalStateException)
    }
}
