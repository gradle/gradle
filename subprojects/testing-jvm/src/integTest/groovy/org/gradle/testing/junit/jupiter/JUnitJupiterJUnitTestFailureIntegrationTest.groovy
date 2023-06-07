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

package org.gradle.testing.junit.jupiter

import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.testing.junit.AbstractJUnitTestFailureIntegrationTest
import org.hamcrest.Matcher

import static org.gradle.testing.fixture.JUnitCoverage.JUNIT_JUPITER
import static org.hamcrest.CoreMatchers.equalTo

@TargetCoverage({ JUNIT_JUPITER })
class JUnitJupiterJUnitTestFailureIntegrationTest extends AbstractJUnitTestFailureIntegrationTest implements JUnitJupiterMultiVersionTest {
    @Override
    void writeBrokenRunnerOrExtension(String className) {
        file("src/test/java/org/gradle/${className}.java") << """
            package org.gradle;

            import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
            import org.junit.jupiter.api.extension.ExtensionContext;

            public class BrokenRunnerOrExtension implements BeforeTestExecutionCallback {
                @Override
                public void beforeTestExecution(ExtensionContext context) throws Exception {
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

            @ExtendWith(${runnerOrExtensionName}.class)
            public class ${className} {
                @Test
                public void ok() {
                }
            }
        """.stripIndent()
    }

    @Override
    String getInitializationErrorTestName() {
        return 'ok()'
    }

    @Override
    String getAssertionFailureClassName() {
        return 'org.opentest4j.AssertionFailedError'
    }

    @Override
    String getBeforeClassErrorTestName() {
        return 'initializationError'
    }

    @Override
    String getAfterClassErrorTestName() {
        return 'executionError'
    }

    @Override
    Matcher<? super String>[] getBrokenBeforeAndAfterMatchers() {
        return [equalTo(failureAssertionError('before failed'))]
    }

    @Override
    boolean hasStableInitializationErrors() {
        return true
    }
}
