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

package org.gradle.nativeplatform.fixtures.app;

import com.google.common.collect.Lists;
import org.gradle.integtests.fixtures.SourceFile;
import org.gradle.integtests.fixtures.TestExecutionResult;
import org.gradle.util.internal.CollectionUtils;

import java.util.List;

public abstract class XCTestSourceElement extends SwiftSourceElement implements XCTestElement {
    public XCTestSourceElement(String projectName) {
        super(projectName);
    }

    @Override
    public String getSourceSetName() {
        return "test";
    }

    @Override
    public List<SourceFile> getFiles() {
        List<SourceFile> result = Lists.newArrayList(CollectionUtils.collect(getTestSuites(), XCTestSourceFileElement::getSourceFile));

        result.add(getLinuxMainSourceFile(getTestSuites()));
        return result;
    }

    public static SourceFile getLinuxMainSourceFile(List<XCTestSourceFileElement> testSuites) {
        StringBuilder content = new StringBuilder();
        content.append("import XCTest\n");

        for (XCTestSourceFileElement testSuite : testSuites) {
            content.append("extension " + testSuite.getTestSuiteName() + " {\n");
            content.append("  public static var allTests = [\n");
            for (XCTestCaseElement testCase : testSuite.getTestCases()) {
                content.append("    (\"" + testCase.getName() + "\", " + testCase.getName() + "),\n");
            }
            content.append("  ]\n");
            content.append("}\n");
        }

        content.append("XCTMain([\n");
        for (XCTestSourceFileElement testSuite : testSuites) {
            content.append("  testCase(" + testSuite.getTestSuiteName() + ".allTests),\n");
        }
        content.append("])\n");

        return new SourceFile("swift", "LinuxMain.swift", content.toString());
    }

    @Override
    public int getFailureCount() {
        int result = 0;
        for (XCTestElement element : getTestSuites()) {
            result += element.getFailureCount();
        }
        return result;
    }

    @Override
    public int getPassCount() {
        int result = 0;
        for (XCTestElement element : getTestSuites()) {
            result += element.getPassCount();
        }
        return result;
    }

    @Override
    public int getTestCount() {
        int result = 0;
        for (XCTestElement element : getTestSuites()) {
            result += element.getTestCount();
        }
        return result;
    }

    public abstract List<XCTestSourceFileElement> getTestSuites();

    public void assertTestCasesRan(TestExecutionResult testExecutionResult) {
        assertTestCasesRanInSuite(testExecutionResult, getTestSuites());
    }

    static void assertTestCasesRanInSuite(TestExecutionResult testExecutionResult, List<XCTestSourceFileElement> testSuites) {
        for (XCTestSourceFileElement element : testSuites) {
            element.assertTestCasesRan(testExecutionResult.testClass(element.getTestSuiteName()));
        }
    }

    @Override
    public String getModuleName() {
        return super.getModuleName() + "Test";
    }
}
