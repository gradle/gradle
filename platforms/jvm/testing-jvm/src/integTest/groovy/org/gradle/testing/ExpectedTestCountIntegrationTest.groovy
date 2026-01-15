/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.testing

import org.gradle.api.tasks.testing.Test
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

/**
 * Integration tests for the {@link Test#getExpectedTestCount()} feature of the Test task.
 */
class ExpectedTestCountIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        buildFile << """
            plugins {
                id 'java'
            }

            ${mavenCentralRepository()}

            dependencies {
                testImplementation 'junit:junit:4.13.2'
            }
        """
    }

    def "test task succeeds when expected test count matches actual count"() {
        given:
        file("src/test/java/SimpleTest.java") << """
            import org.junit.Test;

            public class SimpleTest {
                @Test
                public void test1() {
                }

                @Test
                public void test2() {
                }
            }
        """

        buildFile << """
            test {
                expectedTestCount = 2
            }
        """

        when:
        succeeds("test")

        then:
        executedAndNotSkipped(":test")
    }

    def "test task emits warning when expected test count does not match actual count"() {
        given:
        file("src/test/java/SimpleTest.java") << """
            import org.junit.Test;

            public class SimpleTest {
                @Test
                public void test1() {
                }

                @Test
                public void test2() {
                }
            }
        """

        buildFile << """
            test {
                expectedTestCount = 5
            }
        """

        when:
        succeeds("test")

        then:
        executedAndNotSkipped(":test")
        outputContains("Expected 5 test(s) but executed 2 test(s).")
    }

    def "test task succeeds when expected test count is not set"() {
        given:
        file("src/test/java/SimpleTest.java") << """
            import org.junit.Test;

            public class SimpleTest {
                @Test
                public void test1() {
                }

                @Test
                public void test2() {
                }
            }
        """

        when:
        succeeds("test")

        then:
        executedAndNotSkipped(":test")
    }

    def "test task emits warning when expected test count is zero but tests exist"() {
        given:
        file("src/test/java/SimpleTest.java") << """
            import org.junit.Test;

            public class SimpleTest {
                @Test
                public void test1() {
                }
            }
        """

        buildFile << """
            test {
                expectedTestCount = 0
            }
        """

        when:
        succeeds("test")

        then:
        executedAndNotSkipped(":test")
        outputContains("Expected 0 test(s) but executed 1 test(s).")
    }

    def "test task succeeds with multiple test classes when expected count matches"() {
        given:
        file("src/test/java/SimpleTest1.java") << """
            import org.junit.Test;

            public class SimpleTest1 {
                @Test
                public void test1() {
                }
            }
        """

        file("src/test/java/SimpleTest2.java") << """
            import org.junit.Test;

            public class SimpleTest2 {
                @Test
                public void test2() {
                }

                @Test
                public void test3() {
                }
            }
        """

        buildFile << """
            test {
                expectedTestCount = 3
            }
        """

        when:
        succeeds("test")

        then:
        executedAndNotSkipped(":test")
    }
}
