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

import org.gradle.integtests.fixtures.SourceFile;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class SourceTestElement extends SourceElement implements TestElement {
    public int getFailureCount() {
        int count = 0;
        for (TestSuite testSuite : getTestSuites()) {
            for (TestCase testCase : testSuite.getTestCases()) {
                count += testCase.getExpectedResult() == TestCase.Result.FAIL ? 1 : 0;
            }
        }

        return count;
    }

    public int getPassCount() {
        int count = 0;
        for (TestSuite testSuite : getTestSuites()) {
            for (TestCase testCase : testSuite.getTestCases()) {
                count += testCase.getExpectedResult() == TestCase.Result.PASS ? 1 : 0;
            }
        }

        return count;
    }

    @Override
    public int getTestCount() {
        return getFailureCount() + getPassCount();
    }

    @Override
    public String getSourceSetName() {
        return "test";
    }

    public abstract TestSuiteRenderer getRenderer();

    @Override
    public List<SourceFile> getFiles() {
        List<SourceFile> files = new ArrayList<SourceFile>();
        for (TestSuite testSuite : getTestSuites()) {
            files.add(sourceFile("swift", testSuite.getName() + ".swift", getRenderer().render(testSuite)));
        }

        return files;
    }

    public abstract List<TestElement.TestSuite> getTestSuites();

    static TestSuite newTestSuite(final String name, final List<TestCase> testCases) {
        return newTestSuite(name, testCases, Collections.<String>emptyList());
    }

    static TestSuite newTestSuite(final String name, final List<TestCase> testCases, final List<String> testableImports) {
        return newTestSuite(name, testCases, testableImports, Collections.<String>emptyList());
    }

    static TestSuite newTestSuite(final String name, final List<TestCase> testCases, final List<String> testableImports, final List<String> standardImports) {
        return new TestSuite() {
            @Override
            public List<TestCase> getTestCases() {
                return testCases;
            }

            @Override
            public String getName() {
                return name;
            }

            @Override
            public List<String> getTestableImports() {
                return testableImports;
            }

            @Override
            public List<String> getStandardImports() {
                return standardImports;
            }
        };
    }

    static TestCase newTestCase(String name, TestCase.Result result) {
        return newTestCase(name, result, null);
    }

    static TestCase newTestCase(final String name, final TestCase.Result result, @Nullable final String content) {
        return new TestCase() {
            @Override
            public Result getExpectedResult() {
                return result;
            }

            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getContent() {
                return content;
            }
        };
    }
}
