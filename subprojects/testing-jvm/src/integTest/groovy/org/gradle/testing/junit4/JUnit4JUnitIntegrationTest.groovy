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

package org.gradle.testing.junit4

import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.integtests.fixtures.TestClassExecutionResult
import org.gradle.integtests.fixtures.TestExecutionResult

import static org.gradle.testing.fixture.JUnitCoverage.JUNIT_4_LATEST
import static org.gradle.testing.fixture.JUnitCoverage.LATEST_JUNIT3_VERSION
import static org.hamcrest.CoreMatchers.containsString

@TargetCoverage({ JUNIT_4_LATEST })
class JUnit4JUnitIntegrationTest extends AbstractJUnit4JUnitIntegrationTest implements JUnit4MultiVersionTest {
    @Override
    String getJUnitVersionAssertion() {
        return "assertEquals(\"${version}\", new org.junit.runner.JUnitCore().getVersion());"
    }

    @Override
    boolean supportsSuiteOutput() {
        return true
    }

    @Override
    String getTestFrameworkJUnit3Dependencies() {
        return """
            testCompileOnly 'junit:junit:${LATEST_JUNIT3_VERSION}'
            testRuntimeOnly 'junit:junit:${version}'
        """
    }

    @Override
    TestClassExecutionResult assertFailedToExecute(TestExecutionResult testResult, String testClassName) {
        return testResult.testClass(testClassName)
            .assertTestFailed("initializationError", containsString('ClassFormatError'))
    }
}
