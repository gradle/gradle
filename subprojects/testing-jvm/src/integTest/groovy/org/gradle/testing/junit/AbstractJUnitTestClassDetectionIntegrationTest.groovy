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

package org.gradle.testing.junit

import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.testing.fixture.AbstractTestingMultiVersionIntegrationTest
import spock.lang.Issue

abstract class AbstractJUnitTestClassDetectionIntegrationTest extends AbstractTestingMultiVersionIntegrationTest {

    @Issue("https://issues.gradle.org/browse/GRADLE-2527")
    def "test class detection works for custom test tasks"() {
        given:
        buildFile << """
            apply plugin:'java'
            ${mavenCentralRepository()}

            sourceSets {
                othertests {
                    java.srcDir file('src/othertests/java')
                    resources.srcDir file('src/othertests/resources')
                }
            }

            dependencies{
                ${getTestFrameworkDependencies('othertests')}
            }

            task othertestsTest(type:Test){
                ${configureTestFramework}
                classpath = sourceSets.othertests.runtimeClasspath
                testClassesDirs = sourceSets.othertests.output.classesDirs
            }
        """.stripIndent()

        and:
        file("src/othertests/java/SomeTestClass.java") << """
            ${testFrameworkImports}
            public class SomeTestClass {
                @Test
                public void testTrue() {
                    assertTrue(true);
                }
            }
        """.stripIndent()

        when:
        run "othertestsTest"
        then:
        def result = new DefaultTestExecutionResult(testDirectory, 'build', '', '', 'othertestsTest')
        result.assertTestClassesExecuted("SomeTestClass")
    }

    @Issue("https://issues.gradle.org/browse/GRADLE-3157")
    def "test class detection works when '-parameters' compiler option is used (JEP 118)"() {
        when:
        buildScript """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                ${testFrameworkDependencies}
            }
            tasks.withType(JavaCompile) {
                options.with {
                    compilerArgs << '-parameters'
                }
            }
            test.${configureTestFramework}
        """.stripIndent()

        and:
        file("src/test/java/TestHelper.java") << """
            public class TestHelper {
                public void helperMethod(String foo, int bar) {
                    // this method shouldn't cause failure due to API version check
                    // in org.objectweb.asm.MethodVisitor#visitParameter
                }
            }
        """.stripIndent()

        and:
        file("src/test/java/TestCase.java") << """
            ${testFrameworkImports}
            public class TestCase {
                @Test
                public void test() {
                    assertTrue(Double.parseDouble(System.getProperty("java.specification.version")) >= 1.8);
                }
            }
        """.stripIndent()

        then:
        run "test"

        and:
        def result = new DefaultTestExecutionResult(testDirectory)
        result.testClass("TestCase").with {
            assertTestCount(1, 0, 0)
        }
    }
}
