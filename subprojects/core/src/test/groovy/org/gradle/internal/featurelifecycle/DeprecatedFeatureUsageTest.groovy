/*
 * Copyright 2013 the original author or authors.
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

class DeprecatedFeatureUsageTest extends Specification {
    def "can create copy with stack-trace filled in"() {
        def usage = new DeprecatedFeatureUsage("message", DeprecatedFeatureUsageTest)

        expect:
        usage.stack.empty
        def copy = usage.withStackTrace()
        copy.message == usage.message
        !copy.stack.empty
    }

    def "returns self when stack-trace already filled in"() {
        def usage = new DeprecatedFeatureUsage("message", DeprecatedFeatureUsageTest).withStackTrace()

        expect:
        usage.withStackTrace() == usage
    }
}
