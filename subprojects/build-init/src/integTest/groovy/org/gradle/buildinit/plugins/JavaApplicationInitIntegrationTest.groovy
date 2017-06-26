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

package org.gradle.buildinit.plugins

import org.gradle.buildinit.plugins.fixtures.WrapperTestFixture
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.TestExecutionResult

class JavaApplicationInitIntegrationTest extends AbstractIntegrationSpec {

    public static final String SAMPLE_APP_CLASS = "src/main/java/App.java"
    public static final String SAMPLE_APP_TEST_CLASS = "src/test/java/AppTest.java"
    public static final String SAMPLE_APP_SPOCK_TEST_CLASS = "src/test/groovy/AppTest.groovy"

    final wrapper = new WrapperTestFixture(testDirectory)

    def "creates sample source if no source present"() {
        when:
        succeeds('init', '--type', 'java-application')

        then:
        file(SAMPLE_APP_CLASS).exists()
        file(SAMPLE_APP_TEST_CLASS).exists()
        buildFile.exists()
        settingsFile.exists()
        wrapper.generated()

        when:
        succeeds("build")

        then:
        assertTestPassed("testAppHasAGreeting")

        when:
        succeeds("run")

        then:
        outputContains("Hello world")
    }

    def "creates sample source using spock instead of junit"() {
        when:
        succeeds('init', '--type', 'java-application', '--test-framework', 'spock')

        then:
        file(SAMPLE_APP_CLASS).exists()
        file(SAMPLE_APP_SPOCK_TEST_CLASS).exists()
        buildFile.exists()
        settingsFile.exists()
        wrapper.generated()

        when:
        succeeds("build")

        then:
        assertTestPassed("application has a greeting")
    }

    def "creates sample source using testng instead of junit"() {
        when:
        succeeds('init', '--type', 'java-application', '--test-framework', 'testng')

        then:
        file(SAMPLE_APP_CLASS).exists()
        file(SAMPLE_APP_TEST_CLASS).exists()
        buildFile.exists()
        settingsFile.exists()
        wrapper.generated()

        when:
        succeeds("build")

        then:
        assertTestPassed("appHasAGreeting")
    }

    def "setupProjectLayout is skipped when java sources detected"() {
        setup:
        file("src/main/java/org/acme/SampleMain.java") << """
        package org.acme;

        public class SampleMain{
        }
"""
        file("src/test/java/org/acme/SampleMainTest.java") << """
                package org.acme;

                public class SampleMain{
                }
        """
        when:
        succeeds('init', '--type', 'java-application')

        then:
        !file(SAMPLE_APP_CLASS).exists()
        !file(SAMPLE_APP_TEST_CLASS).exists()
        buildFile.exists()
        settingsFile.exists()
        wrapper.generated()
    }

    def assertTestPassed(String name) {
        TestExecutionResult testResult = new DefaultTestExecutionResult(testDirectory)
        testResult.assertTestClassesExecuted("AppTest")
        testResult.testClass("AppTest").assertTestPassed(name)
    }
}
