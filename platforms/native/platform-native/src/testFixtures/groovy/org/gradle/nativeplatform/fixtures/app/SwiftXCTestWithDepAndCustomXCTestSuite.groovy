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

package org.gradle.nativeplatform.fixtures.app

class SwiftXCTestWithDepAndCustomXCTestSuite extends SwiftXCTest {
    String testSuiteName
    String methodName
    String assertion
    String[] imports
    String[] importsTestable

    SwiftXCTestWithDepAndCustomXCTestSuite(
        String projectName,
        String classToTest,
        String assertion,
        String[] imports,
        String[] importsTestable
    ) {
        super(projectName)
        this.testSuiteName = classToTest + "Test"
        this.methodName = "test" + classToTest
        this.assertion = assertion
        this.imports = imports
        this.importsTestable = importsTestable
    }

    @Override
    List<XCTestSourceFileElement> getTestSuites() {
        def xcTestSourceFileElement = new XCTestSourceFileElement(testSuiteName) {
            @Override
            List<XCTestCaseElement> getTestCases() {
                return [testCase(methodName, assertion)]
            }
        }
        imports.each { item ->
            xcTestSourceFileElement.withImport(item)
        }
        importsTestable.each { item ->
            xcTestSourceFileElement.withTestableImport(item)
        }
        return [xcTestSourceFileElement]
    }
}
