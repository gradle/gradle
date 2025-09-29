/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.integtests.tooling.r930

import org.gradle.integtests.tooling.TestLauncherSpec
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.TestLauncher
import spock.lang.IgnoreRest
import spock.lang.Timeout

@Timeout(120)
@ToolingApiVersion('>=8.8')
@TargetGradleVersion(">=9.3.0")
/**
 * @see org.gradle.integtests.tooling.r70.TestDisplayNameJUnit5CrossVersionSpec and
 * @see org.gradle.integtests.tooling.r88.TestDisplayNameSpockCrossVersionSpec
 */
class TestDisplayNameJUnit4CrossVersionSpec extends TestLauncherSpec {
    @Override
    void addDefaultTests() {
    }

    @Override
    String simpleJavaProject() {
        """
        allprojects{
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                testImplementation("junit:junit:4.13.2")
            }
        }
        """
    }

    def "reports display names of class and method"() {
        file("src/test/java/org/example/SimpleTests.java") << """package org.example;

import org.junit.Test;

public class SimpleTests {

    @Test
    public void test() {
    }
}
"""
        when:
        launchTests { TestLauncher launcher ->
            launcher.withTaskAndTestClasses(':test', ['org.example.SimpleTests'])
        }

        then:
        jvmTestEvents {
            task(":test") {
                suite("Gradle Test Run :test") {
                    suite("Gradle Test Executor") {
                        testClass("org.example.SimpleTests") {
                            testDisplayName "org.example.SimpleTests"
                            test("test") {
                                testDisplayName "test"
                            }
                        }
                    }
                }
            }
        }
    }

    @IgnoreRest // TODO (donat) fix the weird parameterized test display name
    def "reports display names of parameterized tests"() {
        file("src/test/java/org/example/ParameterizedTests.java") << """package org.example;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

@RunWith(Parameterized.class)
public class ParameterizedTests {

    private final int value;
    private final String name;

    public ParameterizedTests(int value, String name) {
        this.value = value;
        this.name = name;
    }

    @Test
    public void parametrized_test() {
        System.out.println(name + " " + value);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {1, "first"},
                {2, "second"}
        });
    }
}
"""

        when:
        launchTests { TestLauncher launcher ->
            launcher.withTaskAndTestClasses(':test', ['org.example.ParameterizedTest*'])
        }

        then:
        jvmTestEvents {
            task(":test") {
                suite("Gradle Test Run :test") {
                    suite("Gradle Test Executor") {
                        testClass("org.example.ParameterizedTests") {
                            testDisplayName "org.example.ParameterizedTests"
                            testClass("[0]") {
                                testDisplayName "[0]"
                                test("parametrized_test[0](org.example.ParameterizedTests)") {
                                    //testDisplayName "parametrized_test[0](org.example.ParameterizedTests)" // JUnit4 does not provide detailed information for parameterized tests
                                }
                            }
                            testClass("[1]") {
                                testDisplayName "[1]"
                                test("parametrized_test[1](org.example.ParameterizedTests)") {
                                    //testDisplayName "parametrized_test[1](org.example.ParameterizedTests)"
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
