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
import spock.lang.Unroll

class ScalaLibraryInitIntegrationTest extends AbstractInitIntegrationSpec {

    public static final String SAMPLE_LIBRARY_CLASS = "src/main/scala/Library.scala"
    public static final String SAMPLE_LIBRARY_TEST_CLASS = "src/test/scala/LibrarySuite.scala"

    @Unroll
    def "creates sample source if no source present with #scriptDsl build scripts"() {
        when:
        succeeds('init', '--type', 'scala-library', '--dsl', scriptDsl.id)

        then:
        file(SAMPLE_LIBRARY_CLASS).exists()
        file(SAMPLE_LIBRARY_TEST_CLASS).exists()
        dslFixtureFor(scriptDsl).assertGradleFilesGenerated()

        when:
        run("build")

        then:
        new DefaultTestExecutionResult(testDirectory).testClass("LibrarySuite").assertTestPassed("someLibraryMethod is always true")

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    @Unroll
    def "setupProjectLayout is skipped when scala sources detected with #scriptDsl build scripts"() {
        setup:
        file("src/main/scala/org/acme/SampleMain.scala") << """
            package org.acme;

            class SampleMain{
            }
    """
        file("src/test/scala/org/acme/SampleMainTest.scala") << """
                    package org.acme;

                    class SampleMainTest{
                    }
            """

        when:
        succeeds('init', '--type', 'scala-library', '--dsl', scriptDsl.id)

        then:
        !file(SAMPLE_LIBRARY_CLASS).exists()
        !file(SAMPLE_LIBRARY_TEST_CLASS).exists()
        dslFixtureFor(scriptDsl).assertGradleFilesGenerated()

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }
}
