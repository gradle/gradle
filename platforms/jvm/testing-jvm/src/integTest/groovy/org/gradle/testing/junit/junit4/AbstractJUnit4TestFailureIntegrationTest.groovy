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

import org.gradle.testing.junit.AbstractJUnitTestFailureIntegrationTest

abstract class AbstractJUnit4TestFailureIntegrationTest extends AbstractJUnitTestFailureIntegrationTest {

    @Override
    void writeBrokenRunnerOrExtension(String className) {
        file("src/test/java/org/gradle/${className}.java") << """
            package org.gradle;

            import org.junit.runner.Description;
            import org.junit.runner.Runner;
            import org.junit.runner.notification.RunNotifier;

            public class ${className} extends Runner {
                private final Class<?> type;

                public BrokenRunnerOrExtension(Class<?> type) {
                    this.type = type;
                }

                @Override
                public Description getDescription() {
                    return Description.createSuiteDescription(type);
                }

                @Override
                public void run(RunNotifier notifier) {
                    throw new UnsupportedOperationException("broken");
                }
            }
        """.stripIndent()
    }

    @Override
    void writeClassUsingBrokenRunnerOrExtension(String className, String runnerOrExtensionName) {
        file("src/test/java/org/gradle/${className}.java") << """
            package org.gradle;

            ${testFrameworkImports}

            @RunWith(${runnerOrExtensionName}.class)
            public class ${className} {
                @Test
                public void ok() {
                }
            }
        """.stripIndent()
    }

    @Override
    String getAssertionFailureClassName() {
        return "java.lang.AssertionError"
    }


}
