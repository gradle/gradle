/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.resolve.result

import spock.lang.Specification

class DefaultBuildableTypedResolveResultTest extends Specification {
    def result = new DefaultBuildableTypedResolveResult()

    def "can query result and failure when resolved successfully"() {
        given:
        result.resolved("result")

        expect:
        result.hasResult()
        result.successful
        result.result == "result"
        result.failure == null
    }

    def "can query failure when resolved with failure"() {
        given:
        def failure = new RuntimeException()
        result.failed(failure)

        expect:
        result.hasResult()
        !result.successful
        result.failure == failure
    }

    def "rethrows resolution failure when result is queried"() {
        given:
        def failure = new RuntimeException()
        result.failed(failure)

        when:
        result.result

        then:
        def e = thrown(RuntimeException)
        e == failure
    }

    def "can mark as failed after resolved"() {
        given:
        def failure = new RuntimeException()
        result.resolved("result")
        result.failed(failure)

        expect:
        result.hasResult()
        !result.successful
        result.failure == failure

        when:
        result.result

        then:
        def e = thrown(RuntimeException)
        e == failure
    }

    def "can mark as successful after failed"() {
        given:
        def failure = new RuntimeException()
        result.failed(failure)
        result.resolved("result")

        expect:
        result.hasResult()
        result.successful
        result.failure == null
        result.result == "result"
    }

    def "cannot query result or failure when no result"() {
        expect:
        !result.hasResult()
        !result.successful

        when:
        result.result

        then:
        def e1 = thrown(IllegalStateException)
        e1.message == 'No result has been specified.'

        when:
        result.failure

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'No result has been specified.'
    }
}
