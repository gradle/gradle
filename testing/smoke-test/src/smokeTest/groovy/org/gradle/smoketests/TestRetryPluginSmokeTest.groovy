/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.smoketests

import org.gradle.api.internal.tasks.testing.report.generic.GenericHtmlTestExecutionResult
import org.gradle.api.tasks.testing.TestResult
import org.gradle.integtests.fixtures.JUnitXmlTestExecutionResult
import org.gradle.test.fixtures.file.TestFile
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Issue

class TestRetryPluginSmokeTest extends AbstractSmokeTest {
    def setup() {
        sourceFile()
        testSourceFile()
        buildFile << """
            plugins {
                id "java"
                id "org.gradle.test-retry" version "${TestedVersions.testRetryPlugin}"
            }

            ${mavenCentralRepository()}
        """
    }

    @Issue('https://plugins.gradle.org/plugin/org.gradle.test-retry')
    def 'test retry plugin'() {
        given:
        buildFile << """
            dependencies {
                testImplementation("org.junit.jupiter:junit-jupiter:5.7.1")
                testRuntimeOnly("org.junit.platform:junit-platform-launcher")
            }

            test {
                def markerFile = file("marker.file")
                doFirst {
                    markerFile.delete()
                }

                useJUnitPlatform()
                retry {
                    maxRetries = 2
                }
            }
        """

        when:
        def result = runner('test').buildAndFail()
        then:
        assertTaskFailed(result, ":test")
        assertTestResults()
    }

    @Issue('https://plugins.gradle.org/plugin/org.gradle.test-retry')
    def 'test retry plugin with test suites'() {
        given:
        buildFile << """
            testing {
                suites {
                    test {
                        useJUnitJupiter('5.7.1')
                        targets {
                            all {
                                testTask.configure {
                                    def markerFile = file("marker.file")
                                    doFirst {
                                        markerFile.delete()
                                    }
                                    retry {
                                        maxRetries = 2
                                    }
                                }
                            }
                        }
                    }
                }
            }
        """

        when:
        def result = runner('test').buildAndFail()
        then:
        assertTaskFailed(result, ":test")
        assertTestResults()
    }

    @Issue('https://plugins.gradle.org/plugin/org.gradle.test-retry')
    def 'test retry plugin does not rerun passed tests that are not declared as methods'() {
        given:
        buildFile << """
            dependencies {
                testImplementation("org.junit.jupiter:junit-jupiter:5.14.2")
                testImplementation("com.tngtech.archunit:archunit-junit5:1.4.2")
                testRuntimeOnly("org.junit.platform:junit-platform-launcher")
            }

            test {
                def markerFile = file("marker.file")
                doFirst {
                    markerFile.delete()
                }

                useJUnitPlatform()
                retry {
                    maxRetries = 2
                }
            }
        """
        file("src/test/java/org/acme/AcmeArchTest.java") << """
            package org.acme;

            import com.tngtech.archunit.junit.AnalyzeClasses;
            import com.tngtech.archunit.junit.ArchTest;
            import com.tngtech.archunit.lang.ArchRule;

            import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

            @AnalyzeClasses(packages = "org.acme")
            public class AcmeArchTest {
                @ArchTest
                static final ArchRule rule = classes().should().resideInAPackage("org.acme");
            }
        """

        when:
        def result = runner('test').buildAndFail()

        then:
        assertTaskFailed(result, ":test")
        def results = new JUnitXmlTestExecutionResult(new TestFile(testProjectDir))
        results.testClass("org.acme.AcmeTest").assertTestCount(7, 1, 4)
        results.testClass("org.acme.AcmeArchTest").assertTestCount(1, 0)
    }

    private void assertTestResults() {
        def xmlResults = new JUnitXmlTestExecutionResult(new TestFile(testProjectDir)).testClass("org.acme.AcmeTest")
        xmlResults.assertTestCount(7, 1, 4)
        xmlResults.assertTestPassed("successful")
        xmlResults.assertTestSkipped("skipped")

        def htmlResults = new GenericHtmlTestExecutionResult(testProjectDir, "build/reports/tests/test")
        htmlResults.assertTestPathsExecuted(
            ":org.acme.AcmeTest:successful()",
            ":org.acme.AcmeTest:flaky()",
            ":org.acme.AcmeTest:failing()",
            ":org.acme.AcmeTest:skipped()"
        )
        def failing = htmlResults.testPath(":org.acme.AcmeTest:failing()")
        assert failing.singleRootRunCount == 3
        failing.singleRootWithRun(1).assertHasResult(TestResult.ResultType.FAILURE)
        failing.singleRootWithRun(2).assertHasResult(TestResult.ResultType.FAILURE)
        failing.singleRootWithRun(3).assertHasResult(TestResult.ResultType.FAILURE)

        def flaky = htmlResults.testPath(":org.acme.AcmeTest:flaky()")
        assert flaky.singleRootRunCount == 2
        flaky.singleRootWithRun(1).assertHasResult(TestResult.ResultType.FAILURE)
        flaky.singleRootWithRun(2).assertHasResult(TestResult.ResultType.SUCCESS)
    }

    static void assertTaskFailed(BuildResult result, String task) {
        assert result.task(task).outcome == TaskOutcome.FAILED
    }


    private TestFile testSourceFile() {
        file("src/test/java/org/acme/AcmeTest.java") << """
package org.acme;

import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.Assumptions;
import static org.junit.jupiter.api.Assertions.fail;

class AcmeTest {

    @Test
    void successful() {
        new Acme().otherFunctionality();
    }

    @Test
    void flaky() {
        new Acme().functionality();
    }

    @Test
    void failing() {
        fail();
    }

    @Test
    void skipped() {
        Assumptions.assumeTrue(false);
    }
}
        """
    }

    private TestFile sourceFile() {
        file("src/main/java/org/acme/Acme.java") << """
package org.acme;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Acme {

    public void functionality() {
        try {
            Path marker = Paths.get("marker.file");
            if (!Files.exists(marker)) {
                Files.write(marker, "mark".getBytes());
                throw new RuntimeException("fail me!");
            }
            Files.write(marker, "again".getBytes());
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    public void otherFunctionality() {
        System.out.println("I'm doing things");
    }
}
"""
    }

}
