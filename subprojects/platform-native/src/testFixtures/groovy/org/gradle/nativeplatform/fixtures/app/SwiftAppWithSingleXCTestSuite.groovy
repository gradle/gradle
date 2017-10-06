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
import org.gradle.test.fixtures.file.TestFile

class SwiftAppWithSingleXCTestSuite extends XCTestSourceElement implements AppElement {
    final app = new SwiftApp()
    final test = new XCTestSourceFileElement() {
        final delegate = new SwiftAppTest(app.greeter, app.sum, app.multiply)

        @Override
        String getTestSuiteName() {
            return "CombinedTests"
        }

        @Override
        List<XCTestCaseElement> getTestCases() {
            return delegate.sumTest.testCases + delegate.greeterTest.testCases + delegate.multiplyTest.testCases
        }

        @Override
        String getModuleName() {
            return delegate.sumTest.moduleName
        }
    }.withTestableImport("App")

    List<SourceFile> files = app.files + test.files
    List<XCTestSourceFileElement> testSuites = [test]

    String expectedOutput = app.expectedOutput

    @Override
    void writeToProject(TestFile projectDir) {
        app.writeToProject(projectDir)
        test.writeToProject(projectDir)
    }
}
