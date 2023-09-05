/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.testing.testsuites

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DefaultTestExecutionResult

class JUnitPlatformTestEngineIntegrationTest extends AbstractIntegrationSpec {
    def "can customize an existing test toolchain by adding engines"() {
        given:
        file("src/test/java/TestSuite.java") << """
            import org.junit.platform.suite.api.SelectPackages;
            import org.junit.platform.suite.api.Suite;

            @Suite
            @SelectPackages("org.gradle.suite")
            public class TestSuite {
            }
        """
        file("src/test/java/org/gradle/suite/OneTest.java") << """
            package org.gradle.suite;

            import org.junit.jupiter.api.Test;

            public class OneTest {
                @Test
                void test() {
                }
            }
        """
        file("src/test/java/org/gradle/other/AnotherTest.java") << """
            package org.gradle.other;

            import org.junit.jupiter.api.Test;

            public class AnotherTest {
                @Test
                void test() {
                }
            }
        """
        buildFile << """
            plugins {
                id 'java'
            }

            repositories {
                mavenCentral()
            }

            testing {
                suites {
                    test {
                        useJUnitJupiter {
                            engines.add(engineFactory.create(JUnitPlatformSuiteEngine))
                        }
                        targets.all {
                            testTask.configure {
                                include '**/*Suite.class'
                            }
                        }
                    }
                }
            }

            import java.util.Collections;
            import org.gradle.api.plugins.jvm.internal.testing.engines.JUnitPlatformTestEngine;

            abstract class JUnitPlatformSuiteEngine implements JUnitPlatformTestEngine<JUnitPlatformTestEngine.Parameters.None> {
                Iterable<Dependency> getImplementationDependencies() {
                    return Collections.emptyList();
                }
                Iterable<Dependency> getCompileOnlyDependencies() {
                    return Collections.singletonList(getDependencyFactory().create("org.junit.platform:junit-platform-suite-api:1.10.0"));
                }
                Iterable<Dependency> getRuntimeOnlyDependencies() {
                    return Collections.singletonList(getDependencyFactory().create("org.junit.platform:junit-platform-suite-engine:1.10.0"));
                }
            }
        """

        when:
        succeeds("test")

        then:
        def result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted("org.gradle.suite.OneTest")
        result.testClassByHtml("org.gradle.suite.OneTest").assertTestCount(1, 0, 0)
    }

    def "can specify a custom test engine with configuration"() {
        given:
        file("src/test/java/org/gradle/OneTest.java") << """
            package org.gradle;

            import org.junit.Test;

            public class OneTest {
                @Test
                public void test() {
                }
            }
        """
        file("src/test/java/org/gradle/AnotherTest.java") << """
            package org.gradle;

            import org.junit.Test;

            public class AnotherTest {
                @Test
                public void test() {
                }
            }
        """
        buildFile << """
            plugins {
                id 'java'
            }

            repositories {
                mavenCentral()
            }

            testing {
                suites {
                    test {
                        useJUnitPlatform {
                            engines.add(engineFactory.create(JUnitVintageEngine) { params ->
                                params.apiVersion = '4.13.2'
                                params.engineVersion = '5.9.2'
                            })
                        }
                    }
                }
            }

            import java.util.Collections;
            import org.gradle.api.plugins.jvm.internal.testing.engines.JUnitPlatformTestEngine;

            abstract class JUnitVintageEngine implements JUnitPlatformTestEngine<JUnitVintageEngine.Parameters> {
                Iterable<Dependency> getImplementationDependencies() {
                    return Collections.emptyList();
                }
                Iterable<Dependency> getCompileOnlyDependencies() {
                    return Collections.singletonList(getDependencyFactory().create("junit:junit:" + getParameters().getApiVersion().get()));
                }
                Iterable<Dependency> getRuntimeOnlyDependencies() {
                    return Collections.singletonList(getDependencyFactory().create("org.junit.vintage:junit-vintage-engine:" + getParameters().getEngineVersion().get()));
                }

                interface Parameters extends JUnitPlatformTestEngine.Parameters {
                    Property<String> getApiVersion();
                    Property<String> getEngineVersion();
                }
            }
        """

        when:
        succeeds("test")

        then:
        def result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted("org.gradle.OneTest", "org.gradle.AnotherTest")
        result.testClassByHtml("org.gradle.OneTest").assertTestCount(1, 0, 0)
        result.testClassByHtml("org.gradle.AnotherTest").assertTestCount(1, 0, 0)
    }
}
