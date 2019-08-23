/*
 * Copyright 2019 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.testing.fixture.JUnitCoverage
import org.gradle.testing.fixture.TestNGCoverage

class TestFrameworkAutoDetectionIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        buildFile << """
            plugins {
                id("java")
            }
            repositories {
                ${mavenCentralRepository()} 
            }
        """
    }

    def "uses JUnit Platform if junit-platform-engine is on the classpath"() {
        given:
        buildFile << """
            dependencies {
                testImplementation('junit:junit:${JUnitCoverage.JUNIT_4_LATEST}')
                testImplementation('org.testng:testng:${TestNGCoverage.NEWEST}')
                testImplementation('org.junit.jupiter:junit-jupiter:${JUnitCoverage.LATEST_JUPITER_VERSION}')
            }
        """
        file('src/test/java/JupiterTestClass.java') << '''
            class JupiterTestClass {
                @org.junit.jupiter.api.Test
                void test() {}
            }
        '''

        when:
        succeeds("test")

        then:
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('JupiterTestClass')
        result.testClass('JupiterTestClass').assertTestPassed('test')
    }

    def "uses TestNG if its jar is on the classpath"() {
        given:
        buildFile << """
            dependencies {
                testImplementation('junit:junit:${JUnitCoverage.JUNIT_4_LATEST}')
                testImplementation('org.testng:testng:${TestNGCoverage.NEWEST}')
            }
        """
        file('src/test/java/TestNGTestClass.java') << '''
            public class TestNGTestClass {
                @org.testng.annotations.Test
                public void test() {}
            }
        '''

        when:
        succeeds("test")

        then:
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('TestNGTestClass')
        result.testClass('TestNGTestClass').assertTestPassed('test')
    }

    def "uses JUnit 4 if neither JUnit Platform nor TestNG are on the classpath"() {
        given:
        buildFile << """
            dependencies {
                testImplementation('junit:junit:${JUnitCoverage.JUNIT_4_LATEST}')
            }
        """
        file('src/test/java/JUnit4TestClass.java') << '''
            public class JUnit4TestClass {
                @org.junit.Test
                public void test() {}
            }
        '''

        when:
        succeeds("test")

        then:
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('JUnit4TestClass')
        result.testClass('JUnit4TestClass').assertTestPassed('test')
    }
}
