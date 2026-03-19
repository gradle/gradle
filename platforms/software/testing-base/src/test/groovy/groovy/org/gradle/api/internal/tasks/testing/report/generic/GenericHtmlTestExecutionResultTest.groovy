/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.report.generic

import org.gradle.api.internal.tasks.testing.report.generic.GenericTestExecutionResult.TestFramework
import spock.lang.Specification

/**
 * Unit tests for the path conversion logic in {@link GenericHtmlTestExecutionResult}.
 */
final class GenericHtmlTestExecutionResultTest extends Specification {
    def "can convert paths for JUnit 4"() {
        given:
        def results = new GenericHtmlTestExecutionResult(new File("doesnt-matter"), "irrelevant", TestFramework.JUNIT4)

        expect:
        results.frameworkTestPath(testPath) == frameworkTestPath

        where:
        testPath                | frameworkTestPath
        ""                      | ":"
        ":"                     | ":"
        ":class"                | ":class"
        "class"                 | ":class"
        ":class:method"         | ":class:method"
        "class:method"          | ":class:method"
        ":suite:class:method"   | ":suite:class:method"
        "suite:class:method"    | ":suite:class:method"
    }

    def "can convert paths for JUnit Jupiter"() {
        given:
        def results = new GenericHtmlTestExecutionResult(new File("doesnt-matter"), "irrelevant", TestFramework.JUNIT_JUPITER)

        expect:
        results.frameworkTestPath(testPath) == frameworkTestPath

        where:
        testPath                | frameworkTestPath
        ""                      | ":"
        ":"                     | ":"
        ":class"                | ":class"
        "class"                 | ":class"
        ":class:method"         | ":class:method()"
        "class:method"          | ":class:method()"
        ":suite:class:method"   | ":suite:class:method()"
        "suite:class:method"    | ":suite:class:method()"
    }

    def "can convert paths for Test NG"() {
        given:
        def results = new GenericHtmlTestExecutionResult(new File("doesnt-matter"), "irrelevant", TestFramework.TEST_NG)

        expect:
        results.frameworkTestPath(testPath) == frameworkTestPath

        where:
        testPath                | frameworkTestPath
        ""                      | ":"
        ":"                     | ":"
        ":class"                | ":class"
        "class"                 | ":class"
        ":class:method"         | ":class:method"
        "class:method"          | ":class:method"
        ":suite:class:method"   | ":suite:class:method"
        "suite:class:method"    | ":suite:class:method"
    }
}
