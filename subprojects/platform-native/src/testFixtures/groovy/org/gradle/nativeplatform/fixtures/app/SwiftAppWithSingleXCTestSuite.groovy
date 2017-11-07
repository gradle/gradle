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

class SwiftAppWithSingleXCTestSuite extends MainWithXCTestSourceElement implements AppElement {
    final SwiftApp main = new SwiftApp()
    final XCTestSourceElement test = new XCTestSourceElement(main.projectName) {
        @Override
        List<XCTestSourceFileElement> getTestSuites() {
            return [new XCTestSourceFileElement("CombinedTests") {
                final delegate = new SwiftAppTest(main, main.greeter, main.sum, main.multiply)

                @Override
                List<XCTestCaseElement> getTestCases() {
                    return delegate.sumTest.testCases + delegate.greeterTest.testCases + delegate.multiplyTest.testCases
                }
            }.withTestableImport(main.moduleName)]
        }
    }

    String expectedOutput = main.expectedOutput

    SwiftAppWithSingleXCTestSuite() {
        super('app')
    }
}
