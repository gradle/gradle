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
                testImplementation('org.testng:testng:${TestNGCoverage.NEWEST}')
                testImplementation('org.junit.jupiter:junit-jupiter:${JUnitCoverage.LATEST_JUPITER_VERSION}')
            }
        """
        withTestClass('JupiterTestClass', 'org.junit.jupiter.api.Test')

        when:
        succeeds("test")

        then:
        assertTestPassed('JupiterTestClass')
    }

    def "uses JUnit Platform if JUnit 4 and junit-vintage-engine are on the classpath"() {
        given:
        buildFile << """
            dependencies {
                testImplementation('junit:junit:${JUnitCoverage.JUNIT_4_LATEST}')
                testImplementation('org.testng:testng:${TestNGCoverage.NEWEST}')
                testImplementation('org.junit.vintage:junit-vintage-engine:${JUnitCoverage.LATEST_VINTAGE_VERSION}')
            }
        """
        withTestClass('VintageTestClass', 'org.junit.Test')

        when:
        succeeds("test")

        then:
        assertTestPassed('VintageTestClass')
    }

    def "uses TestNG if its jar is on the classpath but JUnit 4 is not"() {
        given:
        buildFile << """
            dependencies {
                testImplementation('org.testng:testng:${TestNGCoverage.NEWEST}')
            }
        """
        withTestClass('TestNGTestClass', 'org.testng.annotations.Test')

        when:
        succeeds("test")

        then:
        assertTestPassed('TestNGTestClass')
    }

    def "uses JUnit 4 if its jar and testng are on the classpath "() {
        given:
        buildFile << """
            dependencies {
                testImplementation('junit:junit:${JUnitCoverage.JUNIT_4_LATEST}')
                testImplementation('org.testng:testng:${TestNGCoverage.NEWEST}')
            }
        """
        withTestClass('JUnit4TestClass', 'org.junit.Test')

        when:
        succeeds("test")

        then:
        assertTestPassed('JUnit4TestClass')
    }

    def "uses JUnit 4 if junit but not junit-vintage-engine is not on the classpath"() {
        given:
        buildFile << """
            dependencies {
                testImplementation('junit:junit:${JUnitCoverage.JUNIT_4_LATEST}')
                testRuntimeOnly('org.junit.jupiter:junit-jupiter-engine:${JUnitCoverage.LATEST_JUPITER_VERSION}')
            }
        """
        withTestClass('JUnit4TestClass', 'org.junit.Test')

        when:
        succeeds("test")

        then:
        assertTestPassed('JUnit4TestClass')
    }

    private void assertTestPassed(String className) {
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted(className)
        result.testClass(className).assertTestPassed('test')
    }

    private void withTestClass(String className, String annotation) {
        file("src/test/java/${className}.java") << """
            public class $className {
                @$annotation
                public void test() {}
            }
        """
    }
}
