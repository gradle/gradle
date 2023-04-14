/*
 * Copyright 2013 the original author or authors.
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


package org.gradle.testing.junit

import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.testing.fixture.JUnitMultiVersionIntegrationSpec

import static org.gradle.testing.fixture.JUnitCoverage.FILTER_JUNIT3_TESTS
import static org.gradle.testing.fixture.JUnitCoverage.JUNIT_VINTAGE

@TargetCoverage({ FILTER_JUNIT3_TESTS + JUNIT_VINTAGE })
class JUnit3FilteringIntegrationTest extends JUnitMultiVersionIntegrationSpec {
    void "filters tests implemented using 3.x test cases"() {
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                ${getDependencyBlockContents()}
            }
        """

        file("src/test/java/FooTest.java") << """
            import junit.framework.*;

            public class FooTest extends TestCase {
                public void testPass() {}
                public void testOk() {}
            }
        """

        when:
        succeeds("test", "--tests", "FooTest.testPass")

        then:
        def result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted("FooTest")
        result.testClass("FooTest").assertTestsExecuted("testPass")

        when:
        fails("test", "--tests", "FooTest.unknown")

        then:
        failure.assertHasCause("No tests found for given includes: [FooTest.unknown]")
    }
}
