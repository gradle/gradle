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
import org.gradle.testing.junit.AbstractJUnitClassLevelFilteringIntegrationTest

abstract class AbstractJUnit4ClassLevelFilteringIntegrationTest extends AbstractJUnitClassLevelFilteringIntegrationTest {
    def "runs all tests for class instead of method when runner is not filterable"() {
        file("src/test/java/FooTest.java") << """
            import org.junit.*;
            import org.junit.runner.*;
            @RunWith(SomeRunner.class)
            public class FooTest {
            }
        """.stripIndent()
        file("src/test/java/SomeRunner.java") << """
            import org.junit.*;
            import org.junit.runner.*;
            import org.junit.runner.notification.*;
            public class SomeRunner extends Runner {
                Class<?> c;

                public SomeRunner(Class<?> c) {
                    this.c = c;
                }

                public Description getDescription() {
                    Description suite = Description.createSuiteDescription(c.getName());
                    suite.addChild(Description.createTestDescription(c, "pass"));
                    suite.addChild(Description.createTestDescription(c, "other"));
                    return suite;
                }

                public void run(RunNotifier notifier) {
                    for(Description d: getDescription().getChildren()) {
                        notifier.fireTestStarted(d);
                        notifier.fireTestFinished(d);
                    }
                }
            }
        """.stripIndent()

        when:
        run("test", "--tests", "FooTest.pass")

        then:
        def result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted("FooTest")
        result.testClass("FooTest").assertTestsExecuted("other", "pass")

        when:
        fails("test", "--tests", "FooTest.ignored")

        then:
        failure.assertHasCause("No tests found for given includes: [FooTest.ignored]")

        when:
        fails("test", "--tests", "NotFooTest.pass")

        then:
        failure.assertHasCause("No tests found for given includes: [NotFooTest.pass]")
    }
}
