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
import org.gradle.testing.junit.AbstractJUnitTestClassDetectionIntegrationTest

abstract class AbstractJUnit4TestClassDetectionIntegrationTest extends AbstractJUnitTestClassDetectionIntegrationTest {
    abstract boolean isSupportsEmptyClassWithRunner()

    // TODO: See if there is some way we can implement the custom runner class as a JUnit Jupiter extension
    def "detects test classes"() {
        given:
        file('src/test/java/org/gradle/AbstractHasRunWith.java') << """
            package org.gradle;

            ${testFrameworkImports}

            ${getRunOrExtendWithAnnotation('CustomRunnerOrExtension.class')}
            public abstract class AbstractHasRunWith {
            }
        """.stripIndent()
        if (supportsEmptyClassWithRunner) {
            file('src/test/java/org/gradle/EmptyRunWithSubclass.java') << """
                package org.gradle;

                public class EmptyRunWithSubclass extends AbstractHasRunWith {

                }
            """.stripIndent()
        }
        file('src/test/java/org/gradle/TestsOnInner.java') << """
            package org.gradle;

            ${testFrameworkImports}
            import junit.framework.TestCase;

            public class TestsOnInner {
                @Test public void ok() { }

                public static class SomeInner {
                    @Test public void ok() { }
                }

                public class NonStaticInner {
                    @Test public void ok() { }
                }

                public class NonStaticInnerTestCase extends TestCase {
                    public void testOk() { }
                }
            }
        """.stripIndent()
        writeCustomRunnerClass('CustomRunnerOrExtension')
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                ${testFrameworkDependencies}
            }
            test.${configureTestFramework}
        """.stripIndent()

        when:
        executer.withTasks('test').run()

        then:
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)
        result.testClass('org.gradle.TestsOnInner').assertTestPassed('ok')
        result.testClass('org.gradle.TestsOnInner$SomeInner').assertTestPassed('ok')
        if (supportsEmptyClassWithRunner) {
            result.testClass('org.gradle.EmptyRunWithSubclass').assertTestsExecuted('ok')
            result.testClass('org.gradle.EmptyRunWithSubclass').assertTestPassed('ok')
        }
    }

    void writeCustomRunnerClass(String className) {
        file("src/test/java/org/gradle/${className}.java") << """
            package org.gradle;

            import org.junit.runner.*;
            import org.junit.runner.notification.RunNotifier;

            public class ${className} extends Runner {
                private final Description description;

                public ${className}(Class type) throws Exception {
                    description = Description.createSuiteDescription(type);
                    description.addChild(Description.createTestDescription(type, "ok"));
                }

                @Override
                public Description getDescription() {
                    return description;
                }

                @Override
                public void run(RunNotifier notifier) {
                    for (Description child : description.getChildren()) {
                        notifier.fireTestStarted(child);
                        notifier.fireTestFinished(child);
                    }
                }
            }
        """.stripIndent()
    }
}
