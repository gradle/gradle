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

package org.gradle.buildinit.plugins

import org.gradle.buildinit.plugins.fixtures.WrapperTestFixture
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.TestExecutionResult

class JavaLibraryInitIntegrationTest extends AbstractIntegrationSpec {

    public static final String SAMPLE_LIBRARY_CLASS = "src/main/java/Library.java"
    public static final String SAMPLE_LIBRARY_TEST_CLASS = "src/test/java/LibraryTest.java"
    public static final String SAMPLE_SPOCK_LIBRARY_TEST_CLASS = "src/test/groovy/LibraryTest.groovy"

    final wrapper = new WrapperTestFixture(testDirectory)

    def "creates sample source if no source present"() {
        when:
        succeeds('init', '--type', 'java-library')

        then:
        file(SAMPLE_LIBRARY_CLASS).exists()
        file(SAMPLE_LIBRARY_TEST_CLASS).exists()
        buildFileSeparatesImplementationAndApi()
        settingsFile.exists()
        wrapper.generated()

        when:
        succeeds("build")

        then:
        assertTestPassed("testSomeLibraryMethod")
    }

    def "creates sample source using spock instead of junit"() {
        when:
        succeeds('init', '--type', 'java-library', '--test-framework', 'spock')

        then:
        file(SAMPLE_LIBRARY_CLASS).exists()
        file(SAMPLE_SPOCK_LIBRARY_TEST_CLASS).exists()
        buildFileSeparatesImplementationAndApi('org.spockframework')
        settingsFile.exists()
        wrapper.generated()

        when:
        succeeds("build")

        then:
        assertTestPassed("someLibraryMethod returns true")
    }

    def "creates sample source using testng instead of junit"() {
        when:
        succeeds('init', '--type', 'java-library', '--test-framework', 'testng')

        then:
        file(SAMPLE_LIBRARY_CLASS).exists()
        file(SAMPLE_LIBRARY_TEST_CLASS).exists()
        buildFileSeparatesImplementationAndApi('org.testng')
        settingsFile.exists()
        wrapper.generated()

        when:
        succeeds("build")

        then:
        assertTestPassed("someLibraryMethodReturnsTrue")
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
        succeeds('init', '--type', 'java-library')

        then:
        !file(SAMPLE_LIBRARY_CLASS).exists()
        !file(SAMPLE_LIBRARY_TEST_CLASS).exists()
        buildFileSeparatesImplementationAndApi()
        settingsFile.exists()
        wrapper.generated()
    }

    def assertTestPassed(String name) {
        TestExecutionResult testResult = new DefaultTestExecutionResult(testDirectory)
        testResult.assertTestClassesExecuted("LibraryTest")
        testResult.testClass("LibraryTest").assertTestPassed(name)
    }

    private void buildFileSeparatesImplementationAndApi(String testFramework = 'junit:junit:') {
        assert buildFile.exists()
        def text = buildFile.text
        assert text.contains("api 'org.apache.commons:commons-math3")
        assert text.contains("implementation 'com.google.guava:guava:")
        assert text.contains("testImplementation '$testFramework")
    }
}
