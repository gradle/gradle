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
                            addEngine(JUnitPlatformSuiteEngine)
                        }
                        targets.all {
                            testTask.configure {
                                include '**/*Suite.class'
                            }
                        }
                    }
                }
            }

            import java.util.Collections
            import org.gradle.api.plugins.jvm.testing.engines.JUnitPlatformTestEngine
            import org.gradle.api.plugins.jvm.testing.engines.JUnitPlatformTestEngineParameters

            abstract class JUnitPlatformSuiteEngine implements JUnitPlatformTestEngine<JUnitPlatformTestEngineParameters.None> {
                Iterable<Dependency> getImplementationDependencies() {
                    return []
                }
                Iterable<Dependency> getCompileOnlyDependencies() {
                    return [ getDependencyFactory().create("org.junit.platform:junit-platform-suite-api:1.10.0") ]
                }
                Iterable<Dependency> getRuntimeOnlyDependencies() {
                    return [ getDependencyFactory().create("org.junit.platform:junit-platform-suite-engine:1.10.0") ]
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

    def "can specify a custom test engine with parameters"() {
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
                            addEngine(JUnitVintageEngine) {
                                apiVersion = '4.13.2'
                                engineVersion = '5.9.2'
                            }
                        }
                    }
                }
            }

            import java.util.Collections
            import org.gradle.api.plugins.jvm.testing.engines.JUnitPlatformTestEngine
            import org.gradle.api.plugins.jvm.testing.engines.JUnitPlatformTestEngineConfigurationParameters

            abstract class JUnitVintageEngine implements JUnitPlatformTestEngine<JUnitVintageEngine.Parameters> {
                Iterable<Dependency> getImplementationDependencies() {
                    return []
                }
                Iterable<Dependency> getCompileOnlyDependencies() {
                    return [ getDependencyFactory().create("junit:junit:" + getParameters().getApiVersion().get()) ]
                }
                Iterable<Dependency> getRuntimeOnlyDependencies() {
                    return [ getDependencyFactory().create("org.junit.vintage:junit-vintage-engine:" + getParameters().getEngineVersion().get()) ]
                }

                interface Parameters extends JUnitPlatformTestEngineParameters<JUnitPlatformTestEngineConfigurationParameters.None> {
                    Property<String> getApiVersion()
                    Property<String> getEngineVersion()
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

    def "can specify a custom test engine with configuration parameters"() {
        given:
        file("src/main/java/com/example/Belly.java") << """
            package com.example;

            public class Belly {
                private int cukes;

                public void eat(int cukes) {
                    this.cukes = cukes;
                }

                public String getSound(int waitingTime) {
                    if (cukes > 41 && waitingTime >= 1) {
                        return "growl";
                    } else {
                        return "silent";
                    }
                }
            }
        """
        file("src/test/resources/com/example/belly.feature") << """
            Feature: Belly

            Scenario: a few cukes
                Given I have 42 cukes in my belly
                When I wait 1 hour
                Then my belly should growl
        """
        file("src/test/java/com/example/RunCucumberTest.java") << """
            package com.example;

            import org.junit.platform.suite.api.ConfigurationParameter;
            import org.junit.platform.suite.api.IncludeEngines;
            import org.junit.platform.suite.api.SelectClasspathResource;
            import org.junit.platform.suite.api.Suite;

            import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;

            @Suite
            @IncludeEngines("cucumber")
            @SelectClasspathResource("com/example")
            @ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.example")
            public class RunCucumberTest {
            }
        """
        file("src/test/java/com/example/StepDefinitions.java") << """
            package com.example;

            import io.cucumber.java.en.*;
            import static org.junit.jupiter.api.Assertions.*;

            public class StepDefinitions {
                private Belly belly;
                private String sound;

                @Given("I have {int} cukes in my belly")
                public void I_have_cukes_in_my_belly(int cukes) {
                    belly = new Belly();
                    belly.eat(cukes);
                }

                @When("I wait {int} hour")
                public void I_wait_hour(int waitingTime) {
                    sound = belly.getSound(1);
                }

                @Then("my belly should growl")
                public void my_belly_should_growl() {
                    assertEquals("growl", sound);
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
                            addEngine(CucumberTestEngine) {
                                cucumberVersion = '7.13.0'
                                platformSuiteVersion = '1.10.0'
                                jupiterVersion = '5.9.2'
                                configurationParameters.with {
                                    namingStrategy = "long"
                                }
                            }
                        }
                    }
                }
            }

            import java.util.Collections;
            import org.gradle.api.plugins.jvm.testing.engines.JUnitPlatformTestEngine;
            import org.gradle.api.plugins.jvm.testing.engines.JUnitPlatformTestEngineConfigurationParameters;

            abstract class CucumberTestEngine implements JUnitPlatformTestEngine<CucumberTestEngine.Parameters> {
                Iterable<Dependency> getImplementationDependencies() {
                    return [
                        getDependencyFactory().create("io.cucumber:cucumber-java:" + getParameters().getCucumberVersion().get()),
                        getDependencyFactory().create("io.cucumber:cucumber-junit-platform-engine:" + getParameters().getCucumberVersion().get()),
                        getDependencyFactory().create("org.junit.platform:junit-platform-suite:" + getParameters().getPlatformSuiteVersion().get()),
                        getDependencyFactory().create("org.junit.jupiter:junit-jupiter-api:" + getParameters().getJupiterVersion().get())
                    ]
                }

                Iterable<Dependency> getCompileOnlyDependencies() {
                    return []
                }
                
                Iterable<Dependency> getRuntimeOnlyDependencies() {
                    return []
                }

                public CommandLineArgumentProvider mapToCommandLineArguments() {
                    return new CommandLineArgumentProvider() {
                        @Override
                        public Iterable<String> asArguments() {
                            return Collections.singletonList("-Dcucumber.junit-platform.naming-strategy=" + getParameters().getConfigurationParameters().getNamingStrategy().get());
                        }
                    };
                }

                interface Parameters extends JUnitPlatformTestEngineParameters<CucumberTestEngine.ConfigurationParameters> {
                    Property<String> getCucumberVersion()
                    Property<String> getPlatformSuiteVersion()
                    Property<String> getJupiterVersion()
                }

                abstract static class ConfigurationParameters implements JUnitPlatformTestEngineConfigurationParameters {
                    abstract Property<String> getNamingStrategy()
                }
            }
        """

        when:
        succeeds("test")

        then:
        def result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted("com.example.RunCucumberTest")
        result.testClassByHtml("com.example.RunCucumberTest").assertTestCount(1, 0, 0)
        result.testClassByHtml("com.example.RunCucumberTest").assertTestsExecuted("Belly - a few cukes")
    }
}
