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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.testing.fixture.JUnitCoverage
import org.gradle.testing.fixture.TestNGCoverage
import org.gradle.util.GradleVersion

/**
 * Tests behavior of different test frameworks when their required
 * runtime dependencies are not included on the test runtime classpath.
 */
class TestFrameworkMissingDependenciesIntegrationTest extends AbstractIntegrationSpec {

    def "junit 4 fails with sensible error message"() {
        given:
        buildFile << """
            plugins {
                id("java-library")
            }

            ${mavenCentralRepository()}

            dependencies {
                testCompileOnly("junit:junit:${JUnitCoverage.LATEST_JUNIT4_VERSION}")
            }

            test.useJUnit()
        """

        executer.withToolchainDetectionEnabled()

        file("src/test/java/MyTest.java") << """
            public class MyTest {
                @org.junit.Test
                public void test() {}
            }
        """

        when:
        fails('test')

        then:
        failure.assertHasCause("Failed to load JUnit 4. Please ensure that JUnit 4 is available on the test runtime classpath.")
    }

    def "junit platform fails with sensible error message"() {
        given:
        buildFile << """
            plugins {
                id("java-library")
            }

            ${mavenCentralRepository()}

            dependencies {
                testCompileOnly("org.junit.jupiter:junit-jupiter-api:${JUnitCoverage.LATEST_JUPITER_VERSION}")
            }

            test.useJUnitPlatform()
        """

        file("src/test/java/MyTest.java") << """
            public class MyTest {
                @org.junit.jupiter.api.Test
                void test() {}
            }
        """

        when:
        fails('test')

        then:
        failure.assertHasCause("Failed to load JUnit Platform. Please ensure that the JUnit Platform is available on the test runtime classpath. See the user guide for more details: https://docs.gradle.org/${GradleVersion.current().version}/userguide/java_testing.html#using_junit5")
    }

    def "testng fails with sensible error message"() {
        given:
        buildFile << """
            plugins {
                id("java-library")
            }

            ${mavenCentralRepository()}

            dependencies {
                testCompileOnly("org.testng:testng:${TestNGCoverage.NEWEST}")
            }

            test.useTestNG()
        """

        file("src/test/java/MyTest.java") << """
            public class MyTest {
                @org.testng.annotations.Test
                public void test() {}
            }
        """

        when:
        fails('test')

        then:
        failure.assertHasCause("Failed to load TestNG. Please ensure that TestNG is available on the test runtime classpath.")
    }

}
