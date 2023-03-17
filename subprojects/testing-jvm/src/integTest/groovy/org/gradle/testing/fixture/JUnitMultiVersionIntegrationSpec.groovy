/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.testing.fixture

import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.junit.Assume

import java.util.regex.Pattern

import static org.gradle.test.fixtures.junitplatform.JUnitPlatformTestRewriter.rewriteWithJupiter
import static org.gradle.test.fixtures.junitplatform.JUnitPlatformTestRewriter.rewriteWithVintage

/**
 * To avoid rework when testing JUnit Platform (a.k.a JUnit 5), we'd like to reuse previous test cases in following aspects:
 *
 *  <ul>
 *   <li>1. Rewrite existing JUnit 4 tests with Jupiter annotations to test Jupiter engine, e.g. @org.junit.Test -> @org.junit.jupiter.api.Test </li>
 *   <li>2. Replace existing JUnit 4 dependencies with Vintage to test Vintage engine's backward compatibility.</li>
 * </ul>
 *
 * {@see org.gradle.test.fixtures.junitplatform.JUnitPlatformTestRewriter}
 *
 */
abstract class JUnitMultiVersionIntegrationSpec extends MultiVersionIntegrationSpec {
    // JUnit 5's test case name contains parentheses which might break test assertion, e.g. testMethod() PASSED -> testMethod PASSED
    static final Pattern TEST_CASE_RESULT_PATTERN = ~/(.*)(\w+)\(\) (PASSED|FAILED|SKIPPED|STANDARD_OUT)/

    def setup() {
        executer.withRepositoryMirrors()
    }

    @Override
    protected ExecutionResult succeeds(String... tasks) {
        rewriteProjectDirectory()
        assertUsingJUnitPlatform()
        super.succeeds(tasks)
    }

    @Override
    protected ExecutionFailure fails(String... tasks) {
        rewriteProjectDirectory()
        assertUsingJUnitPlatform()
        super.fails(tasks)
    }

    @Override
    void outputContains(String string) {
        assert getOutput().contains(string.trim())
    }

    @Override
    String getOutput() {
        return outputWithoutTestCaseParentheses()
    }

    private void rewriteProjectDirectory() {
        if (isJupiter()) {
            rewriteWithJupiter(executer.workingDir, dependencyVersion)
        } else if (isVintage()) {
            rewriteWithVintage(executer.workingDir, dependencyVersion)
        }
    }

    private assertUsingJUnitPlatform() {
        if (isJupiter() || isVintage()) {
            File buildFile = new File(executer.workingDir, 'build.gradle')
            if (buildFile.exists()) {
                String text = buildFile.text
                assert text.contains('useJUnitPlatform')
                assert !text.contains('useJUnit()')
                assert !text.contains('useJUnit {')
                assert !text.contains('useTestNG')
            }
        }
    }

    protected List<String> getDependencyNotation() {
        if (isJupiter()) {
            return ["org.junit.jupiter:junit-jupiter-api:${dependencyVersion}", "org.junit.jupiter:junit-jupiter-engine:${dependencyVersion}"]
        } else if (isVintage()) {
            return ["org.junit.vintage:junit-vintage-engine:${dependencyVersion}","junit:junit:4.13"]
        } else {
            return ["junit:junit:${version}"]
        }
    }

    protected ignoreWhenJupiter() {
        Assume.assumeFalse(isJupiter())
    }

    protected ignoreWhenJUnitPlatform() {
        Assume.assumeFalse(isJUnitPlatform())
    }

    protected ignoreWhenJUnit4() {
        Assume.assumeFalse(isJUnit4())
    }

    static String getTestFramework() {
        isJUnitPlatform() ? "JUnitPlatform" : "JUnit"
    }

    static boolean isJUnitPlatform() {
        isJupiter() || isVintage()
    }

    static boolean isJUnit4() {
        return version.toString() == JUnitCoverage.NEWEST
    }

    static boolean isVintage() {
        return version.toString().startsWith("Vintage")
    }

    static boolean isJupiter() {
        return version.toString().startsWith("Jupiter")
    }

    static String getDependencyVersion() {
        if (isJupiter()) {
            return version.toString().substring("Jupiter:".length())
        } else if (isVintage()) {
            return version.toString().substring("Vintage:".length())
        } else {
            return version
        }
    }

    String outputWithoutTestCaseParentheses() {
        List<String> lines = super.getOutput().split(/\n/)
        return lines.collect {
            if (TEST_CASE_RESULT_PATTERN.matcher(it).matches()) {
                TEST_CASE_RESULT_PATTERN.matcher(it).replaceFirst('$1$2 $3')
            } else {
                it
            }
        }.join('\n')
    }
}
