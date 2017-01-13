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

class GroovyLibraryInitIntegrationTest extends AbstractIntegrationSpec {

    public static final String SAMPLE_LIBRARY_CLASS = "src/main/groovy/Library.groovy"
    public static final String SAMPLE_LIBRARY_TEST_CLASS = "src/test/groovy/LibraryTest.groovy"

    final wrapper = new WrapperTestFixture(testDirectory)

    def "creates sample source if no source present"() {
        when:
        succeeds('init', '--type', 'groovy-library')

        then:
        file(SAMPLE_LIBRARY_CLASS).exists()
        file(SAMPLE_LIBRARY_TEST_CLASS).exists()
        buildFile.exists()
        settingsFile.exists()
        wrapper.generated()

        when:
        succeeds("build")

        then:
        TestExecutionResult testResult = new DefaultTestExecutionResult(testDirectory)
        testResult.assertTestClassesExecuted("LibraryTest")
        testResult.testClass("LibraryTest").assertTestPassed("someLibraryMethod returns true")
    }

    def "supports the Spock test framework"() {
        when:
        succeeds('init', '--type', 'groovy-library', '--test-framework', 'spock')

        then:
        file(SAMPLE_LIBRARY_CLASS).exists()
        file(SAMPLE_LIBRARY_TEST_CLASS).exists()
        buildFile.exists()
        settingsFile.exists()
        wrapper.generated()
    }

    def "setupProjectLayout is skipped when groovy sources detected"() {
        setup:
        file("src/main/groovy/org/acme/SampleMain.groovy") << """
            package org.acme;

            class SampleMain{
            }
    """
        file("src/test/groovy/org/acme/SampleMainTest.groovy") << """
                    package org.acme;

                    class SampleMain{
                    }
            """
        when:
        succeeds('init', '--type', 'groovy-library')

        then:
        !file(SAMPLE_LIBRARY_CLASS).exists()
        !file(SAMPLE_LIBRARY_TEST_CLASS).exists()
        buildFile.exists()
        settingsFile.exists()
        wrapper.generated()
    }
}
