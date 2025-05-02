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

import org.gradle.integtests.fixtures.DefaultTestExecutionResult
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

    private void assertTestResults() {
        def testResult = new DefaultTestExecutionResult(file(""))
        // Our test fixture doesn't handle retried tests
        testResult.testClass("org.acme.AcmeTest")
            .assertTestFailedIgnoreMessages("failing")
            .assertTestSkipped("skipped")
            .assertTestPassed("flaky")
            .assertTestPassed("successful")
            .assertTestCount(7, 4, 0) // failing runs 3 times, flaky twice (once with a failure)
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
