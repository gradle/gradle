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


package org.gradle.testing.testng

import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.testing.AbstractTestFilteringIntegrationTest
import org.gradle.testing.fixture.TestNGCoverage
import spock.lang.Issue

@TargetCoverage({ TestNGCoverage.SUPPORTED_BY_JDK })
class TestNGFilteringIntegrationTest extends AbstractTestFilteringIntegrationTest implements TestNGMultiVersionTest {

    void theUsualFiles() {
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                ${testFrameworkDependencies}
            }
            test {
              ${configureTestFramework} {
                suiteXmlBuilder().suite(name: 'AwesomeSuite') {
                    test (name: 'AwesomeTest') {
                        classes([:]) {
                            'class' (name: 'FooTest')
                            'class' (name: 'BarTest')
                        }
                    }
                }
              }
            }
        """

        file("src/test/java/FooTest.java") << """
            ${testFrameworkImports}
            public class FooTest {
                @Test public void pass() {}
            }
        """
        file("src/test/java/BarTest.java") << """
            ${testFrameworkImports}
            public class BarTest {
                @Test public void pass() {}
            }
        """
        file("src/test/java/BazTest.java") << """
            ${testFrameworkImports}
            public class BazTest {
                @Test public void pass() {}
            }
        """
    }

    @ToBeFixedForConfigurationCache(because = "load-after-store")
    @Issue("GRADLE-3112")
    def "suites can be filtered from the command-line"() {
        given:
        theUsualFiles()

        when:
        run("test", "--tests", "*AwesomeSuite*")

        then:
        def result = new DefaultTestExecutionResult(testDirectory)

        result.assertTestClassesExecuted('FooTest', 'BarTest')
        result.testClass('FooTest').assertTestCount(1, 0, 0)
        result.testClass('FooTest').assertTestsExecuted('pass')
        result.testClass('BarTest').assertTestCount(1, 0, 0)
        result.testClass('BarTest').assertTestsExecuted('pass')
    }

    @ToBeFixedForConfigurationCache(because = "load-after-store")
    @Issue("GRADLE-3112")
    def "suites can be filtered from the build file"() {
        given:
        theUsualFiles()
        // and this addition to the build file ...
        buildFile << """
            test {
              filter {
                includeTestsMatching "*AwesomeSuite*"
              }
            }
        """

        when:
        run("test")

        then:
        def result = new DefaultTestExecutionResult(testDirectory)

        result.assertTestClassesExecuted('FooTest', 'BarTest')
        result.testClass('FooTest').assertTestCount(1, 0, 0)
        result.testClass('FooTest').assertTestsExecuted('pass')
        result.testClass('BarTest').assertTestCount(1, 0, 0)
        result.testClass('BarTest').assertTestsExecuted('pass')
    }
}
