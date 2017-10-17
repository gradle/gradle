/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.testing.AbstractTestFrameworkIntegrationTest
import org.gradle.testing.fixture.TestNGCoverage

class TestNGTestFrameworkIntegrationTest extends AbstractTestFrameworkIntegrationTest {
    @Override
    void createPassingFailingTest() {
        file('src/test/java/AppException.java') << 'public class AppException extends Exception {}'
        file('src/test/java/SomeTest.java') << """
            public class SomeTest {
                @org.testng.annotations.Test
                public void ${failingTestCaseName}() { 
                    System.err.println("some error output");
                    assert false : "test failure message"; 
                }
            }
        """
        file('src/test/java/SomeOtherTest.java') << """
            public class SomeOtherTest {
                @org.testng.annotations.Test
                public void ${passingTestCaseName}() {}
            }
        """

        TestNGCoverage.enableTestNG(buildFile)
    }

    @Override
    String getTestTaskName() {
        return "test"
    }

    @Override
    String getPassingTestCaseName() {
        return "pass"
    }

    @Override
    String getFailingTestCaseName() {
        return "fail"
    }
}
