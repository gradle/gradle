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

package org.gradle.testing.junitvintage

import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.integtests.fixtures.TestClassExecutionResult
import org.gradle.integtests.fixtures.TestExecutionResult
import org.gradle.testing.junit4.AbstractJUnit4JUnitIntegrationTest
import org.gradle.util.internal.VersionNumber

import static org.gradle.testing.fixture.JUnitCoverage.LATEST_JUNIT4_VERSION
import static org.gradle.testing.fixture.JUnitCoverage.JUNIT_VINTAGE
import static org.gradle.testing.fixture.JUnitCoverage.LATEST_JUNIT3_VERSION
import static org.gradle.testing.junitvintage.JUnitVintageMultiVersionTest.JUnitVintageBuildScriptConfiguration.dependencyVersion
import static org.hamcrest.CoreMatchers.containsString

@TargetCoverage({ JUNIT_VINTAGE })
class JUnitVintageJUnitIntegrationTest extends AbstractJUnit4JUnitIntegrationTest implements JUnitVintageMultiVersionTest {
    @Override
    String getJUnitVersionAssertion() {
        return "assertEquals(\"${LATEST_JUNIT4_VERSION}\", new org.junit.runner.JUnitCore().getVersion());"
    }

    @Override
    boolean supportsSuiteOutput() {
        // Suite output events are not correctly reported until version 5.9.0.  See https://github.com/junit-team/junit5/pull/2985.
        return VersionNumber.parse(dependencyVersion) >= VersionNumber.parse("5.9.0")
    }

    @Override
    String getTestFrameworkJUnit3Dependencies() {
        return """
            testCompileOnly 'junit:junit:${LATEST_JUNIT3_VERSION}'
            testRuntimeOnly 'org.junit.vintage:junit-vintage-engine:${dependencyVersion}'
            testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
        """
    }

    @Override
    TestClassExecutionResult assertFailedToExecute(TestExecutionResult testResult, String testClassName) {
        return testResult.testClassStartsWith('Gradle Test Executor')
            .assertTestFailed("failed to execute tests", containsString("Could not execute test class '${testClassName}'"))
    }
}
