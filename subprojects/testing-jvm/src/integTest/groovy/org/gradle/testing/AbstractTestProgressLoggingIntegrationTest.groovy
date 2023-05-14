/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.integtests.fixtures.executer.ProgressLoggingFixture
import org.gradle.testing.fixture.AbstractTestingMultiVersionIntegrationTest
import org.junit.Rule

abstract class AbstractTestProgressLoggingIntegrationTest extends AbstractTestingMultiVersionIntegrationTest {
    @Rule ProgressLoggingFixture events = new ProgressLoggingFixture(executer, temporaryFolder)

    def setup() {
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                ${testFrameworkDependencies}
            }
            test.${configureTestFramework}
        """
    }

    def "captures test progress logging events" () {
        withGoodTestClasses(10)

        when:
        succeeds("test")

        then:
        events.statusLogged("0 tests completed")
        events.statusLogged("1 test completed")
        (2..10).each { count ->
            assert events.statusLogged("${count} tests completed")
        }
    }

    def "captures failing test progress logging events" () {
        withFailingTestClasses(10)

        when:
        fails("test")

        then:
        events.statusLogged("0 tests completed")
        events.statusLogged("1 test completed, 1 failed")
        (2..10).each { count ->
            assert events.statusLogged("${count} tests completed, ${count} failed")
        }
    }

    def "captures skipped test progress logging events" () {
        withSkippedTestClasses(10)

        when:
        succeeds("test")

        then:
        events.statusLogged("0 tests completed")
        events.statusLogged("1 test completed, 1 skipped")
        (2..10).each { count ->
            assert events.statusLogged("${count} tests completed, ${count} skipped")
        }
    }

    def "captures mixed test progress logging events" () {
        withGoodTestClasses(5)
        withFailingTestClasses(3)
        withSkippedTestClasses(2)

        when:
        fails("test")

        then:
        (0..10).each { count ->
            assert events.statusMatches("${count} tests? completed(,.*)*")
        }

        and:
        (1..3).each { count ->
            assert events.statusMatches("\\d+ tests? completed, ${count} failed(,.*)?")
        }

        and:
        (1..2).each { count ->
            assert events.statusMatches("\\d+ tests? completed,( \\d failed,)? ${count} skipped")
        }
    }

    def "captures test progress logging events when tests are run in parallel" () {
        withGoodTestClasses(4)
        buildFile << """
            test {
                maxParallelForks = 4
            }
        """

        when:
        succeeds("test")

        then:
        events.statusLogged("0 tests completed")
        events.statusLogged("1 test completed")
        (2..4).each { count ->
            assert events.statusLogged("${count} tests completed")
        }
    }

    def createTestClass(int index, String type, String method, String assertion) {
        String className = "${type.capitalize()}Test${index}"
        file("src/test/java/${className}.java") << """
            ${testFrameworkImports}
            public class ${className} {
                @Test
                public void ${method}() {
                    ${assertion};
                }
            }
        """
    }

    def createGoodTestClass(int index=1) {
        createTestClass(index, "good", "shouldPass", "assertEquals(1,1)")
    }

    def createFailingTestClass(int index=1) {
        createTestClass(index, "failing", "shouldFail", "assertEquals(1,2)")
    }

    def createSkippedTestClass(int index=1) {
        createTestClass(index, "skipped", "shouldBeSkipped", "assumeTrue(false)")
    }

    def withGoodTestClasses(int count) {
        count.times { index ->
            createGoodTestClass(index)
        }
    }

    def withFailingTestClasses(int count) {
        count.times { index ->
            createFailingTestClass(index)
        }
    }

    def withSkippedTestClasses(int count) {
        count.times { index ->
            createSkippedTestClass(index)
        }
    }
}
