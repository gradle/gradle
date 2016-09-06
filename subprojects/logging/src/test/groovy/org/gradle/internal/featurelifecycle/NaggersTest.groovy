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

package org.gradle.internal.featurelifecycle

import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

@Subject(Naggers)
class NaggersTest extends Specification {
    @Unroll
    def 'Setting #deprecationTracePropertyName=#deprecationTraceProperty overrides setTraceLoggingEnabled value.'() {
        given:
        System.setProperty(deprecationTracePropertyName, deprecationTraceProperty)

        when:
        Naggers.setTraceLoggingEnabled(true)

        then:
        Naggers.isTraceLoggingEnabled() == expectedResult

        when:
        Naggers.setTraceLoggingEnabled(false)

        then:
        Naggers.isTraceLoggingEnabled() == expectedResult

        where:
        deprecationTracePropertyName = Naggers.ORG_GRADLE_DEPRECATION_TRACE_PROPERTY_NAME
        deprecationTraceProperty << ['true', 'false', 'foo']
        expectedResult << [true, false, false]
    }

    @Unroll
    def 'Undefined #deprecationTracePropertyName does not influence setTraceLoggingEnabled value.'() {
        given:
        System.clearProperty(deprecationTracePropertyName)

        when:
        Naggers.setTraceLoggingEnabled(true)

        then:
        Naggers.isTraceLoggingEnabled()

        when:
        Naggers.setTraceLoggingEnabled(false)

        then:
        !Naggers.isTraceLoggingEnabled()

        where:
        deprecationTracePropertyName = Naggers.ORG_GRADLE_DEPRECATION_TRACE_PROPERTY_NAME
    }
}
