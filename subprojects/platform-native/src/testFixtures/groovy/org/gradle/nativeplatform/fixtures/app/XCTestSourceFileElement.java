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
import org.gradle.api.Transformer;
import org.gradle.integtests.fixtures.SourceFile;
import org.gradle.util.CollectionUtils;

import java.util.List;
import java.util.regex.Pattern;

public abstract class XCTestSourceFileElement extends SourceFileElement implements XCTestElement {
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

    public abstract String getTestSuiteName();

    @Override
    public String getSourceSetName() {
        return "test";
    }

    public SourceFile getSourceFile() {
        return sourceFile("swift", getTestSuiteName() + ".swift",
            "import XCTest\n"
                + "\n"
                + "class " + getTestSuiteName() + ": XCTestCase {\n"
                + "    " + renderTestCases() + "\n"
                + "}");
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
    public abstract String getModuleName();

    public void assertTestCasesRan(String output) {
        Pattern testSuiteStartPattern = Pattern.compile(
            "Test Suite '" + getTestSuiteName() + "' started at ");
        if (!testSuiteStartPattern.matcher(output).find()) {
            throw new RuntimeException(String.format("Couldn't find test suite '%s'", getTestSuiteName()));
        }

        for (XCTestCaseElement testCase : getTestCases()) {
            Pattern testCaseStartPattern = Pattern.compile(
                "Test Case '-\\[" + getModuleName() + "." + getTestSuiteName() + " " + testCase.getName() + "]' started.");
            if (!testCaseStartPattern.matcher(output).find()) {
                throw new RuntimeException(String.format("Couldn't find test case '%s.%s' from module '%s'", getTestSuiteName(), testCase.getName(), getModuleName()));
            }

            Pattern testCaseEndPattern = Pattern.compile(
                "Test Case '-\\[" + getModuleName() + "." + getTestSuiteName() + " " + testCase.getName() + "]' " + XCTestSourceElement.toResult(testCase.isExpectFailure() ? 1 : 0));
            if (!testCaseEndPattern.matcher(output).find()) {
                throw new RuntimeException(String.format("Couldn't find result of test case '%s.%s' from module '%s'", getTestSuiteName(), testCase.getName(), getModuleName()));
            }
        }

        Pattern testSuiteSummaryPattern = XCTestSourceElement.toExpectedSummaryOutputPattern(getTestSuiteName(), getTestCount(), getFailureCount());
        if (!testSuiteSummaryPattern.matcher(output).find()) {
            throw new RuntimeException(String.format("Couldn't find summary of test suite '%s'", getTestSuiteName()));
        }
    }

    public XCTestSourceFileElement withImport(final String importName) {
        final XCTestSourceFileElement delegate = this;
        return new XCTestSourceFileElement() {
            @Override
            public List<XCTestCaseElement> getTestCases() {
                return delegate.getTestCases();
            }

            @Override
            public SourceFile getSourceFile() {
                SourceFile sourceFile = delegate.getSourceFile();
                return sourceFile(sourceFile.getPath(), sourceFile.getName(), "import " + importName + "\n" + sourceFile.getContent());
            }

            @Override
            public String getTestSuiteName() {
                return delegate.getTestSuiteName();
            }

            @Override
            public String getModuleName() {
                return delegate.getModuleName();
            }
        };
    }

    public XCTestSourceFileElement withTestableImport(final String importName) {
        final XCTestSourceFileElement delegate = this;
        return new XCTestSourceFileElement() {
            @Override
            public List<XCTestCaseElement> getTestCases() {
                return delegate.getTestCases();
            }

            @Override
            public SourceFile getSourceFile() {
                SourceFile sourceFile = delegate.getSourceFile();
                return sourceFile(sourceFile.getPath(), sourceFile.getName(), "@testable import " + importName + "\n" + sourceFile.getContent());
            }

            @Override
            public String getTestSuiteName() {
                return delegate.getTestSuiteName();
            }

            @Override
            public String getModuleName() {
                return delegate.getModuleName();
            }
        };
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
