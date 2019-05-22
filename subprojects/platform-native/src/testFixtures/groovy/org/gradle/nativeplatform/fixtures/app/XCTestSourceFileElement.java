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

import com.google.common.base.Joiner;
import com.google.common.collect.Sets;
import org.gradle.api.Transformer;
import org.gradle.integtests.fixtures.SourceFile;
import org.gradle.integtests.fixtures.TestClassExecutionResult;
import org.gradle.util.CollectionUtils;
import org.hamcrest.CoreMatchers;

import java.util.List;
import java.util.Set;

public abstract class XCTestSourceFileElement extends SourceFileElement implements XCTestElement {
    private final String testSuiteName;
    private final Set<String> imports = Sets.newLinkedHashSet();
    private final Set<String> testableImports = Sets.newLinkedHashSet();

    public XCTestSourceFileElement(String testSuiteName) {
        this.testSuiteName = testSuiteName;
        withImport("XCTest");
    }

    @Override
    public int getFailureCount() {
        int result = 0;
        for (XCTestCaseElement element : getTestCases()) {
            if (element.isExpectFailure()) {
                result++;
            }
        }
        return result;
    }

    @Override
    public int getPassCount() {
        int result = 0;
        for (XCTestCaseElement element : getTestCases()) {
            if (!element.isExpectFailure()) {
                result++;
            }
        }
        return result;
    }

    @Override
    public int getTestCount() {
        return getTestCases().size();
    }

    public final String getTestSuiteName() {
        return testSuiteName;
    }

    @Override
    public String getSourceSetName() {
        return "test";
    }

    @Override
    public SourceFile getSourceFile() {
        return sourceFile("swift", getTestSuiteName() + ".swift",
                renderImports()
                + "\n"
                + "class " + getTestSuiteName() + ": XCTestCase {\n"
                + "    " + renderTestCases() + "\n"
                + "}");
    }

    private String renderImports() {
        StringBuilder sb = new StringBuilder();
        for (String importModule : getImports()) {
            sb.append("import ").append(importModule).append('\n');
        }
        for (String importModule : getTestableImports()) {
            sb.append("@testable import ").append(importModule).append('\n');
        }
        return sb.toString();
    }

    private String renderTestCases() {
        return Joiner.on("\n").join(
            CollectionUtils.collect(getTestCases(), new Transformer<String, XCTestCaseElement>() {
                @Override
                public String transform(XCTestCaseElement testCase) {
                    return testCase.getContent() + "\n";
                }
            })
        );
    }

    protected XCTestCaseElement passingTestCase(String methodName) {
        return testCase(methodName, "XCTAssert(true)", false);
    }

    protected XCTestCaseElement failingTestCase(String methodName) {
        return testCase(methodName, "XCTAssert(false)", true);
    }

    protected XCTestCaseElement testCase(String methodName, String assertion) {
        return testCase(methodName, assertion, false);
    }

    protected XCTestCaseElement testCase(final String methodName, final String assertion, final boolean isExpectFailure) {
        return new XCTestCaseElement() {
            @Override
            public String getContent() {
                return "func " + methodName + "() {\n"
                    + "    " + assertion + "\n"
                    + "}";
            }

            @Override
            public String getName() {
                return methodName;
            }

            @Override
            public boolean isExpectFailure() {
                return isExpectFailure;
            }
        };
    }

    public abstract List<XCTestCaseElement> getTestCases();

    @SuppressWarnings("unchecked")
    public void assertTestCasesRan(TestClassExecutionResult testExecutionResult) {
        testExecutionResult.assertTestCount(getTestCount(), getFailureCount(), 0);

        for (XCTestCaseElement testCase : getTestCases()) {
            if (testCase.isExpectFailure()) {
                testExecutionResult.assertTestFailed(testCase.getName(), CoreMatchers.anything());
            } else {
                testExecutionResult.assertTestPassed(testCase.getName());
            }
        }
    }

    public Set<String> getImports() {
        return imports;
    }

    public XCTestSourceFileElement withImport(String importName) {
        imports.add(importName);
        return this;
    }

    public Set<String> getTestableImports() {
        return testableImports;
    }

    public XCTestSourceFileElement withTestableImport(String importName) {
        testableImports.add(importName);
        return this;
    }

    public SourceFile emptyInfoPlist() {
        return sourceFile("resources", "Info.plist",
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n"
                + "<plist version=\"1.0\">\n"
                + "<dict>\n"
                + "</dict>\n"
                + "</plist>");
    }
}
