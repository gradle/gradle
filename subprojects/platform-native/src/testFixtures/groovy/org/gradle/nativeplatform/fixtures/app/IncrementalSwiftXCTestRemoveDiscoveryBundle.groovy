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

import org.gradle.integtests.fixtures.SourceFile

class IncrementalSwiftXCTestRemoveDiscoveryBundle extends IncrementalSwiftXCTestElement  {
    private static final String PASSING_ASSERTION = "XCTAssert(true)"
    String moduleName = "RemoveDiscovery"

    final XCTestSourceFileElement fooTestSuite = new XCTestSourceFileElement() {
        String testSuiteName = "FooTestSuite"
        List<XCTestCaseElement> testCases = [
            testCase("testA", PASSING_ASSERTION),
            testCase("testB", PASSING_ASSERTION)
        ]

        @Override
        String getModuleName() {
            moduleName
        }
    }

    final XCTestSourceFileElement alternateFooTestSuite = new XCTestSourceFileElement() {
        String testSuiteName = "FooTestSuite"
        List<XCTestCaseElement> testCases = [
            testCase("testA", PASSING_ASSERTION),
        ]

        @Override
        String getModuleName() {
            moduleName
        }
    }

    final XCTestSourceFileElement barTestSuite = new XCTestSourceFileElement() {
        String testSuiteName = "BarTestSuite"
        List<XCTestCaseElement> testCases = [
            testCase("testA", PASSING_ASSERTION),
        ]

        @Override
        String getModuleName() {
            moduleName
        }
    }

    List<XCTestSourceFileElement> testSuites = [fooTestSuite, barTestSuite]
    List<XCTestSourceFileElement> alternateTestSuites = [alternateFooTestSuite]

    List<IncrementalElement.Transform> incrementalChanges = [
        modify(fooTestSuite, alternateFooTestSuite),
        delete(barTestSuite),
        preserve(new SourceElement() {
            List<SourceFile> files = [barTestSuite.emptyInfoPlist()]
        })
    ]
}
