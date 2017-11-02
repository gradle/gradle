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

class SwiftXCTestWithUnicodeCharactersInTestName extends XCTestSourceElement {
    List<XCTestSourceFileElement> testSuites = [
        new XCTestSourceFileElement() {
            String testSuiteName = "NormalTestSuite"
            List<XCTestCaseElement> testCases = [
                testCase("testExpectedTestName", "XCTAssert(true)")]
        },

        new XCTestSourceFileElement() {
            String testSuiteName = "SpecialCharsTestSuite"
            List<XCTestCaseElement> testCases = [
                testCase("test_name_with_leading_underscore", "XCTAssert(true)"),
                testCase("testname_with_a_number_1234", "XCTAssert(true)"),
                testCase("test·middle_dot", "XCTAssert(true)"),
                testCase("test1234", "XCTAssert(true)"),
                testCase("testᏀᎡᎪᎠᏞᎬ", "XCTAssert(true)")
            ]
        },

        new XCTestSourceFileElement() {
            String testSuiteName = "UnicodeᏀᎡᎪᎠᏞᎬSuite"
            List<XCTestCaseElement> testCases = [
                testCase("testSomething", "XCTAssert(true)"),
            ]
        }
    ]

    XCTestSourceElement withFailures() {
        XCTestSourceElement delegate = this
        new XCTestSourceElement() {
            @Override
            List<XCTestSourceFileElement> getTestSuites() {
                List<XCTestSourceFileElement> result = []
                result.add(delegate.testSuites[0])
                result.add(new XCTestSourceFileElement() {
                    @Override
                    String getTestSuiteName() {
                        return delegate.testSuites[1].testSuiteName
                    }

                    @Override
                    List<XCTestCaseElement> getTestCases() {
                        List<XCTestCaseElement> testCases = delegate.testSuites[1].testCases
                        return [
                            *testCases[0..1],
                            testCase(testCases[2].name, "XCTAssert(false)", true),
                            *testCases[3..4]
                        ]
                    }
                })
                result.add(new XCTestSourceFileElement() {
                    @Override
                    String getTestSuiteName() {
                        return delegate.testSuites[2].testSuiteName
                    }

                    @Override
                    List<XCTestCaseElement> getTestCases() {
                        return [testCase(delegate.testSuites[2].testCases[0].name, "XCTAssert(false)", true)]
                    }
                })

                return result
            }
        }
    }
}
