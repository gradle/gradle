/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.nativeplatform.fixtures.app

import org.gradle.nativeplatform.fixtures.app.TestElement.TestCase
import org.gradle.nativeplatform.fixtures.app.TestElement.TestSuite

class SwiftXcTestRenderer implements TestSuiteRenderer {
    String render(TestSuite testSuite) {
        return """
import XCTest

class ${testSuite.name}: XCTestCase {
    ${render(testSuite.testCases)}
}
"""
    }

    String render(List<TestCase> testCases) {
        return testCases.collect {
            render(it)
        }.join("\n")
    }

    String render(TestCase testCase) {
        return """
func ${testCase.name}() {
    ${renderAssertion(testCase.expectedResult)}
}
"""
    }

    String renderAssertion(TestCase.Result result) {
        if (result == TestCase.Result.PASS) {
            return "XCTAssert(true)"
        }
        return "XCTAssert(false)"
    }
}
