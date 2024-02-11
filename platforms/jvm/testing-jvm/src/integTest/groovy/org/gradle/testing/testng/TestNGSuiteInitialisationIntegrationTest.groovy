/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.testing.testng

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.testing.fixture.TestNGCoverage
import spock.lang.Issue

import static org.gradle.integtests.fixtures.TestExecutionResult.EXECUTION_FAILURE
import static org.hamcrest.CoreMatchers.startsWith

class TestNGSuiteInitialisationIntegrationTest extends AbstractIntegrationSpec {

    @Issue("GRADLE-1710")
    def "reports suite fatal failure"() {
        TestNGCoverage.enableTestNG(buildFile, '6.3.1')
        file("src/test/java/FooTest.java") << """
            import org.testng.annotations.*;

            public class FooTest {
                public FooTest() { throw new NullPointerException(); }
                @Test public void foo() {}
            }
        """

        expect:
        fails("test")

        def result = new DefaultTestExecutionResult(testDirectory)
        result.testClassStartsWith("Gradle Test Executor").assertTestFailed(EXECUTION_FAILURE,
                startsWith("org.gradle.api.internal.tasks.testing.TestSuiteExecutionException"))
    }
}
