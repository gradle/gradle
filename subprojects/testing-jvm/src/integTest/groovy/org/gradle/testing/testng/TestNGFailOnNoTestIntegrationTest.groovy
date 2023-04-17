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

package org.gradle.testing.testng

import static org.gradle.testing.fixture.JUnitCoverage.getLATEST_JUPITER_VERSION

class TestNGFailOnNoTestIntegrationTest extends TestNGTestFrameworkIntegrationTest {

    def "test source and test task use same test framework"() {
        given:
        buildFile << """
            apply plugin:'java-library'
            ${mavenCentralRepository()}
            dependencies {
                testImplementation 'org.testng:testng:6.3.1'
            }
            test {
                useTestNG()
            }
        """

        file("src/test/java/NotATest.java") << """
            @org.testng.annotations.Test
            public class NotATest {}
        """

        when:
        run('test')

        then:
        noExceptionThrown()

        when:
        run('test', '--no-fail-on-no-test')

        then:
        noExceptionThrown()

        expect:
        def failure = fails("test", "--fail-on-no-test")
        failure.assertHasErrorOutput("No tests found for given includes: ")

    }

    def "test source and test task use different test frameworks"() {
        given:
        buildFile << """
            apply plugin:'java-library'
            ${mavenCentralRepository()}
            dependencies {
                testImplementation 'org.junit.jupiter:junit-jupiter:$LATEST_JUPITER_VERSION'
                testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
            }
            test {
                useJUnitPlatform()
            }
        """

        createPassingFailingTest()

        when:
        run("test")

        then:
        noExceptionThrown()

        when:
        run('test', '--no-fail-on-no-test')

        then:
        noExceptionThrown()

        expect:
        def failure = fails("test", "--fail-on-no-test")
        failure.assertHasErrorOutput("No tests found for given includes: ")
    }
}
