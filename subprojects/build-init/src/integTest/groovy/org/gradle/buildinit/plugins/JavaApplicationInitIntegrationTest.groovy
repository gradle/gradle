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
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework
import spock.lang.Unroll

class JavaApplicationInitIntegrationTest extends AbstractInitIntegrationSpec {

    public static final String SAMPLE_APP_CLASS = "src/main/java/App.java"
    public static final String SAMPLE_APP_TEST_CLASS = "src/test/java/AppTest.java"
    public static final String SAMPLE_APP_SPOCK_TEST_CLASS = "src/test/groovy/AppTest.groovy"

    @Unroll
    def "creates sample source if no source present with #scriptDsl build scripts"() {
        when:
        run('init', '--type', 'java-application', '--dsl', scriptDsl.id)

        then:
        file(SAMPLE_APP_CLASS).exists()
        file(SAMPLE_APP_TEST_CLASS).exists()
        commonFilesGenerated(scriptDsl)

        when:
        run("build")

        then:
        assertTestPassed("AppTest", "testAppHasAGreeting")

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
        run('init', '--type', 'java-application', '--test-framework', 'spock', '--dsl', scriptDsl.id)

        then:
        file(SAMPLE_APP_CLASS).exists()
        file(SAMPLE_APP_SPOCK_TEST_CLASS).exists()
        commonFilesGenerated(scriptDsl)

        when:
        run("build")

        then:
        assertTestPassed("AppTest", "application has a greeting")

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    @Unroll
    def "creates sample source using testng instead of junit with #scriptDsl build scripts"() {
        when:
        run('init', '--type', 'java-application', '--test-framework', 'testng', '--dsl', scriptDsl.id)

        then:
        file(SAMPLE_APP_CLASS).exists()
        file(SAMPLE_APP_TEST_CLASS).exists()
        commonFilesGenerated(scriptDsl)

        when:
        run("build")

        then:
        assertTestPassed("AppTest", "appHasAGreeting")

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    @Unroll
    def "creates sample source with package and #testFramework and #scriptDsl build scripts"() {
        when:
        run('init', '--type', 'java-application', '--test-framework', 'testng', '--package', 'my.app', '--dsl', scriptDsl.id)

        then:
        file("src/main/java/my/app/App.java").exists()
        file("src/test/java/my/app/AppTest.java").exists()

        and:
        commonFilesGenerated(scriptDsl)

        when:
        run("build")

        then:
        assertTestPassed("my.app.AppTest", "appHasAGreeting")

        when:
        run("run")

        then:
        outputContains("Hello world")

        where:
        [scriptDsl, testFramework] << [ScriptDslFixture.SCRIPT_DSLS, [BuildInitTestFramework.JUNIT, BuildInitTestFramework.TESTNG]].combinations()
    }

    @Unroll
    def "creates sample source with package and spock and #scriptDsl build scripts"() {
        when:
        run('init', '--type', 'java-application', '--test-framework', 'spock', '--package', 'my.app', '--dsl', scriptDsl.id)

        then:
        file("src/main/java/my/app/App.java").exists()
        file("src/test/groovy/my/app/AppTest.groovy").exists()

        and:
        commonFilesGenerated(scriptDsl)

        when:
        run("build")

        then:
        assertTestPassed("my.app.AppTest", "application has a greeting")

        when:
        run("run")

        then:
        outputContains("Hello world")

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    @Unroll
    def "source generation is skipped when java sources detected with #scriptDsl build scripts"() {
        setup:
        file("src/main/java/org/acme/SampleMain.java") << """
        package org.acme;

        public class SampleMain{
        }
"""
        file("src/test/java/org/acme/SampleMainTest.java") << """
                package org.acme;

                public class SampleMainTest {
                }
        """
        when:
        run('init', '--type', 'java-application', '--dsl', scriptDsl.id)

        then:
        !file(SAMPLE_APP_CLASS).exists()
        !file(SAMPLE_APP_TEST_CLASS).exists()
        dslFixtureFor(scriptDsl).assertGradleFilesGenerated()

        when:
        run("build")

        then:
        executed(":test")

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }
}
