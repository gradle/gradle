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

import java.util.regex.Pattern

class SwiftXcTestTestApp extends SourceTestElement {
    private final List<TestElement.TestSuite> testSuites
    final TestSuiteRenderer renderer = new SwiftXcTestRenderer()

    SwiftXcTestTestApp(List<TestElement.TestSuite> testSuites) {
        this.testSuites = testSuites
    }

    @Override
    List<TestElement.TestSuite> getTestSuites() {
        return testSuites
    }

    Pattern getExpectedSummaryOutputPattern() {
        Pattern.compile(
            "Test Suite 'All tests' ${toResult(failureCount)} at .+\n\\s+Executed ${testCount} ${plurializeIf('test', testCount)}, with ${failureCount} ${plurializeIf('failure', failureCount)} \\(0 unexpected\\)",
            Pattern.MULTILINE | Pattern.DOTALL)
    }

    private static String toResult(int failureCount) {
        if (failureCount > 0) {
            return "failed"
        }
        return "passed"
    }

    private static String plurializeIf(String noun, int count) {
        if (count > 1 || count == 0) {
            return "${noun}s"
        }
        return noun
    }

    @Override
    List<SourceFile> getFiles() {
        return super.getFiles() + [sourceFile("resources", "Info.plist", """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0"/>
            <dict/>
            </dict>
            </plist>
        """)]
    }
}
