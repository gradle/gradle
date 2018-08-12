/*
 * Copyright 2018 the original author or authors.
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
import spock.lang.Unroll

class KotlinApplicationInitIntegrationTest extends AbstractInitIntegrationSpec {

    public static final String SAMPLE_APP_CLASS = "src/main/kotlin/App.kt"
    public static final String SAMPLE_APP_TEST_CLASS = "src/test/kotlin/AppTest.kt"

    @Unroll
    def "creates sample source if no source present with #scriptDsl build scripts"() {
        when:
        run('init', '--type', 'kotlin-application', '--dsl', scriptDsl.id)

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
    def "creates sample source with package and #scriptDsl build scripts"() {
        when:
        run('init', '--type', 'kotlin-application', '--package', 'my.app', '--dsl', scriptDsl.id)

        then:
        file("src/main/kotlin/my/app/App.kt").exists()
        file("src/test/kotlin/my/app/AppTest.kt").exists()
        commonFilesGenerated(scriptDsl)

        when:
        run("build")

        then:
        assertTestPassed("my.app.AppTest", "testAppHasAGreeting")

        when:
        run("run")

        then:
        outputContains("Hello world")

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    @Unroll
    def "setupProjectLayout is skipped when kotlin sources detected with #scriptDsl build scripts"() {
        setup:
        file("src/main/kotlin/org/acme/SampleMain.kt") << """
        package org.acme

        class SampleMain {
        }
"""
        file("src/test/kotlin/org/acme/SampleMainTest.kt") << """
                package org.acme

                class SampleMainTest {
                }
        """
        when:
        run('init', '--type', 'kotlin-application', '--dsl', scriptDsl.id)

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
