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

import org.gradle.buildinit.plugins.fixtures.ScriptDslFixture
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.testing.internal.util.RetryRule
import org.junit.Rule
import spock.lang.Unroll

import static org.gradle.integtests.fixtures.RetryRuleUtil.getRootCauseMessage
import static org.gradle.testing.internal.util.RetryRule.retryIf

class JavaApplicationInitIntegrationTest extends AbstractInitIntegrationSpec {

    public static final String SAMPLE_APP_CLASS = "src/main/java/App.java"
    public static final String SAMPLE_APP_TEST_CLASS = "src/test/java/AppTest.java"
    public static final String SAMPLE_APP_SPOCK_TEST_CLASS = "src/test/groovy/AppTest.groovy"

    @Rule
    RetryRule retryRule = retryIf { Throwable t ->
        //retry on Jcenter connectivity issue
        getRootCauseMessage(t).startsWith("Could not GET")
    }

    @Unroll
    def "creates sample source if no source present with #scriptDsl build scripts"() {
        when:
        succeeds('init', '--type', 'java-application', '--dsl', scriptDsl.id)

        then:
        file(SAMPLE_APP_CLASS).exists()
        file(SAMPLE_APP_TEST_CLASS).exists()
        dslFixtureFor(scriptDsl).assertGradleFilesGenerated()

        when:
        succeeds("build")

        then:
        assertTestPassed("testAppHasAGreeting")

        when:
        succeeds("run")

        then:
        outputContains("Hello world")

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    @Unroll
    def "creates sample source using spock instead of junit with #scriptDsl build scripts"() {
        when:
        succeeds('init', '--type', 'java-application', '--test-framework', 'spock', '--dsl', scriptDsl.id)

        then:
        file(SAMPLE_APP_CLASS).exists()
        file(SAMPLE_APP_SPOCK_TEST_CLASS).exists()
        dslFixtureFor(scriptDsl).assertGradleFilesGenerated()

        when:
        succeeds("build")

        then:
        assertTestPassed("application has a greeting")

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    @Unroll
    def "creates sample source using testng instead of junit with #scriptDsl build scripts"() {
        when:
        succeeds('init', '--type', 'java-application', '--test-framework', 'testng', '--dsl', scriptDsl.id)

        then:
        file(SAMPLE_APP_CLASS).exists()
        file(SAMPLE_APP_TEST_CLASS).exists()
        dslFixtureFor(scriptDsl).assertGradleFilesGenerated()

        when:
        succeeds("build")

        then:
        assertTestPassed("appHasAGreeting")

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    @Unroll
    def "setupProjectLayout is skipped when java sources detected with #scriptDsl build scripts"() {
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
        succeeds('init', '--type', 'java-application', '--dsl', scriptDsl.id)

        then:
        !file(SAMPLE_APP_CLASS).exists()
        !file(SAMPLE_APP_TEST_CLASS).exists()
        dslFixtureFor(scriptDsl).assertGradleFilesGenerated()

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    void assertTestPassed(String name) {
        new DefaultTestExecutionResult(testDirectory).testClass("AppTest").assertTestPassed(name)
    }
}
