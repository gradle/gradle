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

import org.gradle.buildinit.plugins.fixtures.ScriptDslFixture
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.TestExecutionResult
import spock.lang.Unroll

class GroovyLibraryInitIntegrationTest extends AbstractInitIntegrationSpec {

    public static final String SAMPLE_LIBRARY_CLASS = "src/main/groovy/Library.groovy"
    public static final String SAMPLE_LIBRARY_TEST_CLASS = "src/test/groovy/LibraryTest.groovy"

    @Unroll
    def "creates sample source if no source present with #scriptDsl build scripts"() {
        when:
        run('init', '--type', 'groovy-library', '--dsl', scriptDsl.id)

        then:
        file(SAMPLE_LIBRARY_CLASS).exists()
        file(SAMPLE_LIBRARY_TEST_CLASS).exists()
        commonFilesGenerated(scriptDsl)

        when:
        run("build")

        then:
        TestExecutionResult testResult = new DefaultTestExecutionResult(testDirectory)
        testResult.assertTestClassesExecuted("LibraryTest")
        testResult.testClass("LibraryTest").assertTestPassed("someLibraryMethod returns true")

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    @Unroll
    def "supports the Spock test framework with #scriptDsl build scripts"() {
        when:
        run('init', '--type', 'groovy-library', '--test-framework', 'spock', '--dsl', scriptDsl.id)

        then:
        file(SAMPLE_LIBRARY_CLASS).exists()
        file(SAMPLE_LIBRARY_TEST_CLASS).exists()
        dslFixtureFor(scriptDsl).assertGradleFilesGenerated()

        when:
        run("build")

        then:
        TestExecutionResult testResult = new DefaultTestExecutionResult(testDirectory)
        testResult.assertTestClassesExecuted("LibraryTest")
        testResult.testClass("LibraryTest").assertTestPassed("someLibraryMethod returns true")

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    @Unroll
    def "setupProjectLayout is skipped when groovy sources detected with #scriptDsl build scripts"() {
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
        run('init', '--type', 'groovy-library', '--dsl', scriptDsl.id)

        then:
        !file(SAMPLE_LIBRARY_CLASS).exists()
        !file(SAMPLE_LIBRARY_TEST_CLASS).exists()
        dslFixtureFor(scriptDsl).assertGradleFilesGenerated()

        when:
        run("build")

        then:
        executed(":test")

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }
}
