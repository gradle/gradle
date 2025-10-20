/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.testing

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.testing.fixture.TestNGCoverage

class NonClassBasedTestingIntegrationTest extends AbstractIntegrationSpec {
    private engineJarLibPath

    def setup() {
        def version = IntegrationTestBuildContext.INSTANCE.getVersion().getBaseVersion().version
        // TODO: there's probably a better way to get this on the path
        engineJarLibPath = IntegrationTestBuildContext.TEST_DIR.file("../../software/testing-base/build/libs/gradle-testing-base-$version-test-fixtures.jar").path
    }

    def "resource-based test engine detects tests and executes tests (also scanning for test classes = #scanningForTestClasses, excluding jupiter engine = #excludingJupiter)"() {
        buildFile << """
            plugins {
                id 'java'
                id 'jvm-test-suite'
            }

            ${mavenCentralRepository()}

            testing.suites.test {
                useJUnitJupiter()

                dependencies {
                    implementation files('${engineJarLibPath}')
                }

                targets.all {
                    testTask.configure {
                        setScanForTestClasses($scanningForTestClasses)
                        setScanForTestDefinitions(true)
                        testDefinitionDirs.from(project.layout.projectDirectory.file("src/test/rbts"))

                        options {
                            includeEngines("rbt-engine")
                            if ($excludingJupiter) {
                                excludeEngines("junit-jupiter")
                            }
                        }
                    }
                }
            }
        """

        // Avoid no source error (TODO: remove this)
        file("src/test/java/NotATest.java") << """
            public class NotATest {}
        """

        file("src/test/rbts/SomeTestSpec.rbt") << """<?xml version="1.0" encoding="UTF-8" ?>
            <tests>
                <test name="foo" />
                <test name="bar" />
            </tests>
        """
        file("src/test/rbts/subSomeOtherTestSpec.rbt") << """<?xml version="1.0" encoding="UTF-8" ?>
            <tests>
                <test name="other" />
            </tests>
        """

        expect:
        succeeds("test", "-S", "--info")

        and:
        outputContains("INFO: Executing test: Test [file=SomeTestSpec.rbt, name=foo]")
        outputContains("INFO: Executing test: Test [file=SomeTestSpec.rbt, name=bar]")
        outputContains("INFO: Executing test: Test [file=subSomeOtherTestSpec.rbt, name=other]")

        where:
        scanningForTestClasses  | excludingJupiter
        true                    | true
        true                    | false
        false                   | true
        false                   | false
    }

    def "can't do resource-based testing with unsupported test framework #testFrameworkName"() {
        buildFile << """
            plugins {
                id 'java'
                id 'jvm-test-suite'
            }

            ${mavenCentralRepository()}

            testing.suites.test {
                $testFrameworkMethod

                dependencies {
                    implementation 'org.testng:testng:${TestNGCoverage.NEWEST}'
                }

                targets.all {
                    testTask.configure {
                        setScanForTestDefinitions(true)
                        testDefinitionDirs.from(project.layout.projectDirectory.file("src/test/rbts"))
                    }
                }
            }
        """

        // Avoid no source error (TODO: remove this)
        file("src/test/java/NotATest.java") << """
            public class NotATest {}
        """

        expect:
        fails("test")

        and:
        failure.assertHasCause("The $testFrameworkName test framework does not support resource-based testing.")

        where:
        testFrameworkName | testFrameworkMethod
        "Test NG"         | "useTestNG()"
        "JUnit"           | "useJUnit()"
    }
}
