/*
 * Copyright 2020 the original author or authors.
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

import org.gradle.integtests.fixtures.TestResources
import org.gradle.testing.fixture.AbstractTestingMultiVersionIntegrationTest
import org.junit.Rule

abstract class AbstractDefaultTestOrderingIntegrationTest extends AbstractTestingMultiVersionIntegrationTest {

    @Rule public final TestResources resources = new TestResources(temporaryFolder)

    def setup() {
        executer.noExtraLogging()
        executer.withRepositoryMirrors()
    }

    private void addEmptyTestClass(String testName) {
        file("src/test/java/${testName}.java") << """
            ${testFrameworkImports}
            public class ${testName} {
                @Test public void test() {}
            }
        """.stripIndent()
    }

    def "test classes are scanned and run in deterministic order by default"() {
        addEmptyTestClass("AdTest")
        addEmptyTestClass("AATest")
        addEmptyTestClass("AyTest")
        addEmptyTestClass("AÆTest")
        addEmptyTestClass("ACTest")
        addEmptyTestClass("AÄTest")
        addEmptyTestClass("AZTest")
        addEmptyTestClass("AbTest")

        String expectedTestMessages = """
executed Test ${maybeParentheses('test')}(AATest)
executed Test ${maybeParentheses('test')}(ACTest)
executed Test ${maybeParentheses('test')}(AZTest)
executed Test ${maybeParentheses('test')}(AbTest)
executed Test ${maybeParentheses('test')}(AdTest)
executed Test ${maybeParentheses('test')}(AyTest)
executed Test ${maybeParentheses('test')}(AÄTest)
executed Test ${maybeParentheses('test')}(AÆTest)
"""

        file("build.gradle") << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                ${testFrameworkDependencies}
            }
            test.${configureTestFramework}
            test.beforeTest { println "executed " + it }
        """

        when:
        succeeds("test")

        then:
        outputContains(expectedTestMessages)
    }
}
