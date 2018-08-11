/*
 * Copyright 2017 the original author or authors.
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
import spock.lang.Unroll

class GroovyApplicationInitIntegrationTest extends AbstractInitIntegrationSpec {

    public static final String SAMPLE_APP_CLASS = "src/main/groovy/App.groovy"
    public static final String SAMPLE_APP_SPOCK_TEST_CLASS = "src/test/groovy/AppTest.groovy"

    @Unroll
    def "creates sample source if no source present with #scriptDsl build scripts"() {
        when:
        run('init', '--type', 'groovy-application', '--dsl', scriptDsl.id)

        then:
        file(SAMPLE_APP_CLASS).exists()
        file(SAMPLE_APP_SPOCK_TEST_CLASS).exists()
        commonFilesGenerated(scriptDsl)

        when:
        run("build")

        then:
        assertTestPassed("application has a greeting")

        when:
        run("run")

        then:
        outputContains("Hello world")

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    @Unroll
    def "creates sample source using spock instead of junit with #scriptDsl build scripts"() {
        when:
        run('init', '--type', 'groovy-application', '--test-framework', 'spock', '--dsl', scriptDsl.id)

        then:
        file(SAMPLE_APP_CLASS).exists()
        file(SAMPLE_APP_SPOCK_TEST_CLASS).exists()
        dslFixtureFor(scriptDsl).assertGradleFilesGenerated()

        when:
        run("build")

        then:
        assertTestPassed("application has a greeting")

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    @Unroll
    def "specifying TestNG is not supported with #scriptDsl build scripts"() {
        when:
        fails('init', '--type', 'groovy-application', '--test-framework', 'testng', '--dsl', scriptDsl.id)

        then:
        failure.assertHasCause("""The requested test framework 'testng' is not supported for 'groovy-application' setup type. Supported frameworks:
  - 'junit'
  - 'spock'""")

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    @Unroll
    def "setupProjectLayout is skipped when groovy sources detected with #scriptDsl build scripts"() {
        setup:
        file("src/main/groovy/org/acme/SampleMain.groovy") << """
        package org.acme;

        public class SampleMain{
        }
"""
        file("src/test/groovy/org/acme/SampleMainTest.groovy") << """
                package org.acme;

                class SampleMain{
                }
        """
        when:
        run('init', '--type', 'groovy-application', '--dsl', scriptDsl.id)

        then:
        !file(SAMPLE_APP_CLASS).exists()
        !file(SAMPLE_APP_SPOCK_TEST_CLASS).exists()
        dslFixtureFor(scriptDsl).assertGradleFilesGenerated()

        when:
        run("build")

        then:
        executed(":test")

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    def assertTestPassed(String name) {
        new DefaultTestExecutionResult(testDirectory).testClass("AppTest").assertTestPassed(name)
    }
}
