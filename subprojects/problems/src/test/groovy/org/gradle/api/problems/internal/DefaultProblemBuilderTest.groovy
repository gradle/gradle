/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.problems.internal

import org.gradle.internal.operations.NoOpBuildOperationProgressEventEmitter
import spock.lang.Specification

class DefaultProblemBuilderTest extends Specification {

    def buildOperationEmitter = new NoOpBuildOperationProgressEventEmitter()
    def builder = new DefaultProblemBuilder(buildOperationEmitter)

    def "missing location will throw an IllegalArgumentException"() {
        given:
        builder = builder.message("message")
        builder = builder.undocumented()
        builder = builder.location(null, null)

        when:
        builder.build()

        then:
        thrown(IllegalStateException)
    }

    def "missing line number will throw an IllegalArgumentException"() {
        given:
        builder = builder.message("message")
        builder = builder.undocumented()
        builder = builder.location("file", null)

        when:
        builder.build()

        then:
        thrown(IllegalStateException)
    }

    def "missing column number will not throw an IllegalArgumentException"() {
        given:
        builder.message("message")
        builder = builder.undocumented()
        builder.location("file", 1, null)

        when:
        builder.build()

        then:
        noExceptionThrown()
    }

}
