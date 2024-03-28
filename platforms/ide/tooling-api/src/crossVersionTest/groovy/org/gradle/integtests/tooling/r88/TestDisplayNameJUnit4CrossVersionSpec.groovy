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

package org.gradle.integtests.tooling.r88

import org.gradle.integtests.tooling.TestLauncherSpec
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.tooling.TestLauncher
import spock.lang.Timeout

@Timeout(120)
@ToolingApiVersion('>=8.8')
@TargetGradleVersion(">=8.8")
@Requires(UnitTestPreconditions.Jdk17OrEarlier)
/**
 * @see org.gradle.integtests.tooling.r70.TestDisplayNameJUnit5CrossVersionSpec and
 * @see TestDisplayNameSpockCrossVersionSpec
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
                            testDisplayName "SimpleTests"
                            test("test") {
                                testDisplayName "test"
                            }
                        }
                    }
                }
            }
        }
    }

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
                            testDisplayName "ParameterizedTests"
                            test("parametrized_test[0]") {
                                testDisplayName "parametrized_test[0]" // JUnit4 does not provide detailed information for parameterized tests
                            }
                            test("parametrized_test[1]") {
                                testDisplayName "parametrized_test[1]"
                            }
                        }
                    }
                }
            }
        }
    }
}
