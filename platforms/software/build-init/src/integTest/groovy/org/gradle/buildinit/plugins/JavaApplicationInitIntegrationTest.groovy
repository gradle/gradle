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
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import spock.lang.Issue

import static org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl.KOTLIN

class JavaApplicationInitIntegrationTest extends AbstractJvmLibraryInitIntegrationSpec {

    public static final String SAMPLE_APP_CLASS = "some/thing/App.java"
    public static final String SAMPLE_APP_TEST_CLASS = "some/thing/AppTest.java"
    public static final String SAMPLE_APP_SPOCK_TEST_CLASS = "some/thing/AppTest.groovy"

    @Override
    String subprojectName() { 'app' }

    def "defaults to Kotlin build scripts"() {
        when:
        run ('init', '--type', 'java-application')

        then:
        dslFixtureFor(KOTLIN).assertGradleFilesGenerated()
    }

    def "creates sample source if no source present with #scriptDsl build scripts"() {
        def dslFixture = dslFixtureFor(scriptDsl)

        when:
        run('init', '--type', 'java-application', '--dsl', scriptDsl.id)

        then:
        subprojectDir.file("src/main/java").assertHasDescendants(SAMPLE_APP_CLASS)
        subprojectDir.file("src/test/java").assertHasDescendants(SAMPLE_APP_TEST_CLASS)

        and:
        commonJvmFilesGenerated(scriptDsl)
        dslFixture.assertDoesNotUseTestSuites()

        when:
        run("build")

        then:
        assertTestPassed("some.thing.AppTest", "appHasAGreeting")

        when:
        run("run")

        then:
        outputContains("Hello World!")

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    def "creates build using test suites with #scriptDsl build scripts when using --incubating"() {
        def dslFixture = dslFixtureFor(scriptDsl)

        when:
        run ('init', '--type', 'java-application', '--dsl', scriptDsl.id, '--incubating')

        then:
        subprojectDir.file("src/main/java").assertHasDescendants(SAMPLE_APP_CLASS)
        subprojectDir.file("src/test/java").assertHasDescendants(SAMPLE_APP_TEST_CLASS)

        and:
        commonJvmFilesGenerated(scriptDsl)
        dslFixture.assertHasTestSuite("test")

        when:
        run('test')
        then:
        assertTestPassed("some.thing.AppTest", "appHasAGreeting")

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    def "creates with gradle.properties when using #scriptDsl build scripts with --incubating"() {
        when:
        run ('init', '--type', 'java-application', '--dsl', scriptDsl.id, '--incubating')

        then:
        gradlePropertiesGenerated()

        when:
        succeeds('test')

        then:
        assertTestPassed("some.thing.AppTest", "appHasAGreeting")

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    def "creates sample source using spock instead of junit with #scriptDsl build scripts"() {
        when:
        run('init', '--type', 'java-application', '--test-framework', 'spock', '--dsl', scriptDsl.id)

        then:
        subprojectDir.file("src/main/java").assertHasDescendants(SAMPLE_APP_CLASS)
        !subprojectDir.file("src/test/java").exists()
        subprojectDir.file("src/test/groovy").assertHasDescendants(SAMPLE_APP_SPOCK_TEST_CLASS)

        and:
        commonJvmFilesGenerated(scriptDsl)

        when:
        run("build")

        then:
        assertTestPassed("some.thing.AppTest", "application has a greeting")

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    def "creates sample source using testng instead of junit with #scriptDsl build scripts"() {
        when:
        run('init', '--type', 'java-application', '--test-framework', 'testng', '--dsl', scriptDsl.id)

        then:
        subprojectDir.file("src/main/java").assertHasDescendants(SAMPLE_APP_CLASS)
        subprojectDir.file("src/test/java").assertHasDescendants(SAMPLE_APP_TEST_CLASS)

        and:
        commonJvmFilesGenerated(scriptDsl)

        when:
        run("build")

        then:
        assertTestPassed("some.thing.AppTest", "appHasAGreeting")

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    def "creates sample source using junit-jupiter instead of junit with #scriptDsl build scripts"() {
        when:
        run('init', '--type', 'java-application', '--test-framework', 'junit-jupiter', '--dsl', scriptDsl.id)

        then:
        subprojectDir.file("src/main/java").assertHasDescendants(SAMPLE_APP_CLASS)
        subprojectDir.file("src/test/java").assertHasDescendants(SAMPLE_APP_TEST_CLASS)

        and:
        commonJvmFilesGenerated(scriptDsl)

        when:
        run("build")

        then:
        assertTestPassed("some.thing.AppTest", "appHasAGreeting")

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    def "creates sample source with package and #testFramework and #scriptDsl build scripts"() {
        when:
        run('init', '--type', 'java-application', '--test-framework', testFramework.id, '--package', 'my.app', '--dsl', scriptDsl.id)

        then:
        subprojectDir.file("src/main/java").assertHasDescendants("my/app/App.java")
        subprojectDir.file("src/test/java").assertHasDescendants("my/app/AppTest.java")

        and:
        commonJvmFilesGenerated(scriptDsl)

        when:
        run("build")

        then:
        assertTestPassed("my.app.AppTest", "appHasAGreeting")

        when:
        run("run")

        then:
        outputContains("Hello World!")

        where:
        [scriptDsl, testFramework] << [ScriptDslFixture.SCRIPT_DSLS, [BuildInitTestFramework.JUNIT, BuildInitTestFramework.TESTNG]].combinations()
    }

    def "creates sample source with package and spock and #scriptDsl build scripts"() {
        when:
        run('init', '--type', 'java-application', '--test-framework', 'spock', '--package', 'my.app', '--dsl', scriptDsl.id)

        then:
        subprojectDir.file("src/main/java").assertHasDescendants("my/app/App.java")
        subprojectDir.file("src/test/groovy").assertHasDescendants("my/app/AppTest.groovy")

        and:
        commonJvmFilesGenerated(scriptDsl)

        when:
        run("build")

        then:
        assertTestPassed("my.app.AppTest", "application has a greeting")

        when:
        run("run")

        then:
        outputContains("Hello World!")

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    def "source generation is skipped when java sources detected with #scriptDsl build scripts"() {
        setup:
        subprojectDir.file("src/main/java/org/acme/SampleMain.java") << """
        package org.acme;

        public class SampleMain{
        }
"""
        subprojectDir.file("src/test/java/org/acme/SampleMainTest.java") << """
                package org.acme;

                public class SampleMainTest {

                    @org.junit.jupiter.api.Test
                    public void sampleTest() { }
                }
        """
        when:
        run('init', '--type', 'java-application', '--dsl', scriptDsl.id)

        then:
        subprojectDir.file("src/main/java").assertHasDescendants("org/acme/SampleMain.java")
        subprojectDir.file("src/test/java").assertHasDescendants("org/acme/SampleMainTest.java")
        dslFixtureFor(scriptDsl).assertGradleFilesGenerated()

        when:
        run("build")

        then:
        executed(":app:test")

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }


    @Issue("https://github.com/gradle/gradle/issues/17383")
    @Requires(IntegTestPreconditions.NotEmbeddedExecutor)
    def "command line works with different locale"() {
        setup:
        executer.withCommandLineGradleOpts('-Duser.language=tr', '-Duser.country=TR')

        expect:
        succeeds('init', '--type', 'java-application', '--dsl', 'groovy')
    }
}
