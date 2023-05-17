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

package org.gradle.testing.junit.junit4

import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.testing.junit.AbstractJUnitIgnoreClassIntegrationTest

abstract class AbstractJUnit4IgnoreClassIntegrationTest extends AbstractJUnitIgnoreClassIntegrationTest {
    def "can handle class level ignored tests with custom runner"() {
        given:
        executer.noExtraLogging()
        file ('src/test/java/org/gradle/CustomIgnoredTest.java') << """
            package org.gradle;

            ${testFrameworkImports}
            import org.junit.runner.notification.Failure;
            import org.junit.runner.notification.RunNotifier;

            import java.util.ArrayList;
            import java.util.List;

            @Ignore
            @RunWith(org.gradle.CustomIgnoredTest.TheRunner.class)
            public class CustomIgnoredTest {
                static int count = 0;

                public boolean doSomething() {
                    return true;
                }

                public static class TheRunner extends Runner {
                    List descriptions = new ArrayList();
                    private final Class<? extends org.gradle.CustomIgnoredTest> testClass;
                    private final org.gradle.CustomIgnoredTest testContainingInstance;
                    private Description testSuiteDescription;

                    public TheRunner(Class<? extends org.gradle.CustomIgnoredTest> testClass) {
                        this.testClass = testClass;
                        testContainingInstance = reflectMeATestContainingInstance(testClass);
                        testSuiteDescription = Description.createSuiteDescription("Custom Test with Suite ");
                        testSuiteDescription.addChild(createTestDescription("first test run"));
                        testSuiteDescription.addChild(createTestDescription("second test run"));
                        testSuiteDescription.addChild(createTestDescription("third test run"));
                    }

                    @Override
                    public Description getDescription() {
                        return testSuiteDescription;
                    }

                    @Override
                    public void run(RunNotifier notifier) {
                        for (Description description : testSuiteDescription.getChildren()) {
                            notifier.fireTestStarted(description);
                            try {
                                if (testContainingInstance.doSomething()) {
                                    notifier.fireTestFinished(description);
                                } else {
                                    notifier.fireTestIgnored(description);
                                }
                            } catch (Exception e) {
                                notifier.fireTestFailure(new Failure(description, e));
                            }
                        }
                    }

                    private org.gradle.CustomIgnoredTest reflectMeATestContainingInstance(Class<? extends org.gradle.CustomIgnoredTest> testClass) {
                        try {
                            return testClass.getConstructor().newInstance();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }

                    private Description createTestDescription(String description) {
                        return Description.createTestDescription(testClass, description);
                    }
                }
            }
        """
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                ${testFrameworkDependencies}
            }
            test.${configureTestFramework}
        """

        when:
        run('check')

        then:
        def result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.CustomIgnoredTest')
        result.testClass('org.gradle.CustomIgnoredTest').assertTestCount(3, 0, 0).assertTestsSkipped("first test run", "second test run", "third test run")
    }
}
