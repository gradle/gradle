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

package org.gradle.testing

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

import static org.gradle.testing.fixture.JUnitCoverage.getLATEST_JUPITER_VERSION

class TestTaskFailOnNoTestIntegrationTest extends AbstractIntegrationSpec {

    def "test succeeds if a test was executed"() {
        createBuildFileWithJUnitJupiter()

        file("src/test/java/SomeTest.java") << """
            public class SomeTest {
                @org.junit.jupiter.api.Test
                public void foo() { }
            }
        """

        expect:
        succeeds("test")
    }

    def "test fails when no test was executed"() {
        createBuildFileWithJUnitJupiter()

        file("src/test/java/NotATest.java") << """
            public class NotATest {}
        """

        expect:
        fails("test")
        failure.assertHasCause("There are test sources present and no filters are applied, but the test task did not discover any tests to execute. This is likely due to a misconfiguration. Please check your test configuration.")
    }

    @Issue("https://github.com/gradle/gradle/issues/30315")
    def "no deprecation warning when only disabled template tests are present"() {
        createBuildFileWithJUnitJupiter()

        file("src/test/java/SomeTest.java") << testClassWithDisabledTemplateTest

        expect:
        succeeds("test")
    }

    def "test is skipped if no test source detected"() {
        buildFile << "apply plugin: 'java'"

        file("src/test/java/not_a_test.txt")

        when:
        succeeds("test")

        then:
        skipped(":test")
    }

    def "test succeeds when no test was executed and shouldFailOnNoDiscoveredTests is false"() {
        createBuildFileWithJUnitJupiter(false)

        file("src/test/java/NotATest.java") << """
            public class NotATest {}
        """

        expect:
        succeeds("test")
    }

    def createBuildFileWithJUnitJupiter(boolean shouldFailOnNoDiscoveredTests = true) {
        buildFile << """
            plugins {
                id 'java'
                id 'jvm-test-suite'
            }
            ${mavenCentralRepository()}
            dependencies {
                testImplementation 'org.junit.jupiter:junit-jupiter:${LATEST_JUPITER_VERSION}'
            }
            testing.suites.test {
                useJUnitJupiter()
                targets.all {
                    testTask.configure {
                        ${shouldFailOnNoDiscoveredTests ? "" : "failOnNoDiscoveredTests = false"}
                    }
                }
            }
        """.stripIndent()
    }

    private static String getTestClassWithDisabledTemplateTest() {
        return """
            import org.junit.jupiter.api.extension.*;
            import org.junit.jupiter.api.*;
            import java.util.stream.Stream;
            import java.util.List;
            import java.util.Collections;

            public class SomeTest {
                @TestTemplate
                @ExtendWith(CustomTemplateInvocationContextProvider.class)
                @Disabled
                public void templateTest() { }

                static public class CustomTemplateInvocationContextProvider implements TestTemplateInvocationContextProvider {
                    public CustomTemplateInvocationContextProvider() { }

                    @Override
                    public boolean supportsTestTemplate(ExtensionContext context) {
                        return true;
                    }

                    @Override
                    public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(
                            ExtensionContext context) {
                        return Stream.of(invocationContext("foo"), invocationContext("bar"));
                    }

                    private TestTemplateInvocationContext invocationContext(String parameter) {
                        return new TestTemplateInvocationContext() {
                            @Override
                            public String getDisplayName(int invocationIndex) {
                                return parameter;
                            }

                            @Override
                            public List<Extension> getAdditionalExtensions() {
                                return Collections.singletonList(new ParameterResolver() {
                                    @Override
                                    public boolean supportsParameter(ParameterContext parameterContext,
                                            ExtensionContext extensionContext) {
                                        return parameterContext.getParameter().getType().equals(String.class);
                                    }

                                    @Override
                                    public Object resolveParameter(ParameterContext parameterContext,
                                            ExtensionContext extensionContext) {
                                        return parameter;
                                    }
                                });
                            }
                        };
                    }
                }
            }
        """
    }
}
