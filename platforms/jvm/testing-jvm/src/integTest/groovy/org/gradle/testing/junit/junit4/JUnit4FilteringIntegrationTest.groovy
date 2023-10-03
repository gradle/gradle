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

import org.gradle.integtests.fixtures.TargetCoverage

import static org.gradle.testing.fixture.JUnitCoverage.JUNIT4_LARGE_COVERAGE

@TargetCoverage({ JUNIT4_LARGE_COVERAGE })
class JUnit4FilteringIntegrationTest extends AbstractJUnit4FilteringIntegrationTest implements JUnit4MultiVersionTest {
    def 'filter as many classes as possible before sending to worker process'() {
        given:
        // We can know which class is sent to TestClassProcessor via afterSuite() hook method
        // because JUnitTestClassProcessor will emit a test suite event for each loaded class.
        // However, JUnitPlatformTestClassProcessor won't emit such event unless the class is executed.
        // That's why we run test with JUnit 4 only.
        file('src/test/java/org/gradle/FooTest.java') << """
            package org.gradle;
            ${testFrameworkImports}
            public class FooTest {
                @Test public void test() {}
            }
        """
        file('src/test/java/com/gradle/FooTest.java') << """
            package com.gradle;
            ${testFrameworkImports}
            public class FooTest {
                @Test public void test() {}
            }
        """
        file('src/test/java/org/gradle/BarTest.java') << """
            package org.gradle;
            ${testFrameworkImports}
            public class BarTest {
                @Test public void test() {}
            }
        """
        buildFile << """
            test {
                filter {
                    includeTestsMatching "$pattern"
                }
                afterSuite { descriptor, result ->
                    println descriptor
                }
            }
        """

        when:
        if (successful) {
            succeeds('test')
        } else {
            fails('test')
        }

        then:
        includedClasses.every { output.contains(it) }
        excludedClasses.every { !output.contains(it) }

        where:
        pattern             | includedClasses                                                    | excludedClasses        | successful
        'FooTest'           | ['org.gradle.FooTest', 'com.gradle.FooTest']                       | ['org.gradle.BarTest'] | true
        'FooTest.anyMethod' | ['org.gradle.FooTest', 'com.gradle.FooTest']                       | ['org.gradle.BarTest'] | false
        'org.gradle.*'      | ['org.gradle.FooTest', 'org.gradle.BarTest']                       | ['com.gradle.FooTest'] | true
        '*FooTest'          | ['org.gradle.FooTest', 'com.gradle.FooTest', 'org.gradle.BarTest'] | []                     | true
        'org*'              | ['org.gradle.FooTest', 'org.gradle.BarTest']                       | ['com.gradle.FooTest'] | true
    }
}
